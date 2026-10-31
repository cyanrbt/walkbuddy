# 恢复触摸的明确拒收保护

## 已验证的原始失败

源码 `3cc077c` 的[两核 API 35 首轮作业](https://github.com/cyanrbt/walkbuddy/actions/runs/41180527990/attempts/1)在恢复点击时失败。原始 InputDispatcher 日志记录事件时间 `54407000000ns` 的 DOWN 因目标 PreviewActivity 窗口 paused 且没有任何接收目标被丢弃，随后记录进程 `2162` 的注入失败。应用没有收到 `recover-request`，快照 1201/95ms 是探针主动制造的原停止。[完整原始附件与事件顺序](cpu-capacity-two-core-input-2026-10-31.json)保留；原作业单次复验通过不代表这个竞态已修复。

Android 36 公开 SDK 实际签名中，`UiAutomation` 提供两参数 `injectInputEvent`、`waitForIdle`，未提供可直接调用的 `syncInputTransactions`。AOSP [Instrumentation 实现](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/Instrumentation.java)在 DOWN 前等待输入事务和动画，再向本应用 UID 注入；注入返回值未交给 `sendPointerSync` 的调用方。就绪检查与实际交付之间仍可能经过生命周期变化。本轮继续使用这个面向本应用的真实触摸入口，不反射隐藏系统方法，不改用跨应用注入或 `performClick`。

## 保护条件

仅对已经停止、非长按控件的预览恢复按钮启用额外检查，日志明确记录 `restoreGuard` 是否启用。每次保存注入前日志；当 DOWN 后按钮尚未确认 pressed，读取有限数量的 InputDispatcher/InputManager 日志。只有同时满足以下条件，才允许一次重新准备：

- 新日志包含精确匹配该次事件时间的 DOWN，明确记录没有任何目标收到；相邻记录属于同一 PreviewActivity paused 和同一注入进程。旧缓冲重放、重复事件、其他时间/进程/组件、只有 Taskbar UID 警告或含糊信息均不能授权重试。
- 先发送 CANCEL 结束被拒的序列，随后确认 Activity/原按钮所有者仍有效、预览仍停止、上次启动时间和首次问题快照未变，且摄像头、分析与提醒资源已释放。停止时的生命周期取消可改变 generation，不能把它误当成显式重启，因此核对实际启动时间和问题快照。
- 最多两次 DOWN 尝试，共用原 10 秒就绪等待预算，重新核对事件静默、resumed、焦点、解锁、当前应用、系统视口和可触摸窗口。已送达或无法证明拒收的 DOWN 继续原 UP 流程，不重试；第二次拒收、所有者/启动状态变化或预算不足仍令检查失败。

系统同步调用本身仍可能阻塞；这里共享的是原就绪等待预算，不宣称能中断 Android 同步注入。所有拒收证据保存到独立的 `restore-rejected-down-<pid>-<uptime>.txt`，并记录尝试次数和是否允许重新准备。应用的启动、画面和模型截止均保持。

## 本地验证

- 用原始失败路由中的八行记录创建测试 APK 资产 `restore-down-paused.txt`；纯 Java 检查 27 项，包括真实正例、旧记录/重复/错误时间/错误进程、前一个事件的 paused 不得串入、UP 不得冒充 DOWN，以及次数、预算、活跃状态、按钮所有者、启动时间和问题快照保护。JDK 17 实际编译并执行通过，同一检查将在原生恢复流程中再次执行。
- 57 项工具测试通过，8.919 秒；应用和测试 APK 本地编译通过。新增代码仅在 `androidTest`，最终源码仍需云端重新编译验证。
- 三个专项的触发路径补充共享触摸代码和资产；actionlint 与差异检查通过。普通 UI、两核轨迹、最多四核轨迹与三套像素专项将分别执行，不放宽原生断言。

云端结果待推送后取得。日志规则与重新准备资格的合成检查，不等同于本轮真正发生了系统拒收；只有实际出现新的拒收文件和对应重新准备记录，才能确认真实系统分支被执行。若没有新拒收样本，只报告普通真实触摸通过与规则验证，不称该瞬时竞态已根治。

本轮不改 `app/src/main` 或 `app/build.gradle`，应用和安装包仍为 v0.4.38；没有新增实体设备验收，不发布重复 APK。
