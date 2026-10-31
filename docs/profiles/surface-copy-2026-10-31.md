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

原生应用及测试 APK 本地构建成功，31 项工具测试、工作流 actionlint 与 shell 语法检查通过。新增观察类只在测试 APK；生产源码、版本 0.4.37 / 49 与固定发布流程保持。云端实际像素结果待取得。

即使合成试验通过，仍需真实 Camera2 同帧比较、异步请求的单请求上限、过期/取消/停止/恢复及低亮度边界验证，才能接入产品。请求与回调计时为受控观察，不是帧率、耗电、真实 AR 或实体设备结果。
