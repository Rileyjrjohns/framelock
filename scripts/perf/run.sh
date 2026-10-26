#!/usr/bin/env bash
# One performance run on a device (M7, docs/architecture.md "Performance method", docs/perf/README.md).
#
#   run.sh [options] <label> [seconds=600]
#
#   --max-start-temp <C>      start only when the battery is at or below this temperature (default 33)
#   --cooldown-timeout <s>    give up waiting for the cool-down after this long (default 1800)
#   --backend <auto|gpu|cpu>  force the inference backend (debug builds; default auto = production behaviour)
#   --no-trace                skip the Perfetto trace (the numbers still come from the app's own windows)
#   --public                  record a trace fit for publishing: the app's own sections and CPU frequencies only,
#                             no scheduling and no process list (those name every process on the device)
#   --note <text>             what changed in this run, for results.md (default: the label)
#   --force                   overwrite docs/perf/runs/<label>
#
# Plays the fixed input video (scripts/perf/fetch-input.sh) through the debug app on the one attached device
# (ANDROID_SERIAL honoured) and records, into docs/perf/runs/<label>/:
#   meta.txt              device, build, input video SHA-256, start and end thermal status and temperatures
#   cpu-samples.txt       every 10 s: device uptime, utime, stime of the app process (/proc/<pid>/stat)
#   temps.txt             every minute: battery temperature
#   meminfo.txt           dumpsys meminfo at the end (TOTAL PSS)
#   thermal-*.txt, battery-*.txt   dumps at the start and the end
#   logcat.txt            the app's Telemetry lines (PipelineWindow, one per 10 s), engine backend, errors
#   <label>.perfetto-trace  the Perfetto system trace (gitignored: large)
# then runs summarize.py, which writes summary.md and adds a row to docs/perf/results.md.
#
# Order: cool-down gate (thermal status 0 and a cool battery), Perfetto trace started, app force-stopped and launched
# with `-e source file --ez autostart true --ez hud true`, sampling for <seconds>, final readings, app stopped, trace
# and logs pulled. A run starts the app cold on purpose. See docs/perf/README.md for the conditions to hold constant.
#
# Perfetto config: textproto passed on stdin (`perfetto -c - --txt`), as documented in
#   https://perfetto.dev/docs/concepts/config  (Android caveats: stdin config, --background, pulling the trace)
#   https://perfetto.dev/docs/reference/trace-config-proto  (fields below)
#   https://perfetto.dev/docs/data-sources/cpu-freq  and  https://perfetto.dev/docs/data-sources/battery-counters
# If the device's perfetto refuses the full config (an older build without a newer field), the script retries with a
# basic one and says so.
set -euo pipefail

PACKAGE="dev.framelock"
# The application id (dev.framelock) differs from the Kotlin namespace of the activity (dev.framelock.app).
ACTIVITY="$PACKAGE/dev.framelock.app.MainActivity"
INPUT_REL="files/perf/input.mp4"
SAMPLE_INTERVAL=10
TEMP_EVERY=6           # samples between two battery temperature readings (one per minute)
TRACE_MARGIN=30        # seconds of trace beyond the run: launch time, final readings
COOLDOWN_POLL=15
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

max_start_temp=33
cooldown_timeout=1800
backend=""
trace=1
public=0
force=0
note=""
positional=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --max-start-temp) max_start_temp="${2:?--max-start-temp needs a value}"; shift 2 ;;
    --cooldown-timeout) cooldown_timeout="${2:?--cooldown-timeout needs a value}"; shift 2 ;;
    --backend) backend="${2:?--backend needs auto, gpu or cpu}"; shift 2 ;;
    --no-trace) trace=0; shift ;;
    --public) public=1; shift ;;
    --note) note="${2:?--note needs a text}"; shift 2 ;;
    --force) force=1; shift ;;
    -h|--help) sed -n '2,33p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    -*) echo "Unknown option: $1 (see --help)" >&2; exit 2 ;;
    *) positional+=("$1"); shift ;;
  esac
