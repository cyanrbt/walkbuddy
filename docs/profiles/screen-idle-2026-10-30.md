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

本地 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -PuniversalApk` 通过，146 项 JVM 测试无失败，Lint 0 错误 / 22 警告；产品表面与 v0.4.21 发布元数据检查通过。配置生效等待和电源快照补充后，最终原生测试包已重新编译成功；云端实际结果完成后补记。探针保存配置、完整预览、电源息屏及恢复设置前的 power 快照，避免失败后丢失现场。

模拟器的真实自动息屏不能替代 Find X8 Ultra、分屏、系统弹窗、AR 或电池实测。
