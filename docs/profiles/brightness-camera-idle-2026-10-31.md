# 冻结 Camera2 测试的状态回调采证

实验 `03c1ec4` 在 API 26 的首次和单次复验均通过编译、新 draw 请求、32 组像素及 EGL 对照，却在 `stopRepeating` 后的 idle 等待超时。[两次原始失败](brightness-early-queue-api26-failures-2026-10-31.json)保留，不能仅凭序列完成认定相机已停止处理所有请求。

[CameraCaptureSession 官方说明](https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession)要求停止重复请求后等待 `StateCallback.onReady`。Android 8 的 [CameraDeviceImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/hardware/camera2/impl/CameraDeviceImpl.java)仅在从非 idle 状态接收设备 idle 时投递相应回调，[CameraCaptureSessionImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/hardware/camera2/impl/CameraCaptureSessionImpl.java)再将该事件转为 `onReady`。这些源码解释接口路径，不能证明前两次运行具体漏接了哪一个事件。

本次只在独立 `SurfaceProducerProbe` 增加有界数量的状态记录：配置、重复请求 ID、前四个捕获、active/ready、停止调用前后、序列完成/中止、等待结束和关闭。每条保留单调时间、是否主线程、stopping 和最后一次 ready 状态，成功或失败均写入原有 producer JSON。原五秒序列完成、五秒 idle 等待和全部像素/源时间戳门槛保持；记录器本身不满足任何 latch，也不自动中止或重新启动相机。记录有观察开销，不是无干扰性能样本。

同时同步主分支已通过云端验证的托管运行机编译后存储清理。Python 工具 43 项与三个相关工作流 actionlint 通过，应用和原生测试 APK 实际编译 25 秒成功；`git diff 03c1ec4 -- app/src/main app/build.gradle` 为空，实验产品源码及版本相同。下一次云端执行用于取得状态证据，不提前称相机测试已修好或候选可发布。主分支产品和实际下载仍为 v0.4.37；没有实体设备结果。
