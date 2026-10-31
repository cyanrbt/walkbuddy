# 取样时效与异步亮度等待分离

此修订已进入 v0.4.38 并完成正式标签发布、两套原生检查与实际固定签名 APK 核验；原始实验与后续开发复验按各自源码保留。

## 原始证据与语义差异

[公开 v0.4.37 的 PreviewActivity](https://github.com/cyanrbt/walkbuddy/blob/v0.4.37/app/src/main/java/com/cyanrbt/walkbuddy/PreviewActivity.java)在取样前后以 `now-frameAt > 1000` 跳过旧观测，`checkHealth` 没有独立的亮度 1000ms 停机条件；[SessionState](https://github.com/cyanrbt/walkbuddy/blob/v0.4.37/app/src/main/java/com/cyanrbt/walkbuddy/SessionState.java)允许采集和显示帧龄恰好 1200ms，超过后才停止。

早期未发布的 `BrightnessFlight` 则把最早到期起 1000ms 设成整体请求失败条件。这是一项新增的、更早停机策略，不能称为完全保留公开版时效语义。[实际失败](brightness-initial-ready-native-first-2026-10-31.json)的 API 36 首轮 pip 在尚未主动旋转的几何 step 0 停止：采集帧龄 1033ms、显示帧龄 1004ms，均未超过公开版的画面截止。原错误、ZIP 和失败结果继续保留；这次没有保存具体复制/交付阶段，不能据此宣称已经证明 GPU、RenderThread 或排队的精确根因。

## 当前候选规则

- 仍每 1000ms 到期检查一次，沿用原始观测时间；超过 1000ms 的旧观测拒绝启动请求，恰好 1000ms 与公开版一致。
- 新的异步有界等待与画面时效对齐：从最早到期帧起最多 1200ms，恰好 1200ms 可交付，超过后停止。该等待上限是异步实现新增的保护，并不是公开版已有的独立亮度超时。更新的帧或迟到的全局槽位不能延长最早期限。
- 采集/显示 1200ms、模型结果 1000ms、分析 1500ms 与原模型取样规则不变。1004ms 的有效亮度可以完成，但同龄模型结果仍不可采用；有效暗画面仍按原阈值停止。
- 全进程最多一个未结束复制；停止后清空监听，实际回调返回后才释放原像素缓冲和槽位。错误停止、时钟回退拒绝、原始观测和主动恢复规则保持。

新增 JVM 回归直接比较 `SessionState` 的时效边界，并覆盖 1004ms 亮度与旧模型结果的不同决策；原所有权、重复回调、旧帧不能延长期限和检查周期用例保留。原生组件增加受控时钟推进 1004ms 的暗像素交付，并验证超过 1200ms 的请求仍被判过期；这是时效逻辑验证，不是实际复制性能计时。

本地 259 项 JVM 全部通过（0 失败/错误/跳过），Lint 0 错误、21 警告，应用及原生测试 APK 构建 46 秒成功；产品范围与 v0.4.38 / 50 发布元数据检查通过。43 项 Python 工具与三个相关工作流 actionlint 通过。[本地记录](brightness-preview-budget-local-2026-10-31.json)保留实际构建与源文件摘要。

## 当前候选完整云端结果

源码 `1759d0bef120544911482bab4335336384b8ce18` 的[像素专项 41170728106](https://github.com/cyanrbt/walkbuddy/actions/runs/41170728106) API 26/35/36 和[阶段采证 41170728124](https://github.com/cyanrbt/walkbuddy/actions/runs/41170728124) API 35/36 均首次终态成功。五份原始 ZIP 全部实际下载，匹配服务端长度、SHA-256 与 CRC；三套初始化 ready 在开始前、新 ready 在明确停止后的顺序均验证通过，原 96 组冻结像素和 12 组 Camera2/EGL 逐像素及时间戳门槛通过。

四个 Android 15/16 作业各十项原生检查通过，绘制回调提交、退休旧 View 拒绝、默认仅预览无模型读回任务及 20 项异步组件检查均有实际通过记录。六条原始系统轨迹逐条匹配捕获长度/摘要，固定版官方 SQL 引擎重放与云端报告一致，全部完整；没有使用旧的失败复验替代它们。[完整原始来源与重放结果](brightness-preview-budget-native-2026-10-31.json)保留。

本轮阶段记录包含 75 个完整请求：73 个 ACCEPTED，另两个在停止后 RETIRED，只做清理；不将退休结果当作交付。API 35/36 接受请求的入口等待中位数均 0ms；到交付最大 876ms、683ms，仍存在复制完成和主线程交付等待。这是带采证开销的模拟器观察，不是配对 A/B、实体帧率或耗电改善证明，不能据此称历史停顿或旧唤醒离屏问题根因已解决。该候选满足本轮合入前门槛；正式发布仍须新标签工作流与实际固定签名 APK 独立核验。

## 正式标签与开发复验分别核对

标签源码 `a4bbb537e6053674fd565c13fff992e959b936e7` 的[41172338591](https://github.com/cyanrbt/walkbuddy/actions/runs/41172338591) attempt 1 两套 UI 与 APK 均成功。标签全部应用/测试源码与候选 `1759d0b` 相同；实际 [APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.38/Walk-Buddy-v0.4.38-arm64-debug.apk) 完整下载，9,996,881 字节 / SHA-256 `110c6c2d027347805484d6a41a57c4200888517918a3af40aa785891659a654d`，原固定证书 `ffdf47c306d335d2ee3bc39256d43c470fda5572e80662155ed31dc1c16aae0d`、版本 0.4.38 / 50、ARM64、模型、90 槽/80 类中文标签、权限及测试类未进入生产包均核验通过。[实际安装包记录](apk-v0.4.38.json)保留。

正式两份 UI 附件已完整取得，服务端长度、SHA-256、ZIP CRC 匹配，十项摘要与本地重放一致，新增绘制提交、20 项组件、退休回调和仅预览无模型任务标记齐全。[正式原始结果](brightness-v038-release-native-2026-10-31.json)保留。最初本地整包下载超时后签名地址失效；随后仅按原地址新签名的 HTTP206 范围取回原字节，严格校验范围与完整摘要，没有重跑正式构建或探针。

同源码主分支普通 UI 与系统轨迹均首次成功，此处只重新核对其终态，没有冒充另外重复下载和重放它们的全部附件；合入前六条实际重放来源已独立列明。主分支像素专项 API 26 再次出现独立冻结夹具通知交错，后续[显式排空](camera-explicit-drain-2026-10-31.md)仅修改测试，不改变生产源码和已发布 APK。

主分支 API 35 还保留一个不同的实际过期样本：[首次冻结的问题报告](brightness-v038-main-first-expiry-2026-10-31.json)显示采集/显示帧龄 1436/1420ms，亮度阶段 DELIVERING，排队 9ms、请求墙钟 1383ms、调用与像素处理 CPU 0ms、交付 11ms、结果码 0。它已经超过原 1200ms 画面截止，不能说只是早期候选 1000ms 停机语义；该快照不能单独证明 GPU、RenderThread 或调度的精确因果。后续应对照同一冷启动时段的完整轨迹，不扩大截止来掩盖等待。[正式发布及各轮复验状态](v038-publication-status-2026-10-31.json)保留，不宣称全部历史问题已解决。

## 后续失败记录与日志范围

原生 PiP 失败时，先取得当前预览已经冻结的首次问题报告，按进程和单调时间写入独立文件，再采集原有截图和窗口证据。这样后续超时可区分 WAITING、QUEUED、COPYING、DELIVERING 和结果码；失败后报告读取本身不刷新或修复预览，采证失败不替代原始错误。

Perfetto 仅需三个应用 `android.util.Log` 标签，现显式使用 `LID_DEFAULT` 主文本日志 buffer，保留 INFO、原三个标签与全部完整性检查。此前省略 log_ids 时，[官方实现](https://github.com/google/perfetto/blob/v59.1/src/traced/probes/android_log/android_log_data_source.cc)会默认读取含 EVENTS 等多个 buffer，并在过滤标签前解析记录。该范围调整避免读取未使用的日志源，不证明之前那条损坏消息来自哪个 buffer。系统/崩溃/ANR 的独立 adb 证据保留；既有日志解析失败和单次复验结果继续保存，未因此降低完整性门槛。
