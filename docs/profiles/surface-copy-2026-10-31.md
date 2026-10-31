# Surface 像素复制原型

## 目标与当前状态

上一轮[实际系统轨迹](preview-system-trace-2026-10-31.md)中，单个亮度读回消息墙钟约 294.639ms、主线程 CPU 约 0.226ms，且与 RenderThread 的绘制、提交和 `copyLayerInto` 重叠。本轮先验证可替代同步 `TextureView.getBitmap` 的像素来源，尚未修改应用读回链路，也不称历史 1246ms 停顿已修复。

官方 [PixelCopy 接口](https://developer.android.com/reference/android/view/PixelCopy)说明 Surface 路径读取最近排队缓冲并缩放到目标位图。[Android 8 实现](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/graphics/java/android/view/PixelCopy.java)实际同步执行复制后再投递回调，所以不能仅因回调接口就从主线程发起；[Android 15 Readback](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/libs/hwui/Readback.cpp)还会处理源缓冲变换、裁剪和颜色空间，这些与现有 TextureView 路径是否逐像素一致必须实测。

## 原型验证

- 专用 `Surface copy experiment` 流程覆盖 API 26、35、36。原有普通及发布流程仍仅覆盖 Android 15/16；输入未启用时不执行替代路径。
- 复用原有 32 组 TextureView，四种传感器方向、四种显示方向及两个尺寸。生产者每组只提交一次合成画面，保证两次参考和替代复制比较同一静止缓冲。
- 测试线程通过独立 Surface 包装调用 PixelCopy，主线程只接收回调。目标仍为 16×12 ARGB_8888，比较全部 6,144 个像素、RGB 总和和 SHA-256，并检查前后旧参考未改变。任何像素差异或复制状态失败都拒绝该候选；不容许近似阈值或只比较平均亮度。
- 保存所有组的首次结果、复制状态、不同像素及首个差异，不靠重试挑选样本。回调超时则等待回调释放正在被写入的位图，避免提前回收。
- API 26 只声称该冻结合成像素试验，不冒充完整 Camera2/PiP 测试。API 35/36 完成试验后继续原有十项检查，即使候选失败也保留其它检查记录。

原生应用及测试 APK 本地构建成功，31 项工具测试、工作流 actionlint 与 shell 语法检查通过。新增观察类只在测试 APK；生产源码、版本 0.4.37 / 49 与固定发布流程保持。

## 首轮合成结果

源码 `c74a4f277b3a5d96fd3cbf43c2cb2f2936cf17e5` 的[专项 41156695455](https://github.com/cyanrbt/walkbuddy/actions/runs/41156695455)和[常规检查 41156695394](https://github.com/cyanrbt/walkbuddy/actions/runs/41156695394)均 attempt 1 终态成功。五个原始附件完整下载，字节数、GitHub SHA-256、ZIP CRC 和来源提交均核对；原始报告重放一致。API 26 的报告明确只含合成像素专项，API 35/36 的两个流程各十项完整通过。

三个 API 各 32 组、共 18,432 像素均满足：PixelCopy 成功、调用线程不是主线程、前后参考未变、ARGB 摘要相同、不同像素为零、RGB 总和相同。[原始逐组结果和附件来源](surface-copy-native-initial-2026-10-31.json)保留。没有重跑、容差或样本筛选；这只是冻结 Canvas 生产者的像素一致性，不能推断实际相机/EGL、产品响应时间或历史停顿修复。

## Camera2 / EGL 对照待验收

下一轮测试在独立的 TextureView 夹具中打开虚拟 Camera2，取得至少三次真实捕获结果，停止 repeating request 并等待序列完成/会话 idle 后冻结缓冲；不会移除或延长 PreviewActivity 的生产截止，因为夹具不运行产品预览状态机。另一夹具使用 EGL/GLES2 提交单帧，包含不透明、透明度不同和低亮度图块，用于检查增强模式采用的输出来源。

每个生产者分别在两种视口大小比较全部 192 像素，同时核对 TextureView 时间戳未变；共四组、768 个额外像素。Camera2 先关闭会话和设备再销毁消费端；EGL 在所属线程释放后再关闭 Surface。API 26/35/36 的专项均执行这些对照；普通/发布流程的默认 API 范围与十项检查不变。该修订已本地编译，实际云端结果待取得，不等同于真实 AR 或产品取消/恢复验收。

即使生产者试验通过，仍需异步请求的单请求上限、过期/取消/停止/恢复及低亮度边界验证，才能接入产品。请求与回调计时为受控观察，不是帧率、耗电、真实 AR 或实体设备结果。

## Camera2 / EGL 云端结果

源码 `3c2f0d6627f7614a4f50f249aa5d25ddbe2dc054` 的[专项 41158068308](https://github.com/cyanrbt/walkbuddy/actions/runs/41158068308)和[常规 41158068207](https://github.com/cyanrbt/walkbuddy/actions/runs/41158068207)均首次终态成功。五份完整 ZIP 的 GitHub 字节数、SHA-256、ZIP CRC、来源提交均已核对；原始报告重放一致，API 35/36 各流程十项完整通过，API 26 仍只报告专项。

三个 API 各 32 组合成对照及虚拟 Camera2/EGL 各两组全部通过；ARGB、RGB 总和、前后参考与生产者时间戳严格一致，调用不在主线程。Camera2 至少三次 metadata 后停止 repeating，EGL 单帧冻结。[逐组结果与完整来源](surface-copy-native-producers-2026-10-31.json)保留；本轮仍是独立夹具原型，没有替代产品生命周期或真实 AR/设备验收。
