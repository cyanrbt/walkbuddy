# 预览停顿系统轨迹

## 要回答的问题

此前 v0.4.36 候选的 Android 16 在手动恢复后自然出现 1246ms 采集/显示帧龄，超过 1200ms 保护截止，见[原记录](recovery-presentation-2026-10-31.md)。v0.4.37 的读回墙钟/CPU 可以描述任务耗时，但还不能区分主线程调度、RenderThread 工作或其它等待。历史失败没有系统调度轨迹，无法追补其精确根因。

## 采证范围

- 新的 `Preview system trace` 工作流复用 Android 15/16 的既有虚拟 Camera2 环境。每套独立运行三轮 `pip_recovery`，再关闭采证并运行原有十项检查。三轮按时间顺序观测，不作为 A/B 性能实验。
- Perfetto 最长 120 秒，32 MiB 调度/图形环形缓冲和 4 MiB 日志/进程缓冲。采集调度、线程状态、gfx/view/camera/binder、应用三个日志标签；没有图像或音频采集。
- 仅测试 APK 在显式 `preview_trace=true` 参数下安装主 Looper 消息观察器，以 `wb_main:dispatch` 标记消息；四次主动修改 `captureAt` 的测试区段使用 `wb_test:forced_expiry`。原生主程序不包含此观察器。日志和区段按 Perfetto 统一时钟关联；LOCKED/HIDDEN 单独计入生命周期事件。
- 先验证 `ro.kernel.qemu=1`，不使用 adb root 或全局 kill。保存 Perfetto 返回的 PID，发送 SIGINT 后等其退出再下载，避免把信号已发送当成写盘完成；失败仍保留原始轨迹、日志和探针结果。远端 instrumentation 超时后明确停止该测试应用，保留部分输出。
- 主机解析器固定为 [Perfetto v59.1](https://github.com/google/perfetto/releases/tag/v59.1)，官方 linux-amd64.zip SHA-256 为 `7deb8c2572c6ae7ec80cd719d388041079827b0c44a1659694d5d88faa4f501e`，提取前核验。SQL 输出不截行；采集摘要核验 trace SHA-256，缺调度、主线程/渲染线程、四个主动过期区段、对应停止日志或存在解析丢失/错误时明确失败。
- 长消息摘要分别列主线程 CPU、线程状态和重叠的渲染 CPU/区段；时间重叠不证明因果。主消息观察器和系统采集本身有额外开销，结果不扩大为无采证环境、真机帧率或耗电结论。

## 验证进度

本地 26 项 CI 工具测试全部通过，覆盖主动/自然截止分类、生命周期分类、日志时间范围、区间交集、缺失数据、丢失统计、摘要不符和录制失败清理。三份工作流的 actionlint 和两份 shell 语法检查通过。官方 v59.1 已按摘要完整下载，其真实 SQL 引擎成功解析本轮 SQL 的合成 JSON 轨迹；该合成文件没有调度数据，不能充当 Android 运行结果。应用及原生测试 APK 本地构建成功，实际 DEX 确认新观察器只在测试 APK；[本地记录](preview-system-trace-local-2026-10-31.json)保留检查范围。

模拟器两套结果和实际原始轨迹尚待推送后取得。生产 `app/src/main/**`、`app/build.gradle`、版本 0.4.37 / 49、固定签名和发布依赖均保持；本轮是诊断工具，不生成重复版本标签。录制流程参考 [Perfetto 后台采集](https://perfetto.dev/docs/learning-more/tracing-in-background)，分析接口参考[命令行分析](https://perfetto.dev/docs/getting-started/command-line-analysis)。