done
[[ ${#positional[@]} -ge 1 && ${#positional[@]} -le 2 ]] || { echo "Usage: run.sh [options] <label> [seconds=600]" >&2; exit 2; }
label="${positional[0]}"
seconds="${positional[1]:-600}"
[[ "$label" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "The label may only contain letters, digits, '.', '_' and '-'" >&2; exit 2; }
[[ "$seconds" =~ ^[0-9]+$ && "$seconds" -ge 60 ]] || { echo "seconds must be an integer >= 60 (the summary skips the first 30 s)" >&2; exit 2; }
case "$backend" in ""|auto|gpu|cpu) ;; *) echo "--backend must be auto, gpu or cpu" >&2; exit 2 ;; esac

run_dir="$ROOT/docs/perf/runs/$label"
remote_trace="/data/misc/perfetto-traces/$label.perfetto-trace"
log() { echo "[$(date +%H:%M:%S)] $*"; }
die() { echo "error: $*" >&2; exit 1; }
# adb shell with the carriage returns of the pty-less shell protocol removed.
dev() { adb shell "$@" | tr -d '\r'; }
# Integer-safe "a <= b" for decimals.
le() { awk -v a="$1" -v b="$2" 'BEGIN { exit !(a <= b) }'; }

# ---- Pre-checks -------------------------------------------------------------------------------------------------
command -v adb >/dev/null || die "adb not found on PATH"
command -v python3 >/dev/null || die "python3 not found on PATH"
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$ANDROID_SERIAL" || die "device $ANDROID_SERIAL is not attached"
else
  count="$(adb devices | awk 'NR>1 && $2=="device"' | wc -l | tr -d ' ')"
  [[ "$count" -eq 1 ]] || die "$count devices attached: attach exactly one or set ANDROID_SERIAL"
fi
adb shell pm path "$PACKAGE" >/dev/null 2>&1 || die "$PACKAGE is not installed: ./gradlew installDebug"
adb shell run-as "$PACKAGE" true >/dev/null 2>&1 || die "$PACKAGE is not a debug build (run-as refused): install the debug build"
if ! adb shell run-as "$PACKAGE" test -f "$INPUT_REL" >/dev/null 2>&1; then
  die "no input video in the app: run scripts/perf/fetch-input.sh (it pushes it to $INPUT_REL)"
fi
if [[ $trace -eq 1 ]]; then
  adb shell which perfetto >/dev/null 2>&1 || die "perfetto not found on the device (Android 9 or older?); use --no-trace"
fi
if [[ -e "$run_dir" && $force -eq 0 ]]; then die "$run_dir exists (use --force to overwrite)"; fi
rm -rf "$run_dir"
mkdir -p "$run_dir"
input_sha="$(dev run-as "$PACKAGE" sha256sum "$INPUT_REL" | cut -d' ' -f1)"

# ---- Readings ---------------------------------------------------------------------------------------------------
thermal_status() { dev dumpsys thermalservice | grep -m1 'Thermal Status:' | sed 's/.*Thermal Status: *//' | tr -d ' '; }
# Battery temperature in tenths of a degree Celsius, the unit of `dumpsys battery`.
battery_tenths() { dev dumpsys battery | awk '/^ *temperature:/ {print $2; exit}'; }
tenths_to_c() { awk -v t="$1" 'BEGIN { printf "%.1f", t / 10 }'; }
uptime_s() { dev cat /proc/uptime | cut -d' ' -f1; }

# ---- Cool-down gate ---------------------------------------------------------------------------------------------
log "Cool-down gate: thermal status 0 and battery <= ${max_start_temp} C (timeout ${cooldown_timeout}s)"
gate_start=$SECONDS
while true; do
  status="$(thermal_status || true)"
  tenths="$(battery_tenths || true)"
  if [[ "$status" == "0" && -n "$tenths" ]] && le "$(tenths_to_c "$tenths")" "$max_start_temp"; then break; fi
  (( SECONDS - gate_start < cooldown_timeout )) || die "the device did not cool down in time (thermal status '${status}', battery $(tenths_to_c "${tenths:-0}") C)"
  log "waiting: thermal status '${status}', battery $(tenths_to_c "${tenths:-0}") C"
  sleep "$COOLDOWN_POLL"
done
start_status="$status"
start_tenths="$tenths"
log "Start: thermal status $start_status, battery $(tenths_to_c "$start_tenths") C"
dev dumpsys thermalservice > "$run_dir/thermal-start.txt" || true
dev dumpsys battery > "$run_dir/battery-start.txt" || true

# ---- Cleanup ----------------------------------------------------------------------------------------------------
trace_pid=""
logcat_pid=""
cleanup() {
  [[ -n "$logcat_pid" ]] && kill "$logcat_pid" 2>/dev/null || true
  [[ -n "$trace_pid" ]] && adb shell kill "$trace_pid" >/dev/null 2>&1 || true
  adb shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
}
trap cleanup EXIT
trap 'echo "interrupted" >&2; exit 130' INT TERM

# ---- Perfetto ---------------------------------------------------------------------------------------------------
perfetto_config() {
  local mode="$1" duration_ms=$(( (seconds + TRACE_MARGIN) * 1000 )) thermal=""
  [[ "$mode" == full ]] && thermal="thermal_period_ms: 1000"
  cat <<PBTX
duration_ms: $duration_ms
write_into_file: true
file_write_period_ms: 5000
# With write_into_file the buffers are drained to the file periodically: RING_BUFFER and a flush period are what
# trace_processor expects (DISCARD can lose data, and no flush keeps everything in memory).
flush_period_ms: 10000
max_file_size_bytes: 1073741824
buffers { size_kb: 65536 fill_policy: RING_BUFFER }
buffers { size_kb: 16384 fill_policy: RING_BUFFER }

data_sources {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      ftrace_events: "sched/sched_switch"
      ftrace_events: "sched/sched_wakeup"
      ftrace_events: "sched/sched_waking"
      ftrace_events: "sched/sched_process_exit"
      ftrace_events: "sched/sched_process_free"
      ftrace_events: "task/task_newtask"
      ftrace_events: "task/task_rename"
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      ftrace_events: "power/gpu_frequency"
      ftrace_events: "power/suspend_resume"
      ftrace_events: "thermal/thermal_temperature"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "am"
      atrace_apps: "$PACKAGE"
      buffer_size_kb: 16384
    }
  }
}
data_sources {
  config {
    name: "linux.process_stats"
    target_buffer: 1
    process_stats_config {
      scan_all_processes_on_start: true
      record_thread_names: true
      proc_stats_poll_ms: 1000
    }
  }
}
data_sources {
  config {
    name: "linux.sys_stats"
    target_buffer: 1
    sys_stats_config {
      cpufreq_period_ms: 1000
      meminfo_period_ms: 1000
      stat_period_ms: 1000
      stat_counters: STAT_CPU_TIMES
      $thermal
    }
  }
}
data_sources {
  config {
    name: "android.power"
    target_buffer: 1
    android_power_config {
      battery_poll_ms: 1000
      battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
      battery_counters: BATTERY_COUNTER_CURRENT
      battery_counters: BATTERY_COUNTER_VOLTAGE
      collect_power_rails: true
    }
  }
}
data_sources { config { name: "linux.system_info" target_buffer: 1 } }
PBTX
}

