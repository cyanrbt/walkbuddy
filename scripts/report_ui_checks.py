#!/usr/bin/env python3
"""Describe build/probe results and failure-time focus evidence without changing test outcomes."""
import argparse
import json
from pathlib import Path
import re

PACKAGE = "com.cyanrbt.walkbuddy"
PROBES = ["pip_recovery", "runtime", "preview", "overlay", "layout", "home", "recovery", "pip", "pip-repeat"]


def read(path: Path) -> str:
    return path.read_text(errors="replace") if path.is_file() else ""


def summarize(directory: Path, build_result: str, emulator_result: str, mode="ui") -> dict:
    probes = []
    if mode == "ui":
        for name in PROBES:
            text = read(directory / (name + ".txt"))
            marker = name.split("-")[0].upper() + "_PROBE_"
            status = "failed" if marker + "FAIL" in text else "passed" if marker + "PASS" in text else "missing"
            failures = [line.strip()[:2000] for line in text.splitlines() if marker + "FAIL" in line]
            probes.append({"name": name, "status": status, "failures": failures})
    anrs = []
    lines = read(directory / "lifecycle.txt").splitlines()
    for i, line in enumerate(lines):
        match = re.search(r"ActivityManager:\s+ANR in ([\w.]+)(?:\s|\(|$)", line)
        if match:
            reason = next((entry.split("Reason:", 1)[1].strip() for entry in lines[i + 1:i + 8]
                           if "ActivityManager: Reason:" in entry), None)
            anrs.append({"package": match.group(1), "event": line.strip(), "reason": reason})
    focus = []
    blocking = []
    for path in sorted((directory / "cache/ui-probe").glob("*-failure-window.txt")):
        for line in read(path).splitlines():
            if "mCurrentFocus=" not in line:
                continue
            focused = line.strip()
            focus.append({"file": str(path.relative_to(directory)), "focused_window": focused})
            match = re.search(r"Application Not Responding: ([\w.]+)", focused)
            if match and match.group(1) != PACKAGE:
                blocking.append({"package": match.group(1), "file": str(path.relative_to(directory)),
                                 "focused_window": focused})
    if build_result != "success":
        status = "build_not_passed"
    elif emulator_result != "success":
        status = "checks_not_passed"
    elif mode == "ui" and any(p["status"] != "passed" for p in probes):
        status = "evidence_incomplete"
    else:
        status = "passed"
    observations = []
    if blocking:
        observations.append("external_anr_dialog_has_focus")
    if any(event["package"] == PACKAGE for event in anrs):
        observations.append("application_anr_observed")
    if any(event["package"] != PACKAGE for event in anrs):
        observations.append("external_anr_observed")
    return {"status": status, "scope": "Emulator diagnostics; no device acceptance or root-cause claim from ANR coexistence",
            "build_result": build_result, "emulator_result": emulator_result, "mode": mode,
            "probes": probes, "observations": observations, "anrs": anrs,
            "failure_time_focus": focus, "focused_external_anr_dialogs": blocking}


def markdown(report: dict) -> str:
    labels = {"success": "通过", "failure": "失败", "skipped": "未运行", "cancelled": "取消"}
    result = ["### Android 检查结果", "", "| 阶段 | 结果 |", "| --- | --- |",
              f'| 模拟器测试 APK 编译 | {labels.get(report["build_result"], report["build_result"])} |',
              f'| 模拟器运行 | {labels.get(report["emulator_result"], report["emulator_result"])} |', "",
              "这里编译的是模拟器测试包；正式可下载 APK 的发布状态请查看 Release APK 工作流。", ""]
    if report["probes"]:
        result.extend(["| 探针 | 记录 |", "| --- | --- |"])
        for probe in report["probes"]:
            result.append(f'| {probe["name"]} | ' + {"passed": "通过", "failed": "失败", "missing": "未取得通过记录"}[probe["status"]] + " |")
        result.append("")
    if report["status"] == "evidence_incomplete":
        result.extend(["结果与附件不完整，不能将其记录为检查已通过。", ""])
    if report["focused_external_anr_dialogs"]:
        owners = ", ".join(sorted({item["package"] for item in report["focused_external_anr_dialogs"]}))
        result.extend([f"失败时的窗口快照显示，外部 ANR 弹窗占有输入焦点：`{owners}`。", ""])
    if report["anrs"]:
        owners = ", ".join(sorted({item["package"] for item in report["anrs"]}))
        result.extend([f"系统日志记录了 ANR：`{owners}`。仅有同时出现的 ANR 记录，不能证明它导致了探针失败。", ""])
    result.extend(["原始失败、截图和失败时的窗口/输入快照保留在 artifact 中；摘要不会把失败改为成功，也不会自动关闭系统弹窗。", ""])
    return "\n".join(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--build-result", required=True, choices=["success", "failure", "skipped", "cancelled"])
    parser.add_argument("--emulator-result", required=True, choices=["success", "failure", "skipped", "cancelled"])
    parser.add_argument("--mode", choices=["ui", "thread", "scheduling"], default="ui")
    args = parser.parse_args()
    report = summarize(args.directory, args.build_result, args.emulator_result, args.mode)
    args.directory.mkdir(parents=True, exist_ok=True)
    (args.directory / "check-summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    (args.directory / "check-summary.md").write_text(markdown(report))
    print(markdown(report))


if __name__ == "__main__":
    main()
