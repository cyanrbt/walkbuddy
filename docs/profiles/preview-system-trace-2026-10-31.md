# 预览停顿系统轨迹

源码 `355e004fef713c514f3990b6d9ef071b80df6f77` 的[系统轨迹 41154316569](https://github.com/cyanrbt/walkbuddy/actions/runs/41154316569)和[常规检查 41154316656](https://github.com/cyanrbt/walkbuddy/actions/runs/41154316656)均 attempt 1 终态成功。四个完整附件下载核对摘要、长度和 ZIP CRC，原有十项报告重放一致；六条原始轨迹用固定版官方 SQL 引擎本地重读，结果与 CI 一致。31 项工具测试通过。[本轮机器可读记录](preview-system-trace-kernel-2026-10-31.json)保留全部来源和边界，下面的失败轮次均为历史，不覆盖、不重跑消除红叉。

本轮应用源码和发布流程仍与 v0.4.37 相同，远端 `v0.4.37` 标签仍指向 `afa1e10cf7cd0d3d0345e72015030b779695f888`；这次交付为可复核的诊断工具，不另发重复应用版本，也不宣称历史 1246ms 停顿已修复。

## 要回答的问题

此前 v0.4.36 候选的 Android 16 在手动恢复后自然出现 1246ms 采集/显示帧龄，超过 1200ms 保护截止，见[原记录](recovery-presentation-2026-10-31.md)。v0.4.37 的读回墙钟/CPU 可以描述任务耗时，但还不能区分主线程调度、RenderThread 工作或其它等待。历史失败没有系统调度轨迹，无法追补其精确根因。

## 采证范围

- 新的 `Preview system trace` 工作流复用 Android 15/16 的既有虚拟 Camera2 环境。每套独立运行三轮 `pip_recovery`，再关闭采证并运行原有十项检查。三轮按时间顺序观测，不作为 A/B 性能实验。
- Perfetto 最长 120 秒，32 MiB 调度/图形缓冲和 4 MiB 日志/进程缓冲，每秒写盘、每 10 秒请求数据源提交，单文件上限 128 MiB。采集调度、线程状态、gfx/view/camera/binder、应用三个日志标签；没有图像或音频采集。定期提交和写盘也有额外开销。
- 内核 ftrace 请求每 CPU 至少 8 MiB，读取周期 100ms；其它并发 ftrace 会话可能影响实际参数，所以每轮保留 `perfetto --query` 的服务状态。此内核缓冲与上述中央缓冲分别设置，均只在临时模拟器诊断中使用。
- 仅测试 APK 在显式 `preview_trace=true` 参数下安装主 Looper 消息观察器，以 `wb_main:dispatch` 标记消息；四次主动修改 `captureAt` 的测试区段使用 `wb_test:forced_expiry`。原生主程序不包含此观察器。日志和区段按 Perfetto 统一时钟关联；LOCKED/HIDDEN 单独计入生命周期事件。
- 先验证 `ro.kernel.qemu=1`，不使用 adb root 或全局 kill。保存 Perfetto 返回的 PID，发送 SIGINT 后等其退出再下载，避免把信号已发送当成写盘完成；失败仍保留原始轨迹、日志和探针结果。停止后读取远端文件大小和 SHA-256，仅在复制同一个文件时允许最多两次传输，各次标准输出、错误、字节数与摘要均保留，匹配后才将临时文件改为正式轨迹名。不会重跑探针来更换采样。远端 instrumentation 超时后明确停止该测试应用，保留部分输出。
- 主机解析器固定为 [Perfetto v59.1](https://github.com/google/perfetto/releases/tag/v59.1)，官方 linux-amd64.zip SHA-256 为 `7deb8c2572c6ae7ec80cd719d388041079827b0c44a1659694d5d88faa4f501e`，提取前核验。SQL 输出不截行；采集摘要核验 trace SHA-256，缺调度、主线程/渲染线程、四个主动过期区段、对应停止日志或存在解析丢失/错误时明确失败。
- 长消息摘要分别列主线程 CPU、线程状态和重叠的渲染 CPU/区段；时间重叠不证明因果。主消息观察器和系统采集本身有额外开销，结果不扩大为无采证环境、真机帧率或耗电结论。

## 验证进度

初始提交本地 26 项 CI 工具测试全部通过，覆盖主动/自然截止分类、生命周期分类、日志时间范围、区间交集、缺失数据、丢失统计、摘要不符和录制失败清理，最终修订增至 31 项。三份工作流的 actionlint 和两份 shell 语法检查通过。官方 v59.1 已按摘要完整下载，其真实 SQL 引擎成功解析初稿 SQL 的合成 JSON 轨迹；该合成文件没有调度数据，不能充当 Android 运行结果。应用及原生测试 APK 本地构建成功，实际 DEX 确认新观察器只在测试 APK；[初始本地记录](preview-system-trace-local-2026-10-31.json)保留检查范围。

模拟器最终结果和原始轨迹已取得，见开头及下文正式修订结果。生产 `app/src/main/**`、`app/build.gradle`、版本 0.4.37 / 49、固定签名和发布依赖均保持。录制流程参考 [Perfetto 后台采集](https://perfetto.dev/docs/learning-more/tracing-in-background)，分析接口参考[命令行分析](https://perfetto.dev/docs/getting-started/command-line-analysis)。

## 首轮失败与修订

提交 `0ccb9ae40c49daf3ff65775e82c163eb3934a729` 的[常规 UI 41150469412](https://github.com/cyanrbt/walkbuddy/actions/runs/41150469412) attempt 1 两套各十项通过；[系统轨迹 41150469510](https://github.com/cyanrbt/walkbuddy/actions/runs/41150469510) attempt 1 两套失败。四个完整 ZIP 均已下载核对 GitHub 字节数、摘要及 ZIP CRC，十项原始记录与报告重新解析一致。六次受采证的四轮恢复探针本身均成功，失败发生在轨迹完整性检查，不能将流程写成通过。

原始文件表明调度缓冲达到 32 MiB 上限，较早的消息/主动过期区段被覆盖。API 35 三轮分别覆盖 29,032,448 / 18,841,600 / 15,007,744 字节；API 36 为 20,512,768 / 15,499,264 / 9,601,024 字节。仅有环形缓冲时，`traced_buf_bytes_overwritten` 的严重级别是 `info`，原先只查 `data_loss/error` 不足；四区段门槛拦住了五轮，剩下一轮虽四区段齐全，较早启动记录仍已覆盖，也不能作为全程完整证据。官方原生解析器重读 API 36 第一轮，确认首个日志在 45.887 秒而主消息记录从 50.666 秒才开始，与覆盖统计相符。原始硬件类别提示另有模拟器不具备的厂商显示事件，作为 notice 保留；未将其当成调度丢失或应用故障原因。

修订启用每秒写盘并设 128 MiB 文件上限，保留时间和缓冲上限；明确把覆盖/丢弃计数也列入失败条件。记录落在观察器时间范围外的停止日志单独保留，不将其强行归为自然或主动过期。新增 `info` 覆盖统计的回归，工具测试共 27 项通过；[首轮完整来源和重解析结果](preview-system-trace-first-2026-10-31.json)保留，不重跑首轮消除红叉。配置与统计解释参考 [Perfetto 缓冲说明](https://perfetto.dev/docs/concepts/buffers)及[流式写盘配置](https://perfetto.dev/docs/concepts/config)。修订后的云端结果仍待取得，历史自然停顿根因仍未确定。

## 流式录制的配置错误

提交 `67a1c9b1fe2f49f79d49de11909bd389172856de` 的[常规 41151682381](https://github.com/cyanrbt/walkbuddy/actions/runs/41151682381) attempt 1 两套各十项通过。[系统轨迹 41151682335](https://github.com/cyanrbt/walkbuddy/actions/runs/41151682335) attempt 1 仍失败：四个完整附件均已下载核对摘要、字节数及 ZIP CRC，十项原始记录重放一致；六次受采证恢复均通过，所有四区段和停止日志都能对应，没有非零覆盖/丢弃统计，但六条均报告 `config_write_into_file_no_flush=1 (error/trace/)`。原始六条 trace 已用固定版本的官方原生 SQL 引擎重读，消息、停止分类、CPU/线程状态与 CI 解码一致。此配置错误存在，仍不能称为采证通过；[完整来源](preview-system-trace-stream-2026-10-31.json)保留。

增加 `flush_period_ms:10000`，与每秒文件写入区分，保证数据源周期提交而非仅定期读取中央缓冲；字段与开销说明见[官方 TraceConfig](https://perfetto.dev/docs/reference/trace-config-proto)。失败摘要改为直接列统计名、值和类型，便于在 Actions 页面判断原因；原覆盖、缺失、错误门槛均保留，27 项工具测试通过。没有修改应用、相机截止或签名，也未重跑旧失败来改变记录；新结果待实际运行后补充。

## 内核丢失与文件传输失败

提交 `6eb8342c68f53b0f3c29e19e8d74865667d852a1` 的[常规 41152966054](https://github.com/cyanrbt/walkbuddy/actions/runs/41152966054) attempt 1 两套各十项通过；[轨迹 41152965922](https://github.com/cyanrbt/walkbuddy/actions/runs/41152965922) attempt 1 两套失败，六次受采证探针本身均通过。四份完整附件已下载核对摘要、长度及 ZIP CRC，十项记录重放一致；五条实际存在的轨迹均以官方原生 SQL 引擎重读，结果与 CI 一致，四条无错误。不能把四条成功写成整轮通过。

- API 35 第一次：录制进程退出已确认，`adb pull` 在约 60% 返回 1，正式轨迹文件缺失。原记录只保留了进度错误而未保存完整标准输出，无法确定这次传输中断的精确原因。其它两次完整。
- API 36 第一次：四个主动过期均对应，周期提交配置错误已消失，但 `ftrace_cpu_overrun_delta=5350`、`ftrace_cpu_has_data_loss=1`，均为 CPU 1。其它两次完整。该统计证明内核轨迹事件丢失，不证明之前的应用停顿由同一原因造成。

修订请求更大的每 CPU 内核缓冲并缩短读取周期，依据[官方缓冲说明](https://perfetto.dev/docs/concepts/buffers)与[FtraceConfig](https://perfetto.dev/docs/reference/trace-config-proto)，不删除事件或放宽丢失门槛。远端停止文件的只读传输增加有界重试和端到端摘要校验；两次均失败或摘要不符仍失败。补充缺文件时底层原因、逐轮录制/传输结果和启动失败后已知 PID 清理；31 项工具测试通过，涵盖重传不重跑探针及同长度错误摘要拒绝。[原始结果与边界](preview-system-trace-flushed-2026-10-31.json)完整保留；修订后的实际结果待取得。

固定解析器的[检查实现](https://github.com/google/perfetto/blob/v59.1/src/trace_processor/importers/proto/proto_trace_reader.cc)确认：显式提交周期可以避免第二轮的配置错误。第三轮的触发记录稍后才可查询，现只确认流程已创建；未确定延迟发生在推送触发还是状态查询阶段，不重推提交来猜测处理。

## 最终修订结果

提交 `355e004fef713c514f3990b6d9ef071b80df6f77` 的轨迹与常规两个流程均首次成功，两套 Android 的每个流程均各十项通过；六次受采证恢复各四个循环成功。完整附件及来源为：

| 流程 | Android | artifact | 字节数 | SHA-256 |
| --- | --- | --- | ---: | --- |
| 系统轨迹 41154316569 | 15 / API 35 | 13025066321 | 69,359,504 | `08144f977e040f8df1a340e8aafc2923169abda6e5c532806f16f3860f2fc874` |
| 系统轨迹 41154316569 | 16 / API 36 | 13024920965 | 63,944,546 | `687b4cde329163296e2691d056fdfcac29478ee1c3f5a4c31e7c02742cbe4b6c` |
| 常规 41154316656 | 15 / API 35 | 13024107568 | 14,019,928 | `45074c94000a956b1411c36a1bda67983a8f899d3cbaa29529018ff5494f86bc` |
| 常规 41154316656 | 16 / API 36 | 13024722552 | 14,003,589 | `69152f40a8abc89e3a32663090ea4da9f934d8c8711ec21fcf8259c1173d827e` |

六条轨迹均与停止后的远端字节数、SHA-256 一致，本轮均首次传输成功；同文件重传分支有工具回归覆盖，本轮未触发，不能冒充实际中断后重传验收。四区段和对应停止日志在每条均齐全，无非零 error/data_loss、缓冲覆盖、丢弃或内核 overrun_delta；模拟器不具备的厂商显示事件 notice 仍保留。主消息区段数 API 35 三轮为 2,803 / 2,481 / 2,274，API 36 为 2,902 / 2,530 / 2,276。24 次测试主动过期、六次生命周期停止与六次最终停止分别记录；观察器范围之外没有停止日志，本轮未见未标记的 GENERAL 超 1200ms 停止。

本地使用同一官方 v59.1 二进制（SHA-256 `2434129a3af4dc9e2034a476f7b76447b490975213e301769310d3bd0d5d6458`）重读所有六条实际原始 trace，消息、停止分类、CPU、线程状态和渲染区段与 CI 一致；只排除解析器本身运行耗时这种每次变化的 info 统计。四个 UI 原始报告也重新解析一致，没有把部分通过写成整轮成功。

一个可定位的慢读回样例来自 API 35 第一轮：应用整毫秒日志为亮度 294ms / CPU 0ms。对应主消息实际墙钟 294.638598ms，调度 CPU 0.225892ms，状态为 Sleeping 288.629284ms、Runnable 5.783422ms；0ms 是整毫秒取整，不能理解为完全没有 CPU 工作。该消息不重叠任何主动过期，也不含停止日志。重叠的 RenderThread CPU 为 75.288439ms，`copyLayerInto` 区段重叠 94.211604ms，另有绘制与提交区段；原始数值和时间戳保留在 JSON。它支持继续检查同步读回/渲染提交等待，而不是只看像素求和的 CPU 微基准；时间重叠仍不足以证明之前 1246ms 停顿的根因，也不是无采证场景或真机的性能结果。

本轮未重现历史 1200ms 保护截止，不能称其已修复。采证日志和主消息观察器本身会扰动调度；没有实体设备、真实 AR、道路、TalkBack、音频可听性、帧率或耗电的新验收结果。后续替代方案仍需保留原生像素一致性、截止和恢复检查，再用实际结果判断。
