# 更早提交亮度请求候选

该实现仍在 `perf/brightness-async-a110908` 实验分支，未发布；主分支的产品仍为 v0.4.37。此前两个完整 Android 16 轨迹中 22 个请求的提交前等待最高 431ms，见[阶段原始记录](brightness-stages-first-2026-10-31.json)。这些是带观察开销的模拟器样本，不是实体性能或配对 A/B 结果。

## 改动

在实际 TextureView 更新回调中记录原帧时间后，主线程立即创建 Surface 包装并投递唯一的 worker 请求，随后更新界面。PixelCopy 调用、像素复制完成后的读取和求和仍在 worker；默认「检测关」时不再为了亮度额外投递主线程读回任务。检测开启后的模型 getBitmap 继续在绘制结束后的主线程任务执行，保留原帧时间和采样规则。

包装/投递后再次使用当前时钟检查健康状态，避免一次启动或分配等待后仍用旧时间呈现 LIVE；该检查不刷新原观测帧时间。采集/显示 1200ms、亮度最早到期帧起 1000ms、每秒亮度间隔、全进程单飞、原暗画面阈值、错误停止和手动恢复规则保持。PixelCopy 仍与共享 RenderThread 发生工作，不能据去掉一个主线程任务声称整体等待消失。

TextureView 监听器同时绑定实际 View 所有者。旧 View 的可用、尺寸、更新或销毁回调不会刷新新观察或停止新相机；当前 View 的真实销毁继续停止预览。原生 PiP 测试在实际退休旧 View、手动重新观察后再投递旧更新/销毁回调，检查帧时间、相机及亮度所有者均未改变。

## 框架依据与验证范围

[AOSP Android 15 TextureView](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/view/TextureView.java)在更新层后调用更新监听器；[Surface 构造](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/java/android/view/Surface.java)及[对应 JNI](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-15.0.0_r1/core/jni/android_view_Surface.cpp)包装图形缓冲生产者。这支持将投递与 TextureView.getBitmap 分开；并不是任何绘制内像素读取都安全的承诺。

新增独立原生夹具：先提交并绘制一个白色缓冲，再在真实更新回调中提交第二个四象限缓冲的亮度请求。验证 PixelCopy 确实在 worker 调用、结果在主线程交付、全部 192 个 ARGB 与回调外的旧参考完全相同，RGB 总和为 73440，避免把暖场/空白帧当作新帧。此夹具使用一个暖场帧，只验证实际绘制回调内投递，不称为冷启动性能验收。API 26/35/36 像素专项均运行；原有 Android 15/16 十项及三次系统轨迹恢复也保留。

实际相机探针继续验收复用、受控异常释放、冻结时序和手动重开，并检查默认仅预览未生成模型读回任务。旧 `ReadbackTiming` 的「未取得」在该路径表示没有执行模型任务，不把它写成耗时为零。

本地 JVM、Lint、应用及原生测试包结果见[记录](brightness-early-queue-local-2026-10-31.json)。当前候选云端仍待验证；无实体设备、真实 AR、TalkBack、音频可听性、帧率、温升或耗电的新结果。后续发布仍需正式标签与实际固定签名 APK 独立核验。