# The public variant leaves out what names other processes: scheduling and wakeups, task names, process stats and
# the system-wide atrace categories. What stays: the app's own atrace sections (filtered by package), CPU frequency and
# idle, battery counters, and the device description that every Perfetto trace carries.
public_config() {
  local duration_ms=$(( (seconds + TRACE_MARGIN) * 1000 ))
  cat <<PBTX
duration_ms: $duration_ms
write_into_file: true
file_write_period_ms: 5000
flush_period_ms: 10000
buffers { size_kb: 32768 fill_policy: RING_BUFFER }
buffers { size_kb: 4096 fill_policy: RING_BUFFER }
data_sources {
  config {
    name: "linux.ftrace"
    target_buffer: 0
    ftrace_config {
      ftrace_events: "power/cpu_frequency"
      ftrace_events: "power/cpu_idle"
      ftrace_events: "power/gpu_frequency"
      atrace_apps: "$PACKAGE"
      buffer_size_kb: 8192
    }
  }
}
data_sources {
  config {
    name: "android.power"
    target_buffer: 1
    android_power_config {
      battery_poll_ms: 1000
      battery_counters: BATTERY_COUNTER_CAPACITY_PERCENT
      battery_counters: BATTERY_COUNTER_CURRENT
    }
  }
}
data_sources { config { name: "linux.system_info" target_buffer: 1 } }
PBTX
}

