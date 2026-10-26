#!/usr/bin/env python3
"""Draws a one-second timeline of the app's trace sections as an SVG, from a Perfetto trace.

Usage: timeline.py <trace> <out.svg> [--title TEXT] [--skip SECONDS] [--window MS]

The window starts at the first INFERENCE slice found after <skip> seconds (default 30: the warm-up is excluded), and
lasts <window> ms (default 1000). Lanes: CAPTURE (decode thread), PREPROCESS / INFERENCE / POSTPROCESS (inference
thread), RENDER (main thread). Needs trace_processor_shell on PATH. Python 3 standard library only.
"""

import argparse
import csv
import io
import shutil
import subprocess
import sys
import tempfile
from html import escape

SECTIONS = ("CAPTURE", "PREPROCESS", "INFERENCE", "POSTPROCESS", "RENDER")
LANES = (("Capture (decode thread)", ("CAPTURE",)),
         ("Inference thread", ("PREPROCESS", "INFERENCE", "POSTPROCESS")),
         ("Render (main thread)", ("RENDER",)))
COLOURS = {"CAPTURE": "#60A5FA", "PREPROCESS": "#FBBF24", "INFERENCE": "#F87171", "POSTPROCESS": "#34D399",
           "RENDER": "#A78BFA"}
WIDTH, LEFT, RIGHT, LANE_H, TOP = 1000, 190, 20, 46, 64


def query(trace, sql):
    shell = shutil.which("trace_processor_shell")
    if shell is None:
        sys.exit("trace_processor_shell is not on PATH")
    with tempfile.NamedTemporaryFile("w", suffix=".sql", delete=False) as f:
        f.write(sql)
        path = f.name
    out = subprocess.run([shell, "-q", path, trace], capture_output=True, text=True, check=True).stdout
    return list(csv.DictReader(io.StringIO(out[out.index('"'):] if '"' in out else "")))


def slices(trace, skip_s, window_ms):
    names = ",".join("'%s'" % s for s in SECTIONS)
    start = query(trace, """
        SELECT MIN(ts) AS t FROM slice WHERE name = 'INFERENCE'
          AND ts >= (SELECT MIN(ts) FROM slice WHERE name = 'INFERENCE') + %d""" % int(skip_s * 1e9))
    t0 = int(start[0]["t"])
    t1 = t0 + int(window_ms * 1e6)
    rows = query(trace, """
        SELECT name, ts, dur FROM slice
        WHERE name IN (%s) AND ts + dur > %d AND ts < %d ORDER BY ts""" % (names, t0, t1))
    return t0, t1, [(r["name"], int(r["ts"]), int(r["dur"])) for r in rows]


def svg(t0, t1, items, title):
    span = t1 - t0
    plot = WIDTH - LEFT - RIGHT
    height = TOP + LANE_H * len(LANES) + 70
    x = lambda t: LEFT + (min(max(t, t0), t1) - t0) / span * plot  # noqa: E731
    out = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" font-family="sans-serif" font-size="12">'
           % (WIDTH, height), '<rect width="100%" height="100%" fill="#0A0C10"/>',
           '<text x="%d" y="26" fill="#F3F4F6" font-size="16" font-weight="600">%s</text>' % (LEFT, escape(title))]
    for ms in range(0, int(span / 1e6) + 1, 100):
        gx = LEFT + ms * 1e6 / span * plot
        out.append('<line x1="%.1f" y1="%d" x2="%.1f" y2="%d" stroke="#1F2937"/>'
                   % (gx, TOP - 6, gx, TOP + LANE_H * len(LANES)))
        out.append('<text x="%.1f" y="%d" fill="#9CA3AF" text-anchor="middle">%d ms</text>'
                   % (gx, TOP + LANE_H * len(LANES) + 18, ms))
    for i, (label, names) in enumerate(LANES):
        y = TOP + i * LANE_H
        out.append('<text x="%d" y="%d" fill="#E5E7EB" text-anchor="end">%s</text>'
                   % (LEFT - 10, y + LANE_H / 2 + 4, escape(label)))
        for name, ts, dur in items:
            if name not in names:
                continue
            x0, x1 = x(ts), x(ts + dur)
            out.append('<rect x="%.1f" y="%d" width="%.1f" height="%d" rx="2" fill="%s"><title>%s %.1f ms</title></rect>'
                       % (x0, y + 8, max(x1 - x0, 1.0), LANE_H - 16, COLOURS[name], name, dur / 1e6))
            if x1 - x0 > 34:
                out.append('<text x="%.1f" y="%d" fill="#0A0C10" text-anchor="middle" font-size="11">%.0f</text>'
                           % ((x0 + x1) / 2, y + LANE_H / 2 + 4, dur / 1e6))
    legend_y = TOP + LANE_H * len(LANES) + 44
    lx = LEFT
    for name in SECTIONS:
        out.append('<rect x="%d" y="%d" width="12" height="12" rx="2" fill="%s"/>' % (lx, legend_y - 10, COLOURS[name]))
        out.append('<text x="%d" y="%d" fill="#D1D5DB">%s</text>' % (lx + 18, legend_y, name))
        lx += 130
    out.append('<text x="%d" y="%d" fill="#9CA3AF" text-anchor="end">numbers: duration in ms</text>'
               % (WIDTH - RIGHT, legend_y))
    out.append("</svg>")
    return "\n".join(out) + "\n"


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("trace")
    parser.add_argument("out")
    parser.add_argument("--title", default="Trace sections, 1 s window")
    parser.add_argument("--skip", type=float, default=30.0)
    parser.add_argument("--window", type=float, default=1000.0)
    args = parser.parse_args(argv)
    t0, t1, items = slices(args.trace, args.skip, args.window)
    with open(args.out, "w") as f:
        f.write(svg(t0, t1, items, args.title))
    inference = [d / 1e6 for n, _, d in items if n == "INFERENCE"]
    print("%s: %d slices, %d inferences in the window" % (args.out, len(items), len(inference)))


if __name__ == "__main__":
    main(sys.argv[1:])
