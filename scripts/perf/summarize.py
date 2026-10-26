#!/usr/bin/env python3
"""Summarises one performance run (M7) and logs it in docs/perf/results.md.

    summarize.py <run_dir> [--results <results.md>] [--warmup 30]
    summarize.py --init-results <results.md>

<run_dir> is docs/perf/runs/<label>/, as written by scripts/perf/run.sh. The summary uses what the app logged
(PipelineWindow lines, one per 10 s window: the first --warmup seconds are ignored, then the median of the windows is
taken for each figure), the CPU samples, `dumpsys meminfo`, and the thermal and battery readings. When
trace_processor_shell is on PATH, per-section durations (CAPTURE, PREPROCESS, INFERENCE, POSTPROCESS, RENDER) are read
from the Perfetto trace too; nothing is downloaded.

Writes <run_dir>/summary.md and adds (or replaces) one row in results.md. Python 3 standard library only.
Self-test: python3 scripts/perf/test_summarize.py
"""

import argparse
import csv
import io
import math
import os
import re
import shutil
import statistics
import subprocess
import sys
import tempfile

SECTIONS = ["CAPTURE", "PREPROCESS", "INFERENCE", "POSTPROCESS", "RENDER"]
PACKAGE = "dev.framelock"
WINDOW_SECONDS = 10
THERMAL_TYPES = {0: "CPU", 1: "GPU", 2: "battery", 3: "skin"}

RESULTS_HEADER = (
    "| Run | Date | Device | Backend | Change | Inference p50 / p95 (ms) | E2E p50 / p95 (ms) | Input fps "
    "| Inference fps | Drops | CPU % | PSS (MB) | Temp. start → end (°C) | Trace |"
)
RESULTS_SEPARATOR = "|---|---|---|---|---|---|---|---|---|---|---|---|---|---|"

RESULTS_TEMPLATE = """# Performance results

Method: [README.md](README.md) and "Performance method" in [../architecture.md](../architecture.md). One change at a
time, each one re-measured on the same device, with the same input video and the same start conditions, then logged
here. The numbers come from `scripts/perf/run.sh`; each row links to its run directory
(`runs/<label>/summary.md`, the raw logs and the samples). Perfetto traces are large and not committed: they stay in
`runs/<label>/` on the machine that ran the measurement.

- **Input**: `classroom.mp4`, 1920x1080, H.264, 30 fps, 32.8 s, played in a loop through `capture-file`.
  SHA-256 `a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31`. Source and licence: see "Input video".
- **Run**: 10 minutes after a cool-down gate (thermal status 0, battery at or below 33 °C), debug build, HUD on.
- **Figures**: median over the 10 s windows after the first 30 s. Inference and end-to-end latency are the p50 / p95 of
  each window (`PipelineWindow`), then the median of the windows. Drops are summed over those windows.
  CPU % is the mean of the app process's CPU time over wall time, in percent of one core (a value above 100 uses
  several cores). PSS is `dumpsys meminfo` TOTAL PSS at the end. Temperatures are the battery temperature (and the
  skin sensor where the device reports it) at the start and after the run.
- **Backend**: what the engine reported at load (`LiteRtEngine` in Logcat), not what was asked.

| Run | Date | Device | Backend | Change | Inference p50 / p95 (ms) | E2E p50 / p95 (ms) | Input fps | Inference fps | Drops | CPU % | PSS (MB) | Temp. start → end (°C) | Trace |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Input video

`classroom.mp4` (CC BY 4.0, unmodified; source and attribution in `docs/soup.md`). Not stored in this repository:
`scripts/perf/fetch-input.sh` downloads it
and checks the SHA-256.
"""

PIPELINE_WINDOW = re.compile(r"PipelineWindow\((.*)\)")
THREADTIME = re.compile(r"^(\d\d)-(\d\d) (\d\d):(\d\d):(\d\d)\.(\d{3})\s")


# ---- Parsing ----------------------------------------------------------------------------------------------------


