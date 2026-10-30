# 2026-10-30 Actions 失败原因核对

用户询问 GitHub 打包持续失败。本次直接查询最近 20 次 Actions、最近五个 Releases，并读取相关失败 job 日志。最近 v0.4.15、v0.4.16、v0.4.17、v0.4.18 的 `Release APK` 均首次成功，各自的 Release 确有 APK；近期红叉主要发生在 main 分支的 `Android UI checks` 模拟器运行步骤，之前的 `Build emulator test APKs` 已成功。构建成功与设备验收仍是不同范围。

## 已查明的失败

| Actions | 日志中的具体失败 | 后续处理与当前边界 |
| --- | --- | --- |
| [40999704272](https://github.com/cyanrbt/walkbuddy/actions/runs/40999704272) | `Native checkbox lost label/state`；在未附着窗口快照读取节点元数据 | 将严格语义断言移到实际相机窗口；不是跳过断言，后续通过 |
| [41000875826](https://github.com/cyanrbt/walkbuddy/actions/runs/41000875826) API 36 | 控件底边 381px，视口底边 377px | 修正 320×480 / 200% 字体时说明区域留白，后续严格可见检查通过 |
| [41006155456](https://github.com/cyanrbt/walkbuddy/actions/runs/41006155456)、[41006806141](https://github.com/cyanrbt/walkbuddy/actions/runs/41006806141) | `RGB parity failed for [137, 59] rotation=90 ... byte=91473` | 两次尝试合并绘制均不满足逐字节对照；撤回该实现，最终保留旧绘制顺序并复用 worker 旋转缓冲，未放宽断言 |
| [41014998568](https://github.com/cyanrbt/walkbuddy/actions/runs/41014998568) attempt 1 API 35 | `Timed out: touch target ready ... interactive=true keyguard=false; 已停止；请主动开启` | 原提交重跑失败 job 后 attempt 2 成功；应用与 v0.4.18 无源码差异，触摸/窗口时序根因仍未定位，不能将重跑通过称为修复 |

已直接读取的 job：`123880977735`、`123884714492`、`123901411736`、`123929214335`。完整历史和对应应用修正见[验证结果](../验证结果.md)。不重跑已被后续实现替代的历史失败来消除红叉，也不删除失败记录。

## 当前可下载发布

[v0.4.18 发布流程 41011135290](https://github.com/cyanrbt/walkbuddy/actions/runs/41011135290) 首次成功，含 Android 15/16 检查及固定签名 APK 作业。Release 实际存在 [Walk-Buddy-v0.4.18-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.18/Walk-Buddy-v0.4.18-arm64-debug.apk)，9,947,545 字节。发布后已实际下载核验包名、版本、模型和签名，记录见[发布附件核验](../验证结果.md#v0418-发布附件核验)；不代表 Find X8 Ultra、真实道路或 TalkBack 已验收。

## 本轮 CI 触发范围

此前每次 main 推送都会跑完整模拟器，包括没有 Android 改动的候选模型研究。现将仅包含 `docs/**`、`experiments/**`、`README.md` 或模型核验工作流的推送从 Android push 检查中排除；有任何其它文件变更仍触发，修改 UI 工作流本身也会触发。模型研究继续由专用 `Candidate model audit` 验证。

标签发布仍通过 `workflow_call` 调用原 Android 15/16 检查，`apk` 作业仍 `needs: ui`，必须通过后才打包和发布；未改发布依赖、检查项目、超时、签名或生产应用。此项调整减少无关运行，不解决已有窗口/触摸时序问题。本轮推送后的实际状态另行核实。
