# 预览停顿系统轨迹

## 要回答的问题

此前 v0.4.36 候选的 Android 16 在手动恢复后自然出现 1246ms 采集/显示帧龄，超过 1200ms 保护截止，见[原记录](recovery-presentation-2026-10-31.md)。v0.4.37 的读回墙钟/CPU 可以描述任务耗时，但还不能区分主线程调度、RenderThread 工作或其它等待。历史失败没有系统调度轨迹，无法追补其精确根因。

## 采证范围

- 新的 `Preview system trace` 工作流复用 Android 15/16 的既有虚拟 Camera2 环境。每套独立运行三轮 `pip_recovery`，再关闭采证并运行原有十项检查。三轮按时间顺序观测，不作为 A/B 性能实验。
- Perfetto 最长 120 秒，32 MiB 调度/图形缓冲和 4 MiB 日志/进程缓冲，每秒写盘、每 10 秒请求数据源提交，单文件上限 128 MiB。采集调度、线程状态、gfx/view/camera/binder、应用三个日志标签；没有图像或音频采集。定期提交和写盘也有额外开销。
- 仅测试 APK 在显式 `preview_trace=true` 参数下安装主 Looper 消息观察器，以 `wb_main:dispatch` 标记消息；四次主动修改 `captureAt` 的测试区段使用 `wb_test:forced_expiry`。原生主程序不包含此观察器。日志和区段按 Perfetto 统一时钟关联；LOCKED/HIDDEN 单独计入生命周期事件。
- 先验证 `ro.kernel.qemu=1`，不使用 adb root 或全局 kill。保存 Perfetto 返回的 PID，发送 SIGINT 后等其退出再下载，避免把信号已发送当成写盘完成；失败仍保留原始轨迹、日志和探针结果。远端 instrumentation 超时后明确停止该测试应用，保留部分输出。
- 主机解析器固定为 [Perfetto v59.1](https://github.com/google/perfetto/releases/tag/v59.1)，官方 linux-amd64.zip SHA-256 为 `7deb8c2572c6ae7ec80cd719d388041079827b0c44a1659694d5d88faa4f501e`，提取前核验。SQL 输出不截行；采集摘要核验 trace SHA-256，缺调度、主线程/渲染线程、四个主动过期区段、对应停止日志或存在解析丢失/错误时明确失败。
- 长消息摘要分别列主线程 CPU、线程状态和重叠的渲染 CPU/区段；时间重叠不证明因果。主消息观察器和系统采集本身有额外开销，结果不扩大为无采证环境、真机帧率或耗电结论。

## 验证进度

本地 26 项 CI 工具测试全部通过，覆盖主动/自然截止分类、生命周期分类、日志时间范围、区间交集、缺失数据、丢失统计、摘要不符和录制失败清理。三份工作流的 actionlint 和两份 shell 语法检查通过。官方 v59.1 已按摘要完整下载，其真实 SQL 引擎成功解析本轮 SQL 的合成 JSON 轨迹；该合成文件没有调度数据，不能充当 Android 运行结果。应用及原生测试 APK 本地构建成功，实际 DEX 确认新观察器只在测试 APK；[本地记录](preview-system-trace-local-2026-10-31.json)保留检查范围。

模拟器两套结果和实际原始轨迹尚待推送后取得。生产 `app/src/main/**`、`app/build.gradle`、版本 0.4.37 / 49、固定签名和发布依赖均保持；本轮是诊断工具，不生成重复版本标签。录制流程参考 [Perfetto 后台采集](https://perfetto.dev/docs/learning-more/tracing-in-background)，分析接口参考[命令行分析](https://perfetto.dev/docs/getting-started/command-line-analysis)。

## 首轮失败与修订

提交 `0ccb9ae40c49daf3ff65775e82c163eb3934a729` 的[常规 UI 41150469412](https://github.com/cyanrbt/walkbuddy/actions/runs/41150469412) attempt 1 两套各十项通过；[系统轨迹 41150469510](https://github.com/cyanrbt/walkbuddy/actions/runs/41150469510) attempt 1 两套失败。四个完整 ZIP 均已下载核对 GitHub 字节数、摘要及 ZIP CRC，十项原始记录与报告重新解析一致。六次受采证的四轮恢复探针本身均成功，失败发生在轨迹完整性检查，不能将流程写成通过。

原始文件表明调度缓冲达到 32 MiB 上限，较早的消息/主动过期区段被覆盖。API 35 三轮分别覆盖 29,032,448 / 18,841,600 / 15,007,744 字节；API 36 为 20,512,768 / 15,499,264 / 9,601,024 字节。仅有环形缓冲时，`traced_buf_bytes_overwritten` 的严重级别是 `info`，原先只查 `data_loss/error` 不足；四区段门槛拦住了五轮，剩下一轮虽四区段齐全，较早启动记录仍已覆盖，也不能作为全程完整证据。官方原生解析器重读 API 36 第一轮，确认首个日志在 45.887 秒而主消息记录从 50.666 秒才开始，与覆盖统计相符。原始硬件类别提示另有模拟器不具备的厂商显示事件，作为 notice 保留；未将其当成调度丢失或应用故障原因。

修订启用每秒写盘并设 128 MiB 文件上限，保留时间和缓冲上限；明确把覆盖/丢弃计数也列入失败条件。记录落在观察器时间范围外的停止日志单独保留，不将其强行归为自然或主动过期。新增 `info` 覆盖统计的回归，工具测试共 27 项通过；[首轮完整来源和重解析结果](preview-system-trace-first-2026-10-31.json)保留，不重跑首轮消除红叉。配置与统计解释参考 [Perfetto 缓冲说明](https://perfetto.dev/docs/concepts/buffers)及[流式写盘配置](https://perfetto.dev/docs/concepts/config)。修订后的云端结果仍待取得，历史自然停顿根因仍未确定。

## 流式录制的配置错误

提交 `67a1c9b1fe2f49f79d49de11909bd389172856de` 的[常规 41151682381](https://github.com/cyanrbt/walkbuddy/actions/runs/41151682381) attempt 1 两套各十项通过。[系统轨迹 41151682335](https://github.com/cyanrbt/walkbuddy/actions/runs/41151682335) attempt 1 仍失败：四个完整附件均已下载核对摘要、字节数及 ZIP CRC，十项原始记录重放一致；六次受采证恢复均通过，所有四区段和停止日志都能对应，没有非零覆盖/丢弃统计，但六条均报告 `config_write_into_file_no_flush=1 (error/trace/)`。原始六条 trace 已用固定版本的官方原生 SQL 引擎重读，消息、停止分类、CPU/线程状态与 CI 解码一致。此配置错误存在，仍不能称为采证通过；[完整来源](preview-system-trace-stream-2026-10-31.json)保留。

增加 `flush_period_ms:10000`，与每秒文件写入区分，保证数据源周期提交而非仅定期读取中央缓冲；字段与开销说明见[官方 TraceConfig](https://perfetto.dev/docs/reference/trace-config-proto)。失败摘要改为直接列统计名、值和类型，便于在 Actions 页面判断原因；原覆盖、缺失、错误门槛均保留，27 项工具测试通过。没有修改应用、相机截止或签名，也未重跑旧失败来改变记录；新结果待实际运行后补充。