def parse_meta(text):
    """key=value lines -> dict.

    >>> parse_meta("a=1\\nb=x=y\\n\\nnot a pair\\n")
    {'a': '1', 'b': 'x=y'}
    """
    meta = {}
    for line in text.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            meta[key.strip()] = value.strip()
    return meta


def log_seconds(line):
    """Seconds since day 1, from a `logcat -v threadtime` line; None if it has no stamp.

    Months are ignored (a run is far shorter than one), so a day change is the only rollover that matters.

    >>> log_seconds("01-02 00:00:01.500 123 456 I Telemetry: x")
    86401.5
    >>> log_seconds("--------- beginning of main") is None
    True
    """
    match = THREADTIME.match(line)
    if not match:
        return None
    _month, day, hour, minute, second, millis = (int(g) for g in match.groups())
    return (day - 1) * 86400 + hour * 3600 + minute * 60 + second + millis / 1000.0


def parse_window_fields(body):
    """`a=1.5, b=null, c=3` -> {'a': 1.5, 'b': None, 'c': 3.0}.

    >>> parse_window_fields("inferenceMsP50=12.5, e2eMsP50=null, droppedFrames=3")
    {'inferenceMsP50': 12.5, 'e2eMsP50': None, 'droppedFrames': 3.0}
    """
    fields = {}
    for part in body.split(","):
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        value = value.strip()
        fields[key.strip()] = None if value == "null" else float(value)
    return fields


def parse_log(text):
    """Returns (windows, backend, load_time).

    windows: list of (time or None, fields) for each PipelineWindow line, in order. backend: "GPU"/"CPU" from the
    engine's load line, or None. load_time: the time of that line, the best marker of the pipeline's start.

    >>> t = ("10-24 10:00:00.000 1 1 I LiteRtEngine: Backend GPU (requested auto)\\n"
    ...      "10-24 10:00:12.000 1 1 I Telemetry: PipelineWindow(inferenceMsP50=5.0, droppedFrames=2)\\n")
    >>> windows, backend, load = parse_log(t)
    >>> backend, len(windows), windows[0][1]["droppedFrames"], windows[0][0] - load
    ('GPU', 1, 2.0, 12.0)
    """
    windows = []
    backend = None
    load_time = None
    day_offset = 0.0
    previous = None
    for line in text.splitlines():
        stamp = log_seconds(line)
        if stamp is not None:
            stamp += day_offset
            if previous is not None and stamp < previous - 3600:  # midnight: the day number restarts
                day_offset += 86400
                stamp += 86400
            previous = stamp
        match = re.search(r"LiteRtEngine: Backend (GPU|CPU)", line)
        if match and backend is None:
            backend = match.group(1)
            load_time = stamp
            continue
        match = PIPELINE_WINDOW.search(line)
        if match:
            windows.append((stamp, parse_window_fields(match.group(1))))
    return windows, backend, load_time


