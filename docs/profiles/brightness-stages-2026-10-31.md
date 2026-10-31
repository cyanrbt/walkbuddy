# 异步亮度阶段观察：独立实验

主分支保持已发布的 v0.4.37 产品路径；本实验仅在 `perf/brightness-async-a110908` 分支。原候选 `a1109083bc8fb4ddfa6f492d4740f8f1cad33f2a` 的 [41160797255](https://github.com/cyanrbt/walkbuddy/actions/runs/41160797255) API 35 第二轮在首次进入小窗前停止；旧日志没有分开请求状态与交付时间，无法区分复制错误、固定截止和主线程交付等待。该失败和撤回记录仍保留于[主分支](https://github.com/cyanrbt/walkbuddy/blob/main/docs/profiles/brightness-async-2026-10-31.md)，不重跑原记录来更换样本。

## 本次观察

只有原生 `pip_recovery` 明确传入 `preview_trace=true` 时启用 `BrightnessStage` 标量日志，结束后关闭；其它探针和默认产品路径不启用。每个在途请求记录进程、所有者、单调请求编号、原观测时间、最早到期时间及当前时间。

| 事件 | 所在位置 | 用途 |
| --- | --- | --- |
| DUE | 主线程观察到本周期到期 | 确认最早时间，没有随着新帧延长 |
| QUEUED | 主线程提交 worker 前 | 保存 queuedNs 和从最早到期帧已经等待的时间 |
| SUBMIT / RETURN | worker 发起 PixelCopy 前后 | 判断 API 调用本身是否同步等待；包含启用记录时的观察开销 |
| CALLBACK | PixelCopy 回调进入 worker | 记录真实 API 状态码；不将它当作 GPU 最早完成的时间 |
| FINISHED | worker 完成求和和 Surface 释放 | 保存不可变完成时间、状态及现有 CPU/墙钟记录 |
| DELIVER | 主线程完成票据判断 | 区分 ACCEPTED、EXPIRED、RETIRED，并记录交付等待 |
| EXPIRED / CLOSE | 主线程截止检查与资源退休 | 记录 WAITING/QUEUED/COPYING/DELIVERING 及是否已有结果 |

没有图像、声音或像素内容采集。截止、采样频率、全局单飞、退休后缓冲归还规则及 PreviewActivity 产品逻辑保持候选原样；本次只补足观察。观察日志和主 Looper/Perfetto 本身都会扰动调度，不能当作无观察的性能实验或真机结果。

SQL 同时读取 `WalkBuddy brigh*` 的 CPU 与线程状态，兼容 Linux 线程名截断；保留原有主线程、RenderThread、四次主动过期和完整性门槛。新 SQL 已用原来 API 35 第二轮的真实 trace 本地重放，原失败摘要保持，仅解析器自身运行耗时不比较；新增 worker 调度行实际存在，见[SQL 记录](brightness-stages-sql-local-2026-10-31.json)。旧轨迹没有新增的应用阶段日志，不能追补历史阶段。

## 当前验证

本地 256 项 JVM、31 项工具测试均通过，Lint 0 错误 / 21 警告，应用及原生测试包构建成功，分支专项工作流 actionlint 通过。[本地记录](brightness-stages-local-2026-10-31.json)保留范围。只有实验分支相关代码推送会触发该分支的 `Experimental brightness stages`；不会创建版本标签或发布安装包。新的云端结果仍待取得，实体设备、真实 AR、TalkBack、帧率及耗电均无新验收。
