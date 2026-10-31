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
- 57 项工具测试通过，8.919 秒；应用和测试 APK 本地编译通过。[最终本地文件哈希与构建记录](rejected-restore-down-local-2026-10-31.json)保留。新增代码仅在 `androidTest`。
- 三个专项的触发路径补充共享触摸代码和资产；actionlint 与差异检查通过。普通 UI、两核轨迹、最多四核轨迹与三套像素专项将分别执行，不放宽原生断言。

## 源码 90169c3 的云端首轮

源码提交 `90169c3b248d92e42d5d4d6049fcb7cf2d5acfee` 已推送，四个流程九个作业在 attempt 1 中八个成功、一个失败。[首轮终态和附件元数据](rejected-restore-first-status-2026-10-31.json)保留，不能称全绿：

- [普通 UI](https://github.com/cyanrbt/walkbuddy/actions/runs/41186158105)：API 35/36 均首次成功。两份完整附件分别为 14,040,892 / 14,008,034 字节，核对 GitHub 服务端 SHA256、全部 ZIP CRC，再用仓库脚本重放十项原生检查，均通过。
- [最多四核轨迹](https://github.com/cyanrbt/walkbuddy/actions/runs/41186158161)：两套首次成功；[像素专项](https://github.com/cyanrbt/walkbuddy/actions/runs/41186158158)：API 26/35/36 三套首次成功。本次只核对其作业终态与部分作业日志，不将它们表述为本地完整附件或全部原始轨迹重放。
- [两核轨迹](https://github.com/cyanrbt/walkbuddy/actions/runs/41186158156/attempts/1)：API 36 首次成功，API 35 作业 `124493392413` 失败。编译成功，但恢复流程在首页 `start` 尚未获得焦点时就超时；没有进入本轮受保护的恢复点击。

普通 UI 的两套 `pip-timing.txt` 各有七次 `restoreGuard=true`，确认真实触摸路径启用了保护；均无 `restore DOWN rejected` 或独立拒收文件。27 项历史日志规则检查在云端再次通过，但本轮没有新的真实系统拒收样本，不能宣称重新准备分支已端到端验证或竞态已根治。

## 保留的两核 API 35 失败

失败附件 `13035949402` 为 44,284,289 字节，服务端 SHA256 `0d54409ab1054ae18603d086e16ce0b1a44423526fea144d39989948ee1c8800`，完整下载、大小、SHA256、ZIP CRC 均通过。失败截图已实际查看：`Pixel Launcher isn't responding` 弹窗覆盖首页。对应 InputDispatcher 的焦点明确属于 `Application Not Responding: com.google.android.apps.nexuslauncher`。系统在 08:52:12.062 记录 Launcher ANR，早于这份生命周期记录中的首个应用进程 08:52:20.792；这能解释已保存的首页失焦，不能据此把后续所有屏幕关闭失败或此前自然画面过期都归因于 Launcher。

该运行确实配置两核，模拟器在线 CPU 为 `0-1`。第一条轨迹的 Camera2/EGL 前缀与卸载重装均通过；三次恢复在首页失焦处失败，均缺少四个强制停止标记。三条原始轨迹分别为 63,770,340 / 22,863,269 / 3,477,769 字节，已核对各自哈希、配置及源码，用固定版本官方 Perfetto 重新解码，仓库脚本仍正确报告三条无效，均没有亮度请求样本。没有放宽断言、跳过失败或自动关闭系统弹窗。

[完整本地重放与窗口证据](rejected-restore-native-2026-10-31.json)保留，包含失败截图及输入窗口文件的哈希。确认外部弹窗后，只申请原失败作业的一次复验；请求已提交，结果尚待确认。成功作业不重复执行，首轮失败继续单独记录。

本轮不改 `app/src/main` 或 `app/build.gradle`，应用和安装包仍为 v0.4.38；没有新增实体设备验收，不发布重复 APK。