start_trace() {
  local mode out modes="full basic"
  [[ $public -eq 1 ]] && modes="public"
  for mode in $modes; do
    if [[ "$mode" == public ]]; then public_config; else perfetto_config "$mode"; fi > "$run_dir/perfetto-config-$mode.pbtx"
    if out="$(adb shell perfetto -c - --txt --background -o "$remote_trace" < "$run_dir/perfetto-config-$mode.pbtx" 2>"$run_dir/perfetto-$mode.err")" \
      && [[ "$(echo "$out" | tr -d '\r\n ')" =~ ^[0-9]+$ ]]; then
      trace_pid="$(echo "$out" | tr -d '\r\n ')"
      log "Perfetto started (pid $trace_pid, $mode config, $((seconds + TRACE_MARGIN)) s)"
      return 0
    fi
    log "Perfetto refused the $mode config: $(head -c 300 "$run_dir/perfetto-$mode.err")"
  done
  die "Perfetto could not start (see $run_dir/perfetto-*.err); use --no-trace to run without"
}

[[ $trace -eq 1 ]] && start_trace

# ---- Launch -----------------------------------------------------------------------------------------------------
adb shell am force-stop "$PACKAGE"
adb shell pm grant "$PACKAGE" android.permission.CAMERA >/dev/null 2>&1 || true   # the live screen waits for it
adb shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb logcat -c
adb logcat -v threadtime -s Telemetry:I LiteRtEngine:I VideoFileSource:I AppGraph:W LiveViewModel:E TelemetryBuffer:W \
  AndroidRuntime:E DEBUG:F > "$run_dir/logcat.txt" 2>&1 &
logcat_pid=$!

launch_args=(-e source file --ez autostart true --ez hud true)
[[ -n "$backend" ]] && launch_args+=(-e backend "$backend")
log "Launching: am start ${launch_args[*]}"
# No -W: with the screen off it can block for minutes while the app already runs, which lengthens the run unseen.
adb shell am start -n "$ACTIVITY" "${launch_args[@]}" > "$run_dir/am-start.txt"
grep -q "^Error" "$run_dir/am-start.txt" && die "launch failed: $(grep "^Error" "$run_dir/am-start.txt" | tail -1)"
launch_uptime="$(uptime_s)"

pid=""
for _ in $(seq 1 30); do
  pid="$(dev pidof "$PACKAGE" | awk '{print $1}')" || true
  [[ -n "$pid" ]] && break
  sleep 1
done
[[ -n "$pid" ]] || die "the app did not start (see $run_dir/am-start.txt)"
log "App pid $pid; running for ${seconds}s"

# ---- Sampling ---------------------------------------------------------------------------------------------------
: > "$run_dir/cpu-samples.txt"
: > "$run_dir/temps.txt"
sample_cpu() {
  # After the "(comm)" field, utime and stime are the 12th and 13th fields (14th and 15th of /proc/<pid>/stat).
  dev "cat /proc/uptime; cat /proc/$pid/stat" 2>/dev/null \
    | awk 'NR==1 { up = $1 } NR==2 { sub(/^.*\) /, ""); print up, $12, $13 }'
}
ended_early=0
n=0
run_start=$SECONDS
while (( SECONDS - run_start < seconds )); do
  line="$(sample_cpu || true)"
  if [[ -z "$line" ]]; then
    log "the app process $pid is gone: the run ends early"
    ended_early=1
    break
  fi
  echo "$line" >> "$run_dir/cpu-samples.txt"
  if (( n % TEMP_EVERY == 0 )); then echo "$(uptime_s) $(battery_tenths || true)" >> "$run_dir/temps.txt"; fi
  n=$((n + 1))
  (( n % 6 == 0 )) && log "$((SECONDS - run_start))/${seconds}s"
  next=$(( run_start + n * SAMPLE_INTERVAL ))
  (( next > SECONDS )) && sleep $(( next - SECONDS ))
