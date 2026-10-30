#!/usr/bin/env python3
"""Summarize every thread trial, including failed runs and their recorded timings."""
import json
from pathlib import Path
import re
import statistics
import sys


def stats(values):
    return {"count": len(values), "medianMs": statistics.median(values) if values else None,
            "maxMs": max(values) if values else None}


def summarize(directory):
    parity = (directory / "thread-parity.txt").read_text()
    result = {"parityPassed": "THREAD_PARITY_PASS" in parity, "trials": [],
              "scope": "Recorded initialization and inference wall time; worker CPU excludes helper threads; emulator only"}
    for index, threads in enumerate((2, 1, 1, 2), 1):
        prefix = directory / f"threads-{index}-count-{threads}"
        output = prefix.with_suffix(".txt").read_text()
        timing = Path(str(prefix) + "-timing.txt").read_text()
        header = f"THREAD_PROFILE priority=1 threads={threads} " in output
        initializations, calls = [], []
        wrong_threads = False
        for line in timing.splitlines():
            if "Model ready initMs=" not in line and "Inference prepareMs=" not in line:
                continue
            fields = dict(re.findall(r"(\w+)=([^\s]+)", line))
            if fields.get("threads") != str(threads):
                wrong_threads = True
            if "initMs" in fields:
                initializations.append(int(fields["initMs"]))
            else:
                calls.append({key: int(fields[key]) for key in ("prepareMs", "nativeMs", "workerCpuMs")}
                             | {"first": fields["first"] == "true"})
        passed = "PIP_PROBE_PASS" in output and header and not wrong_threads and bool(calls) and bool(initializations)
        failure = next((line for line in output.splitlines() if "PIP_PROBE_FAIL" in line), None)
        trial = {"index": index, "threads": threads, "passed": passed, "failure": failure,
                 "profileHeaderMatches": header, "timingThreadsMatch": not wrong_threads,
                 "initWall": stats(initializations), "nativeWall": stats([v["nativeMs"] for v in calls]),
                 "prepareWall": stats([v["prepareMs"] for v in calls]),
                 "firstNativeWall": stats([v["nativeMs"] for v in calls if v["first"]]),
                 "steadyNativeWall": stats([v["nativeMs"] for v in calls if not v["first"]]),
                 "workerCpu": stats([v["workerCpuMs"] for v in calls])}
        result["trials"].append(trial)
    result["passed"] = result["parityPassed"] and all(trial["passed"] for trial in result["trials"])
    return result


if __name__ == "__main__":
    directory = Path(sys.argv[1])
    result = summarize(directory)
    rendered = json.dumps(result, indent=2, ensure_ascii=False)
    (directory / "thread-summary.json").write_text(rendered + "\n")
    print(rendered)
    sys.exit(0 if result["passed"] else 1)
