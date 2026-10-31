# 冻结 Camera2 测试的状态回调采证

实验 `03c1ec4` 在 API 26 的首次和单次复验均通过编译、新 draw 请求、32 组像素及 EGL 对照，却在 `stopRepeating` 后的 idle 等待超时。[两次原始失败](brightness-early-queue-api26-failures-2026-10-31.json)保留，不能仅凭序列完成认定相机已停止处理所有请求。

[CameraCaptureSession 官方说明](https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession)要求停止重复请求后等待 `StateCallback.onReady`。Android 8 的 [CameraDeviceImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/hardware/camera2/impl/CameraDeviceImpl.java)仅在从非 idle 状态接收设备 idle 时投递相应回调，[CameraCaptureSessionImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/hardware/camera2/impl/CameraCaptureSessionImpl.java)再将该事件转为 `onReady`。这些源码解释接口路径，不能证明前两次运行具体漏接了哪一个事件。

本次只在独立 `SurfaceProducerProbe` 增加有界数量的状态记录：配置、重复请求 ID、前四个捕获、active/ready、停止调用前后、序列完成/中止、等待结束和关闭。每条保留单调时间、是否主线程、stopping 和最后一次 ready 状态，成功或失败均写入原有 producer JSON。原五秒序列完成、五秒 idle 等待和全部像素/源时间戳门槛保持；记录器本身不满足任何 latch，也不自动中止或重新启动相机。记录有观察开销，不是无干扰性能样本。

同时同步主分支已通过云端验证的托管运行机编译后存储清理。Python 工具 43 项与三个相关工作流 actionlint 通过，应用和原生测试 APK 实际编译 25 秒成功；`git diff 03c1ec4 -- app/src/main app/build.gradle` 为空，实验产品源码及版本相同。下一次云端执行用于取得状态证据，不提前称相机测试已修好或候选可发布。主分支产品和实际下载仍为 v0.4.37；没有实体设备结果。

## 实际首轮状态顺序与初始化修订

采证源码 `e7ef51c` 的[像素专项 41168543511](https://github.com/cyanrbt/walkbuddy/actions/runs/41168543511) API 26 仍在原 idle 门槛失败，没有重跑。[完整回调记录](brightness-camera-idle-first-2026-10-31.json)来自实际 ZIP，130,004 字节，SHA-256 `c2e8917b471c1de46532e92bace0fe83b9bc7e1d48fa096b98b8a0ced7148d45`，CRC 通过。

原始顺序为 `CONFIGURED → REPEATING_SUBMITTED → READY → ACTIVE → READY → 三帧 CAPTURE → STOP_REPEATING → SEQUENCE_COMPLETED`，随后五秒内没有新 ready。第二个 ready 发生在第一帧完成前约 223ms，三帧完成都晚于它，因而不能以最后 ready 为 true 接受“已冻结”。它与初始化和请求启动交错一致，但这些用户层回调不能独立证明 CameraDevice 内部状态写入顺序，也不将此轮数据倒推为前两轮的确定根因。

[官方状态回调说明](https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession.StateCallback)指出，没有提交采集请求的新会话会在 configured 后发出初始 ready。因此修订为 `onConfigured` 只保存会话，测试后台最多五秒等待初始 ready，再到主线程提交重复请求，并记录 `START_AFTER_INITIAL_READY`。这是新增的初始化前提；三帧要求、停止后五秒序列完成与五秒新 ready、像素/源时间戳对照全部保留，没有延长产品的亮度或画面截止，也没有用旧 ready 满足停止门槛。应用和原生测试 APK 实际编译 25 秒通过，diff 检查通过；产品源码、版本与工作流相对 `e7ef51c` 均无变化。云端结果待取得。
