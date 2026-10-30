# v0.4.20 首轮锁屏恢复输入失败

源码 `b7628a0f612764b91ed07d5941deb06682205d13` 的 [41036811932 attempt 1](https://github.com/cyanrbt/walkbuddy/actions/runs/41036811932/attempts/1)，API 35 job `123999553089` 在最先执行的独立 `pip_recovery` 失败；API 36 job `123999553366` 首次全部通过。失败 API 尚未运行模型或新增小窗关闭检测用例，不能称其完成本版原生验证。

已下载 artifact `12968258442`，63,185 字节，ZIP SHA-256 `587235c6b78f94416909f610f6427c858cbe89d87ffe08f6d61a92a43c5f93e4`，核对原始截图、输入和 Activity 快照。前两次恢复循环通过，随后锁屏/唤醒后的 `restart_preview` 按下失败：目标窗口 UID 不匹配，目标 UID 10209；应用按钮 focus=true、附着/可见/启用，所有祖先均已布局且无待布局请求，中心来自 `Rect(16, 552 - 304, 604)`。

实际截图为完整的停止/手动恢复页，没有 Launcher ANR 弹窗；异常后采集的输入快照显示 FocusedApplications 与 FocusedWindows 都属于 PreviewActivity，应用输入窗口覆盖 `[0,0][320,640]`、touchableRegion 相同、ownerUid=10209。此快照只能证明采集时状态，不能还原注入那一瞬间的接收窗口。生命周期记录显示按下前后又发生 pause，不能把 UI focus=true 当作输入路由已稳定的证明；本次日志没有 ANR，不能归因为上次的 Launcher 弹窗。

另直接读取 [Android 15 Instrumentation 官方源码](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/app/Instrumentation.java)：`sendPointerSync` 在按下前已等待窗口动画及输入事务，再按当前 UID 注入。因此“缺少任何动画同步”没有证据，不改为全局注入来绕过目标 UID 限制，也不增加重试点击、固定等待或放宽原有截止。

首轮全部终态后，仅请求原提交失败作业重跑。attempt 2 最终成功，API 35 job `124001689872` 通过，API 36 的首次成功结果保留；已下载 API 35 成功附件并核对新增用例，见[小窗操作记录](pip-detection-control-2026-10-30.md)。重跑通过不表示这次输入/窗口时序根因已修复。
