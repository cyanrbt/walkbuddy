# 取样时效与异步亮度等待分离

此修订仍在实验分支，未发布。主分支与可下载版本仍为 v0.4.37。

## 原始证据与语义差异

[公开 v0.4.37 的 PreviewActivity](https://github.com/cyanrbt/walkbuddy/blob/v0.4.37/app/src/main/java/com/cyanrbt/walkbuddy/PreviewActivity.java)在取样前后以 `now-frameAt > 1000` 跳过旧观测，`checkHealth` 没有独立的亮度 1000ms 停机条件；[SessionState](https://github.com/cyanrbt/walkbuddy/blob/v0.4.37/app/src/main/java/com/cyanrbt/walkbuddy/SessionState.java)允许采集和显示帧龄恰好 1200ms，超过后才停止。

早期未发布的 `BrightnessFlight` 则把最早到期起 1000ms 设成整体请求失败条件。这是一项新增的、更早停机策略，不能称为完全保留公开版时效语义。[实际失败](brightness-initial-ready-native-first-2026-10-31.json)的 API 36 首轮 pip 在尚未主动旋转的几何 step 0 停止：采集帧龄 1033ms、显示帧龄 1004ms，均未超过公开版的画面截止。原错误、ZIP 和失败结果继续保留；这次没有保存具体复制/交付阶段，不能据此宣称已经证明 GPU、RenderThread 或排队的精确根因。

## 当前候选规则

- 仍每 1000ms 到期检查一次，沿用原始观测时间；超过 1000ms 的旧观测拒绝启动请求，恰好 1000ms 与公开版一致。
- 新的异步有界等待与画面时效对齐：从最早到期帧起最多 1200ms，恰好 1200ms 可交付，超过后停止。该等待上限是异步实现新增的保护，并不是公开版已有的独立亮度超时。更新的帧或迟到的全局槽位不能延长最早期限。
- 采集/显示 1200ms、模型结果 1000ms、分析 1500ms 与原模型取样规则不变。1004ms 的有效亮度可以完成，但同龄模型结果仍不可采用；有效暗画面仍按原阈值停止。
- 全进程最多一个未结束复制；停止后清空监听，实际回调返回后才释放原像素缓冲和槽位。错误停止、时钟回退拒绝、原始观测和主动恢复规则保持。

新增 JVM 回归直接比较 `SessionState` 的时效边界，并覆盖 1004ms 亮度与旧模型结果的不同决策；原所有权、重复回调、旧帧不能延长期限和检查周期用例保留。原生组件增加受控时钟推进 1004ms 的暗像素交付，并验证超过 1200ms 的请求仍被判过期；这是时效逻辑验证，不是实际复制性能计时。

本地 259 项 JVM 全部通过（0 失败/错误/跳过），Lint 0 错误、21 警告，应用及原生测试 APK 构建 46 秒成功；产品范围与 v0.4.38 / 50 发布元数据检查通过。新的完整云端结果待取得，不提前发布。

## 后续失败记录与日志范围

原生 PiP 失败时，先取得当前预览已经冻结的首次问题报告，按进程和单调时间写入独立文件，再采集原有截图和窗口证据。这样后续超时可区分 WAITING、QUEUED、COPYING、DELIVERING 和结果码；失败后报告读取本身不刷新或修复预览，采证失败不替代原始错误。

Perfetto 仅需三个应用 `android.util.Log` 标签，现显式使用 `LID_DEFAULT` 主文本日志 buffer，保留 INFO、原三个标签与全部完整性检查。此前省略 log_ids 时，[官方实现](https://github.com/google/perfetto/blob/v59.1/src/traced/probes/android_log/android_log_data_source.cc)会默认读取含 EVENTS 等多个 buffer，并在过滤标签前解析记录。该范围调整避免读取未使用的日志源，不证明之前那条损坏消息来自哪个 buffer。系统/崩溃/ANR 的独立 adb 证据保留；既有日志解析失败和单次复验结果继续保存，未因此降低完整性门槛。
