# 2026-10-30 完整预览亮屏生命周期

v0.4.20 的完整预览没有请求保持亮屏，用户只看画面而不触摸时，系统可能按闲置超时息屏并停止相机。参考 [Open Camera 的前台取景亮屏行为](https://opencamera.org.uk/help.html#quickstart)和 [Android Activity 窗口亮屏建议](https://developer.android.com/develop/background-work/background-tasks/awake/screen-on)，本轮仅在正在使用的完整预览期间设置 FLAG_KEEP_SCREEN_ON。

## 范围

- 仅 Activity 已恢复、有窗口焦点、未进入紧凑 UI 或 PiP，且状态为 STARTING 或采集/显示仍新鲜的 LIVE 时设置。停止页、失焦、暂停、销毁不保持；进入 PiP 动画前已释放，进入失败恢复完整 UI 后按实际状态重新计算。
- 通过现有主线程状态与生命周期同步，只在该标记改变时更新对应窗口位，保留其它窗口属性；相机帧回调不反复向系统提交窗口更新。
- 不使用 WakeLock、不改系统息屏时间或亮度、不新增权限；手动锁屏、离屏和故障仍按现有规则停止相机。可选检测单独暂停时，若仍在完整预览且画面实时，继续取景亮屏。
- 长时间看完整预览的屏幕耗电会增加；本轮没有实体设备功耗数据，不把功能改善表述为节电优化。小窗本身不要求常亮，实际息屏还受前台应用与设备设置影响。

## 验证方法

两轮普通 PiP 原生流程各追加一次真实闲置验证，不改变独立线程/调度实验：仅在受限模拟器中暂存并修改 screen_off_timeout=1000、stay_on_while_plugged_in=0，读取 PowerManagerService 实际配置，按系统最小息屏时间观察完整预览超过该时间仍实时/亮屏。期间不注入触摸；随后实际进入小窗，等待系统自动息屏，检查相机资源、反馈与 watchdog 释放。恢复原系统设置后唤醒，先验证相机仍停止，再主动重新开启并验证亮屏恢复。设置在 finally 中恢复。

系统最小息屏时间从 dumpsys power 的 mMinimumScreenOffTimeoutConfig 读取，配置字段格式已核对 [Android 15 官方源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/services/core/java/com/android/server/power/PowerManagerService.java)。配置生效等待与自然息屏观察有各自界限，不改变相机启动 5000ms、采集/显示 1200ms、分析 1500ms 或输入/结果 1000ms 的截止。既有手动锁屏检查保留；停止/故障资源检查同时验证窗口没有遗留亮屏标记。

本地 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -PuniversalApk` 通过，146 项 JVM 测试无失败，Lint 0 错误 / 22 警告；产品表面与 v0.4.21 发布元数据检查通过。配置生效等待和电源快照补充后，最终原生测试包已重新编译成功；云端实际结果见下节。探针保存配置、完整预览、电源息屏及恢复设置前的 power 快照，避免失败后丢失现场。

模拟器的真实自动息屏不能替代 Find X8 Ultra、分屏、系统弹窗、AR 或电池实测。

首轮候选 `5514036` 的 [41040368806](https://github.com/cyanrbt/walkbuddy/actions/runs/41040368806) API 35 首次全部通过，原始附件已下载；两轮实际自动息屏均通过，系统最小时间 10000ms，完整预览无输入观察 11500ms 仍 Awake，小窗按配置自动进入 Asleep。API 36 在旧锁屏恢复输入步骤失败，未运行到新增用例，[原始范围与唤醒同步调整](wake-input-idle-2026-10-30.md)已记录。最终候选结果见后节，不能用该轮单套成功替代两套验证。

仅增加事件空闲等待的 `4f490ae` 中 API 36 仍在相同输入步骤失败。已据新时序移除完整预览唤醒后重复的 singleTask 启动 Intent；小窗唤醒则改为实际点击首页返回，保持主动展开与手动重开的用户操作。具体证据和没有证明的部分见上方同步记录，生产亮屏逻辑未改变。

自然唤醒候选 `58e3900` 的 API 36 仍在旧恢复触摸处失败，该轮未满足发布条件。新增 InputDispatcher 路由证据；脚本在任何探针失败后继续采集后续流程、最终仍返回失败，12 项工具检查通过。

## 最终候选结果

源码 `23e3ccac3922803bbfeb5823f3a47504a7199f79` 的 [41043678006](https://github.com/cyanrbt/walkbuddy/actions/runs/41043678006) attempt 1 成功：API 35 job `124022371350`、API 36 job `124022371055` 均首次通过。两套附件已下载并校验大小/摘要；各九项探针完整、两轮自动息屏与完整模型/PiP 回归通过，各五条唤醒就绪记录。摘要均没有 ANR 观察。

两套的两轮记录均为系统最小息屏时间 10000ms、完整预览无输入观察至少 11500ms；电源快照分别 Awake / Asleep，且两阶段的实际配置相同（screen_off_timeout=1000、stay_on_while_plugged_in=0）。自然息屏后资源与轮询释放、唤醒仍停止、首页主动展开与手动重开亮屏恢复均通过。已实际查看 API 36 完整实时预览原图，既有开关和固定操作保持。

| API | artifact / 字节 | ZIP SHA-256 | 原始摘要及电源字段 |
| --- | --- | --- | --- |
| 35 | 12971103932 / 7,956,128 | `981513cf0b54312244440ee410c498f6c617aafda1f2643962dca71875bdf35c` | [api35.json](screen-idle-2026-10-30-api35.json) |
| 36 | 12970579227 / 7,881,167 | `7b217f333a12892a2c6ed8565f63570eeb35099ebf3ea5e38d4b9f9d674a0e26` | [api36.json](screen-idle-2026-10-30-api36.json) |

此前三轮输入失败与未充分解释之处全部保留。本次成功没有触发失败瞬时采集分支，不能证明旧 UID 异常根因已经消失。最后按 UiAutomation 的直接 Runtime.exec 参数规则移除日志过滤器中的 shell 引号，最终测试包重新编译成功；只影响失败采证，不改变运行应用或通过路径。正式标签仍需完整发布检查。
