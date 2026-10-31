# 输入焦点与采证文件传输

本轮修改 CI 诊断工具，产品仍为 v0.4.37 / 49。38 项工具测试通过；没有 APK、密钥、缓存或机器配置提交。[本地与原始来源](ci-input-focus-2026-10-31.json)区分真实运行、离线重放与故障注入。

## 撤回后的云端结果

源码 `066e2245529c8a69edbd3045347b0cfb118a3ea0` 的[像素专项 41162050269](https://github.com/cyanrbt/walkbuddy/actions/runs/41162050269)和[系统轨迹 41162050199](https://github.com/cyanrbt/walkbuddy/actions/runs/41162050199)均首次成功。[普通 UI 41162050197](https://github.com/cyanrbt/walkbuddy/actions/runs/41162050197)的 API 35 首次失败，保留的 API 36 首次成功；仅请求失败作业复验，API 35 attempt 2 通过，整轮终态成功。API 36 在 attempt 2 的查询中有新映射 ID，但开始/结束时间仍为首次运行，不冒充第二次执行。

全部七份成功 ZIP 已实际下载核对 GitHub 长度、SHA-256、CRC 和来源，原报告重放一致，六条原始轨迹由官方固定 v59.1 本地重读。[完整来源](brightness-withdrawal-native-2026-10-31.json)保留。一次主机下载另遇 curl 18，重传的是同一附件且最终摘要一致，不是重跑探针。

API 35 首次在锁屏后等待 `recovery wake and unlock` 超时，随后多项窗口/输入检查失败；原始输入没有聚焦窗口，Activity 仍为 STOPPED、task `isSleeping=true`，电源日志已记录醒来。实际截图为状态栏下黑屏，没有 ANR 观察。具体 wake/任务状态根因仍未知；[窗口、输入、Activity 与电源原始记录](withdrawal-wake-2026-10-31.json)和[截图](withdrawal-wake-2026-10-31.png)保留，不与后面的 Launcher ANR 混为一因。

## 独立实验中的可确认阻挡

[阶段实验 41162976895](https://github.com/cyanrbt/walkbuddy/actions/runs/41162976895) attempt 1 失败。API 35 三轮在首页点击前失败，没有亮度请求记录。原图明确是 Pixel Launcher 未响应弹窗遮住首页，InputDispatcher 的 `FocusedWindows` 主显示器行也指向 `Application Not Responding: com.google.android.apps.nexuslauncher`。[实际截图](brightness-stages-launcher-2026-10-31.png)与[完整实验记录](brightness-stages-first-2026-10-31.json)保留。此处有直接阻挡证据；不能把只有同时出现的 ANR 日志或其它显示器焦点也作相同推断。

此前摘要只读取窗口文件的 `mCurrentFocus`，遗漏已采集到的输入快照焦点。现在同时读取严格的 `FocusedWindows` 当前区段，只将主显示器的外部 ANR 弹窗归为这类输入阻挡；`FocusRequests` 历史、普通窗口列表、无焦点、其它显示器和应用自身 ANR 都不误归为外部阻挡。四项回归与两份实际附件重放验证新分类，原十项状态及整个失败结果保持。

## 同一停止文件的传输

实验 API 36 三次实际恢复均通过，但第一条轨迹两次复制均失败：第一次在 0% 返回 1，第二次明确 `device offline`，没有任何有效字节；原停止文件预期 54,357,819 字节。后两条完整轨迹已取得，五个实际存在的原始文件均重新解析；不能写成六条完整，也不能将整个实验写成成功。

第二次复制前新增最多 10 秒的 `adb wait-for-device`，仍只复制原来同一个已停止文件，共最多两次；原先取得的长度和 SHA-256 不变，错误仍失败，不重新录制、重跑探针或放宽校验。新增两项回归验证恢复连接后的同文件复制以及连接超时仍失败，保留一次原探针的要求。INFO 亮度阶段日志也写入后备文本，以免原始文件取回失败后只剩 WARN。该后备分支有额外回归覆盖；新的实际离线恢复成功仍待后续运行，不用故障注入冒充真机或云端断线验收。

当前 v0.4.37 Release 和固定签名包保持，异步候选及其阶段观察继续留在独立分支，未发布 v0.4.38。实体设备、真实 AR、道路、TalkBack、帧率和耗电均无新验收。