done

# ---- Final readings ---------------------------------------------------------------------------------------------
end_status="$(thermal_status || true)"
end_tenths="$(battery_tenths || true)"
log "End: thermal status ${end_status:-?}, battery $(tenths_to_c "${end_tenths:-0}") C"
dev dumpsys meminfo "$PACKAGE" > "$run_dir/meminfo.txt" || true
dev dumpsys thermalservice > "$run_dir/thermal-end.txt" || true
dev dumpsys battery > "$run_dir/battery-end.txt" || true
echo "$(uptime_s) ${end_tenths:-}" >> "$run_dir/temps.txt"
end_pid="$(dev pidof "$PACKAGE" | awk '{print $1}' || true)"
[[ "$end_pid" == "$pid" ]] || ended_early=1

adb shell am force-stop "$PACKAGE"
sleep 2
kill "$logcat_pid" 2>/dev/null || true
logcat_pid=""

# ---- Trace ------------------------------------------------------------------------------------------------------
if [[ -n "$trace_pid" ]]; then
  log "Waiting for the trace to end"
  for _ in $(seq 1 $(( TRACE_MARGIN + 120 ))); do
    adb shell kill -0 "$trace_pid" >/dev/null 2>&1 || break
    sleep 1
  done
  adb shell kill -0 "$trace_pid" >/dev/null 2>&1 && adb shell kill "$trace_pid" >/dev/null 2>&1 && sleep 3
  trace_pid=""
  adb pull "$remote_trace" "$run_dir/$label.perfetto-trace" >/dev/null
  adb shell rm -f "$remote_trace"
  log "Trace: $run_dir/$label.perfetto-trace ($(du -h "$run_dir/$label.perfetto-trace" | cut -f1))"
fi

# ---- Metadata ---------------------------------------------------------------------------------------------------
{
  echo "label=$label"
  echo "date_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "seconds=$seconds"
  echo "note=${note:-$label}"
  echo "backend_requested=${backend:-auto}"
  echo "serial=${ANDROID_SERIAL:-$(adb get-serialno | tr -d '\r')}"
  # Committed summaries name the device by this label only (no product names in the docs); the raw meta stays local.
  echo "device_label=${FRAMELOCK_DEVICE_LABEL:-test phone}"
  echo "device_model=$(dev getprop ro.product.model)"
  echo "device_soc=$(dev getprop ro.soc.model)"
  echo "android_release=$(dev getprop ro.build.version.release)"
  echo "android_sdk=$(dev getprop ro.build.version.sdk)"
  echo "build_fingerprint=$(dev getprop ro.build.fingerprint)"
  echo "app_version=$(dev dumpsys package "$PACKAGE" | grep -m1 versionName= | sed 's/.*versionName=//' | tr -d ' ')"
  echo "git_commit=$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)$(git -C "$ROOT" diff --quiet HEAD 2>/dev/null || echo -dirty)"
  echo "input_sha256=$input_sha"
  echo "clk_tck=$(dev getconf CLK_TCK 2>/dev/null || echo 100)"
  echo "max_start_temp_c=$max_start_temp"
  echo "start_thermal_status=$start_status"
  echo "start_battery_tenths_c=$start_tenths"
  echo "end_thermal_status=${end_status:-}"
  echo "end_battery_tenths_c=${end_tenths:-}"
  echo "launch_uptime_s=$launch_uptime"
  echo "pid=$pid"
  echo "ended_early=$ended_early"
} > "$run_dir/meta.txt"

find "$run_dir" -name 'perfetto-*.err' -empty -delete

# ---- Summary ----------------------------------------------------------------------------------------------------
python3 -I "$ROOT/scripts/perf/summarize.py" "$run_dir"
log "Done: $run_dir/summary.md and a row in docs/perf/results.md"
[[ $ended_early -eq 0 ]] || { echo "warning: the app ended before the run did (crash?): see $run_dir/logcat.txt" >&2; exit 1; }