def steady_windows(windows, load_time, warmup, duration=None):
    """Windows ending at least `warmup` seconds after the pipeline started, and within `duration` seconds of it.

    The start is the engine's load line; without it, the first window's time minus one window. The duration bound
    keeps a run comparable when the app ran longer than asked (a launch that blocked while the app already ran).

    >>> ws = [(20.0, {}), (30.0, {}), (35.0, {}), (55.0, {})]
    >>> [t for t, _ in steady_windows(ws, 0.0, 30)]
    [30.0, 35.0, 55.0]
    >>> [t for t, _ in steady_windows(ws, None, 30)]
    [55.0]
    >>> [t for t, _ in steady_windows(ws, 0.0, 30, duration=40)]
    [30.0, 35.0]
    """
    if not windows:
        return []
    start = load_time if load_time is not None else (windows[0][0] or 0.0) - WINDOW_SECONDS
    if windows[0][0] is None:
        return windows[max(0, int(warmup // WINDOW_SECONDS)):]
    limit = float("inf") if duration is None else duration + 1.0
    return [(t, f) for t, f in windows if warmup <= t - start <= limit]


def median(values):
    """Median of the values that are not None; None when there are none.

    >>> median([3.0, None, 1.0, 2.0]), median([None]), median([1.0, 2.0])
    (2.0, None, 1.5)
    """
    present = [v for v in values if v is not None]
    return statistics.median(present) if present else None


def cpu_percentages(text, launch_uptime, tck, warmup):
    """Per-interval CPU % of one core from `uptime utime stime` samples, skipping the warm-up.

    >>> cpu_percentages("100.0 0 0\\n110.0 300 100\\n120.0 600 200\\n", 0.0, 100, 0)
    [40.0, 40.0]
    """
    samples = []
    for line in text.splitlines():
        parts = line.split()
        if len(parts) == 3:
            samples.append((float(parts[0]), int(parts[1]) + int(parts[2])))
    result = []
    for (up0, ticks0), (up1, ticks1) in zip(samples, samples[1:]):
        if up1 - launch_uptime < warmup or up1 <= up0:
            continue
        result.append((ticks1 - ticks0) / tck / (up1 - up0) * 100.0)
    return result


def parse_pss(text):
    """TOTAL PSS in kB from `dumpsys meminfo <package>`, old and new layouts; None if absent.

    >>> parse_pss("       TOTAL PSS:   155309            TOTAL RSS:   250000")
    155309
    >>> parse_pss("      TOTAL   155310   1  2  3")
    155310
    """
    match = re.search(r"TOTAL PSS:\s+(\d+)", text) or re.search(r"^\s*TOTAL\s+(\d+)", text, re.MULTILINE)
    return int(match.group(1)) if match else None


def parse_app_summary(text):
    """Heap breakdown in kB from the App Summary of `dumpsys meminfo`: {'Java Heap': kB, ...}.

    >>> parse_app_summary("App Summary\\n  Java Heap:    1000\\n  Native Heap:  2000\\n  Graphics:  300\\n")
    {'Java Heap': 1000, 'Native Heap': 2000, 'Graphics': 300}
    """
    wanted = ["Java Heap", "Native Heap", "Code", "Stack", "Graphics", "Private Other", "System"]
    result = {}
    for name in wanted:
        match = re.search(r"^\s*" + re.escape(name) + r":\s+(\d+)", text, re.MULTILINE)
        if match:
            result[name] = int(match.group(1))
    return result


def parse_thermal_temps(text):
    """Last reported temperature per sensor type from `dumpsys thermalservice`: {'skin': 33.1, ...}.

    >>> parse_thermal_temps("Temperature{mValue=30.5, mType=3, mName=skin, mStatus=0}\\n"
    ...                     "Temperature{mValue=40.0, mType=0, mName=cpu0, mStatus=0}\\n"
    ...                     "Temperature{mValue=31.5, mType=3, mName=skin, mStatus=0}")
    {'skin': 31.5, 'CPU': 40.0}
    """
    found = {}
    for value, kind in re.findall(r"mValue=(-?[\d.]+), mType=(-?\d+)", text):
        name = THERMAL_TYPES.get(int(kind))
        if name:
            found[name] = float(value)
    return found


def tenths(value):
    """Battery temperature in tenths of a degree -> °C, or None.

    >>> tenths("312"), tenths(""), tenths(None)
    (31.2, None, None)
    """
    return int(value) / 10.0 if value and value.lstrip("-").isdigit() else None


# ---- Trace sections ---------------------------------------------------------------------------------------------


def percentile(sorted_values, fraction):
    """Nearest-rank percentile of a sorted list.

    >>> percentile([1, 2, 3, 4], 0.5), percentile([1, 2, 3, 4], 0.95), percentile([7], 0.95)
    (2, 4, 7)
    """
    rank = max(1, math.ceil(round(fraction * len(sorted_values), 9)))
    return sorted_values[min(rank, len(sorted_values)) - 1]


def section_stats(rows, warmup_seconds):
    """rows: (name, ts_ns, dur_ns) -> {name: (count, p50_ms, p95_ms)}; slices in the warm-up are skipped.

    The warm-up is measured from the first slice of any section.

    >>> rows = [("INFERENCE", 0, 9_000_000)] + [("INFERENCE", 31_000_000_000 + i, (i + 1) * 1_000_000) for i in range(4)]
    >>> section_stats(rows, 30)
    {'INFERENCE': (4, 2.0, 4.0)}
    """
    if not rows:
        return {}
    origin = min(ts for _, ts, _ in rows)
    durations = {}
    for name, ts, dur in rows:
        if ts - origin >= warmup_seconds * 1e9:
            durations.setdefault(name, []).append(dur / 1e6)
    stats = {}
    for name in SECTIONS:
        values = sorted(durations.get(name, []))
        if values:
            stats[name] = (len(values), percentile(values, 0.5), percentile(values, 0.95))
    return stats


def trace_sections(trace_path, warmup):
    """Per-section durations from the trace with trace_processor_shell, or (None, reason)."""
    shell = shutil.which("trace_processor_shell")
    if shell is None:
        return None, "trace_processor_shell is not on PATH: section durations skipped"
    if not os.path.isfile(trace_path):
        return None, "no trace in the run directory: section durations skipped"
    names = ", ".join("'%s'" % s for s in SECTIONS)
    query = (
        "select slice.name as name, slice.ts as ts, slice.dur as dur from slice "
        "join thread_track tt on slice.track_id = tt.id join thread using (utid) join process using (upid) "
        "where process.name = '%s' and slice.name in (%s) and slice.dur > 0 order by slice.ts;" % (PACKAGE, names)
    )
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False) as handle:
        handle.write(query)
    try:
        done = subprocess.run([shell, "-q", handle.name, trace_path], capture_output=True, text=True)
    finally:
        os.unlink(handle.name)
    if done.returncode != 0:
        return None, "trace_processor_shell failed: %s" % done.stderr.strip()[:200]
    rows = []
    for row in csv.DictReader(io.StringIO(done.stdout)):
        try:
            rows.append((row["name"], int(row["ts"]), int(row["dur"])))
        except (KeyError, ValueError):
            continue
    if not rows:
        return None, "the trace has no section slices of %s (was the app traced? atrace_apps)" % PACKAGE
    return section_stats(rows, warmup), None


# ---- Rendering --------------------------------------------------------------------------------------------------


def fmt(value, digits=1):
    return "–" if value is None else ("%.*f" % (digits, value))


def pair(p50, p95):
    return "%s / %s" % (fmt(p50), fmt(p95))


def build_summary(run_dir, warmup):
    """Reads a run directory and returns a dict of every figure (None where missing) plus notes."""
    def read(name):
        path = os.path.join(run_dir, name)
        if not os.path.isfile(path):
            return ""
        with open(path, encoding="utf-8", errors="replace") as handle:
            return handle.read()

    meta = parse_meta(read("meta.txt"))
    windows, backend, load_time = parse_log(read("logcat.txt"))
    seconds = meta.get("seconds")
    steady = steady_windows(windows, load_time, warmup, int(seconds) if seconds and seconds.isdigit() else None)
    values = [fields for _, fields in steady]
    notes = []
    if not windows:
        notes.append("No PipelineWindow line in logcat.txt: no latency figures.")
    elif not steady:
        notes.append("The run is shorter than the %d s warm-up: no steady-state window." % warmup)
    if meta.get("ended_early") == "1":
        notes.append("The app ended before the run did: the figures are partial.")
    if backend is None:
        notes.append("No LiteRtEngine backend line in logcat.txt.")
    requested = meta.get("backend_requested", "auto")
    if backend and requested in ("gpu", "cpu") and backend.lower() != requested:
        notes.append("Backend %s was requested but %s was used." % (requested, backend))

    tck = int(meta.get("clk_tck") or 100)
    launch = float(meta.get("launch_uptime_s") or 0.0)
    cpu = cpu_percentages(read("cpu-samples.txt"), launch, tck, warmup)
    meminfo = read("meminfo.txt")
    pss_kb = parse_pss(meminfo)
    thermal_start = parse_thermal_temps(read("thermal-start.txt"))
    thermal_end = parse_thermal_temps(read("thermal-end.txt"))

    trace_path = os.path.join(run_dir, meta.get("label", os.path.basename(os.path.normpath(run_dir))) + ".perfetto-trace")
    sections, section_note = trace_sections(trace_path, warmup)
    if section_note:
        notes.append(section_note)

    return {
        "meta": meta,
        "backend": backend,
        "windows_total": len(windows),
        "windows_used": len(steady),
        "inference": (median([v.get("inferenceMsP50") for v in values]), median([v.get("inferenceMsP95") for v in values])),
        "e2e": (median([v.get("e2eMsP50") for v in values]), median([v.get("e2eMsP95") for v in values])),
        "input_fps": median([v.get("inputFps") for v in values]),
        "inference_fps": median([v.get("inferenceFps") for v in values]),
        "drops": int(sum(v.get("droppedFrames") or 0 for v in values)) if values else None,
        "cpu_mean": statistics.fmean(cpu) if cpu else None,
        "cpu_max": max(cpu) if cpu else None,
        "pss_mb": pss_kb / 1024.0 if pss_kb else None,
        "app_summary": parse_app_summary(meminfo),
        "battery_start": tenths(meta.get("start_battery_tenths_c")),
        "battery_end": tenths(meta.get("end_battery_tenths_c")),
        "thermal_status": (meta.get("start_thermal_status"), meta.get("end_thermal_status")),
        "sensors_start": thermal_start,
        "sensors_end": thermal_end,
        "sections": sections,
        "notes": notes,
    }


def temp_pair(start, end):
    return "%s → %s" % (fmt(start), fmt(end))


def results_row(label, s):
    meta = s["meta"]
    skin = (s["sensors_start"].get("skin"), s["sensors_end"].get("skin"))
    temps = temp_pair(s["battery_start"], s["battery_end"])
    if skin[1] is not None:
        temps += " (skin %s)" % temp_pair(*skin)
    cells = [
        "[%s](runs/%s/summary.md)" % (label, label),
        meta.get("date_utc", "–")[:10],
        "%s, Android %s" % (meta.get("device_label", "test phone"), meta.get("android_release", "–")),
        s["backend"] or "–",
        meta.get("note", label),
        pair(*s["inference"]),
        pair(*s["e2e"]),
        fmt(s["input_fps"]),
        fmt(s["inference_fps"]),
        "–" if s["drops"] is None else str(s["drops"]),
        fmt(s["cpu_mean"], 0),
        fmt(s["pss_mb"], 0),
        temps,
        "`runs/%s/%s.perfetto-trace` (local)" % (label, label),
    ]
    return "| " + " | ".join(c.replace("|", "/") for c in cells) + " |"


def summary_markdown(label, s, warmup):
    meta = s["meta"]
    lines = [
        "# Run %s" % label,
        "",
        "%s on %s (Android %s, SDK %s), app %s, commit %s." % (
            meta.get("date_utc", "–"), meta.get("device_label", "test phone"), meta.get("android_release", "–"),
            meta.get("android_sdk", "–"), meta.get("app_version", "–"), meta.get("git_commit", "–")),
        "",
        "- Change: %s" % meta.get("note", label),
        "- Backend: %s (requested %s)" % (s["backend"] or "unknown", meta.get("backend_requested", "auto")),
        "- Duration: %s s; steady state = %d of %d windows (first %d s ignored)" % (
            meta.get("seconds", "–"), s["windows_used"], s["windows_total"], warmup),
        "- Input video SHA-256: `%s`" % meta.get("input_sha256", "–"),
        "- Start: thermal status %s, battery %s °C. End: thermal status %s, battery %s °C." % (
            s["thermal_status"][0] or "–", fmt(s["battery_start"]), s["thermal_status"][1] or "–", fmt(s["battery_end"])),
        "",
        "| Figure | Value |",
        "|---|---|",
        "| Inference p50 / p95 (ms) | %s |" % pair(*s["inference"]),
        "| End-to-end p50 / p95 (ms) | %s |" % pair(*s["e2e"]),
        "| Input fps | %s |" % fmt(s["input_fps"]),
        "| Inference fps | %s |" % fmt(s["inference_fps"]),
        "| Dropped frames (steady windows) | %s |" % ("–" if s["drops"] is None else s["drops"]),
        "| CPU %% of one core, mean / max | %s / %s |" % (fmt(s["cpu_mean"], 0), fmt(s["cpu_max"], 0)),
        "| PSS (MB) | %s |" % fmt(s["pss_mb"], 0),
    ]
    for name, kb in s["app_summary"].items():
        lines.append("| &nbsp;&nbsp;%s (MB) | %s |" % (name, fmt(kb / 1024.0)))
    for sensor in sorted(set(s["sensors_start"]) | set(s["sensors_end"])):
        lines.append("| %s sensor start → end (°C) | %s |" % (
            sensor, temp_pair(s["sensors_start"].get(sensor), s["sensors_end"].get(sensor))))
    if s["sections"]:
        lines += ["", "## Trace sections (from the Perfetto trace, steady state)", "",
                  "| Section | Count | p50 (ms) | p95 (ms) |", "|---|---|---|---|"]
        for name in SECTIONS:
            if name in s["sections"]:
                count, p50, p95 = s["sections"][name]
                lines.append("| %s | %d | %s | %s |" % (name, count, fmt(p50, 2), fmt(p95, 2)))
    if s["notes"]:
        lines += ["", "## Notes", ""] + ["- %s" % n for n in s["notes"]]
    lines += ["", "Open the trace in https://ui.perfetto.dev (see docs/perf/README.md).", ""]
    return "\n".join(lines)


# ---- results.md -------------------------------------------------------------------------------------------------


def update_results(text, label, row):
    """Returns text with the row of `label` replaced, or appended to the results table.

    >>> t = RESULTS_HEADER + "\\n" + RESULTS_SEPARATOR + "\\n"
    >>> t = update_results(t, "a", "| [a](runs/a/summary.md) | x |")
    >>> t = update_results(t, "b", "| [b](runs/b/summary.md) | y |")
    >>> t = update_results(t, "a", "| [a](runs/a/summary.md) | z |")
    >>> [l for l in t.splitlines()[2:]]
    ['| [a](runs/a/summary.md) | z |', '| [b](runs/b/summary.md) | y |']
    """
    lines = text.splitlines()
    header = next((i for i, line in enumerate(lines) if line.startswith("| Run |")), None)
    if header is None:
        raise SystemExit("results.md has no table starting with '| Run |'")
    end = header + 2
    while end < len(lines) and lines[end].startswith("|"):
        end += 1
    rows = lines[header + 2:end]
    marker = "| [%s](" % label
    position = next((i for i, line in enumerate(rows) if line.startswith(marker)), None)
    if position is None:
        rows.append(row)
    else:
        rows[position] = row
    return "\n".join(lines[:header + 2] + rows + lines[end:]) + "\n"


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("run_dir", nargs="?")
    parser.add_argument("--results", help="results.md to update (default: two levels above the run directory)")
    parser.add_argument("--warmup", type=int, default=30, help="seconds to ignore at the start (default 30)")
    parser.add_argument("--init-results", metavar="FILE", help="write the results.md template to FILE and exit")
    args = parser.parse_args(argv)

    if args.init_results:
        with open(args.init_results, "w", encoding="utf-8") as handle:
            handle.write(RESULTS_TEMPLATE)
        return 0
    if not args.run_dir:
        parser.error("a run directory is required")
    run_dir = os.path.normpath(args.run_dir)
    label = os.path.basename(run_dir)
    results = args.results or os.path.join(os.path.dirname(os.path.dirname(run_dir)), "results.md")

    summary = build_summary(run_dir, args.warmup)
    with open(os.path.join(run_dir, "summary.md"), "w", encoding="utf-8") as handle:
        handle.write(summary_markdown(label, summary, args.warmup))
    if not os.path.isfile(results):
        with open(results, "w", encoding="utf-8") as handle:
            handle.write(RESULTS_TEMPLATE)
    with open(results, encoding="utf-8") as handle:
        text = handle.read()
    with open(results, "w", encoding="utf-8") as handle:
        handle.write(update_results(text, label, results_row(label, summary)))
    print("Summary: %s" % os.path.join(run_dir, "summary.md"))
    for note in summary["notes"]:
        print("note: %s" % note)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
