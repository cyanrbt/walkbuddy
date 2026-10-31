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

## 参考实现与选择边界

官方 [CameraX 预览文档](https://developer.android.com/media/camera/camerax/preview)说明 SurfaceView 可以利用独立 Surface/硬件合成以降低部分 GPU 路径开销，而 TextureView 为灵活变换承担合成；实际结果受设备、覆盖层与配置影响。[ImplementationMode API](https://developer.android.com/reference/androidx/camera/view/PreviewView.ImplementationMode)还说明兼容回退条件。WalkBuddy 有常驻覆盖层、32 组方向/取样约束和实际 PiP 生命周期，不能把一般说明当成本应用迁移后的性能结论。

Google [Grafika TextureFromCameraActivity](https://github.com/google/grafika/blob/b1df331e89cffeab621f02b102d4c2c25eb6088a/app/src/main/java/com/android/grafika/TextureFromCameraActivity.java)采用独立相机/GL 渲染线程，并单独管理 Surface 与 Activity 生命周期。此处固定参考提交为 2020 年的历史图形示例，取其线程/生命周期观察思路，不把旧 Camera API 或主线程等待启动模式作为当前直接移植方案。当前先用实际阶段数据判断多余排队、API 等待和主线程交付；后续如更改渲染路径，仍需重新证明像素、几何、截止、释放与手动恢复，而非只替换控件名称。

## 首次云端观察与后备采集修订

源码 `5d68ec98840c177e71f74d1e9186d09e7bf77852` 的 [41162976895](https://github.com/cyanrbt/walkbuddy/actions/runs/41162976895) attempt 1 整体失败。两个完整 ZIP 已核对 GitHub 长度、SHA-256、CRC、来源，五条实际存在的原始轨迹由同一官方 v59.1 本地重读；缺少的文件保持缺失，不挑选样本补齐。[完整原始结果](brightness-stages-first-2026-10-31.json)保留。

API 35 三轮在首页触摸前均被 Pixel Launcher 的 ANR 弹窗阻挡，输入快照主显示器焦点和原图一致，没有亮度请求；该 API 不能作为新路径的性能结果。API 36 三轮恢复均通过，但第一条文件传输在 0% 失败，第二次尝试明确 device offline，未取得任何字节，因此整轮仍失败。后两条完整轨迹各有 11 次、共 22 次完整请求，结果均为成功及 ACCEPTED；这 22 次的调用前主线程等待最高 431ms，worker 排队最高约 47.389ms，API 调用返回后到 worker 回调最高约 472.349ms，主线程交付最高 153ms。API 调用本身最高约 1.903ms；不能把快速返回当作复制已完成，后续回调等待也不能全部归为 GPU 时间。各阶段及原始事件保留在 JSON，没有配对实验或实体性能结论。

现有数据支持下一步检查实际帧到期后多余的主线程排队，仍不足以定位早先读回停止或证明替代方案满足发布条件。当前截止、生命周期与产品路径未据此改动。

首次后备 `logcat.txt` 仍只过滤 WARN，INFO 阶段只保存在取得的 Perfetto 文件里；现在后备改为 INFO，防止传输失败后连阶段记录也丢失。主显示器输入焦点解析同时纳入 Launcher 阻挡说明，依旧保留失败。第二次同文件复制前有界等待 adb 恢复，原长度/摘要和最多两次要求保留，不重新采样。38 项工具测试覆盖焦点历史误判、其它显示器、应用自身 ANR、INFO 后备及离线恢复/超时；其实际云端恢复分支待观察，默认产品和应用源码与 `5d68ec9` 保持。
