#!/usr/bin/env python3
"""Self-test of summarize.py: its doctests and one run directory built from small fixtures.

    python3 scripts/perf/test_summarize.py
"""

import doctest
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import summarize  # noqa: E402

META = """label=demo
date_utc=2026-10-24T10:00:00Z
seconds=120
backend_requested=gpu
note=baseline
device_model=Test Phone
android_release=16
android_sdk=36
app_version=0.1.0
git_commit=abc1234
input_sha256=deadbeef
clk_tck=100
start_thermal_status=0
start_battery_tenths_c=305
end_thermal_status=1
end_battery_tenths_c=368
launch_uptime_s=1000.0
ended_early=0
"""


def window(second, inference, e2e, drops):
    return (
        "10-24 10:00:%02d.000 1 1 I Telemetry: PipelineWindow(inferenceMsP50=%s, inferenceMsP95=%s, e2eMsP50=%s, "
        "e2eMsP95=%s, inputFps=30.0, inferenceFps=%s, droppedFrames=%d)\n"
        % (second, inference, inference + 4, e2e, e2e + 10, 25.0, drops)
    )


LOGCAT = (
    "10-24 10:00:00.000 1 1 I LiteRtEngine: Backend GPU (requested gpu)\n"
    + window(10, 99.0, 99.0, 50)  # warm-up: ignored
    + window(20, 99.0, 99.0, 50)
    + window(40, 10.0, 30.0, 1)
    + window(50, 12.0, 32.0, 2)
    + window(59, 11.0, 31.0, 3)
)

CPU = "1000.0 0 0\n1010.0 100 50\n1040.0 200 100\n1050.0 400 200\n1060.0 500 250\n"

MEMINFO = "App Summary\n  Java Heap:    2048\n  Native Heap:  4096\n\n TOTAL PSS:   204800            TOTAL RSS: 1\n"

THERMAL = "Temperature{mValue=%s, mType=3, mName=skin, mStatus=0}\n"


class RunDirectory(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.TemporaryDirectory()
        self.run = os.path.join(self.root.name, "runs", "demo")
        os.makedirs(self.run)
        for name, text in {
            "meta.txt": META,
            "logcat.txt": LOGCAT,
            "cpu-samples.txt": CPU,
            "meminfo.txt": MEMINFO,
            "thermal-start.txt": THERMAL % "30.1",
            "thermal-end.txt": THERMAL % "35.4",
        }.items():
            with open(os.path.join(self.run, name), "w") as handle:
                handle.write(text)
        self.results = os.path.join(self.root.name, "results.md")

    def tearDown(self):
        self.root.cleanup()

    def test_figures_are_medians_of_steady_windows(self):
        s = summarize.build_summary(self.run, 30)

        self.assertEqual("GPU", s["backend"])
        self.assertEqual(3, s["windows_used"])
        self.assertEqual(5, s["windows_total"])
        self.assertEqual((11.0, 15.0), s["inference"])
        self.assertEqual((31.0, 41.0), s["e2e"])
        self.assertEqual(6, s["drops"])
        self.assertEqual(200.0, s["pss_mb"])
        self.assertEqual((30.5, 36.8), (s["battery_start"], s["battery_end"]))
        self.assertEqual({"skin": 35.4}, s["sensors_end"])

    def test_cpu_skips_the_warm_up(self):
        s = summarize.build_summary(self.run, 30)

        # Intervals ending at uptime 1040, 1050 and 1060 (30 s or more after the launch): 5 %, 30 % and 15 %.
        self.assertAlmostEqual(50.0 / 3, s["cpu_mean"])

    def test_main_writes_the_summary_and_one_row_per_label(self):
        self.assertEqual(0, summarize.main([self.run, "--results", self.results]))
        self.assertEqual(0, summarize.main([self.run, "--results", self.results]))

        with open(os.path.join(self.run, "summary.md")) as handle:
            summary = handle.read()
        with open(self.results) as handle:
            results = handle.read()
        self.assertIn("| Inference p50 / p95 (ms) | 11.0 / 15.0 |", summary)
        self.assertEqual(1, results.count("| [demo](runs/demo/summary.md) |"))
        self.assertIn("30.5 → 36.8 (skin 30.1 → 35.4)", results)

    def test_a_missing_log_gives_notes_not_a_crash(self):
        os.remove(os.path.join(self.run, "logcat.txt"))

        s = summarize.build_summary(self.run, 30)

        self.assertIsNone(s["inference"][0])
        self.assertTrue(any("No PipelineWindow" in n for n in s["notes"]))


def load_tests(loader, tests, _pattern):
    tests.addTests(doctest.DocTestSuite(summarize))
    return tests


if __name__ == "__main__":
    unittest.main()
