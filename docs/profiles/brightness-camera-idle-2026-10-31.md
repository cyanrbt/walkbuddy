# 冻结 Camera2 测试的状态回调采证

实验 `03c1ec4` 在 API 26 的首次和单次复验均通过编译、新 draw 请求、32 组像素及 EGL 对照，却在 `stopRepeating` 后的 idle 等待超时。[两次原始失败](brightness-early-queue-api26-failures-2026-10-31.json)保留，不能仅凭序列完成认定相机已停止处理所有请求。

[CameraCaptureSession 官方说明](https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession)要求停止重复请求后等待 `StateCallback.onReady`。Android 8 的 [CameraDeviceImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/hardware/camera2/impl/CameraDeviceImpl.java)仅在从非 idle 状态接收设备 idle 时投递相应回调，[CameraCaptureSessionImpl](https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/core/java/android/hardware/camera2/impl/CameraCaptureSessionImpl.java)再将该事件转为 `onReady`。这些源码解释接口路径，不能证明前两次运行具体漏接了哪一个事件。

本次只在独立 `SurfaceProducerProbe` 增加有界数量的状态记录：配置、重复请求 ID、前四个捕获、active/ready、停止调用前后、序列完成/中止、等待结束和关闭。每条保留单调时间、是否主线程、stopping 和最后一次 ready 状态，成功或失败均写入原有 producer JSON。原五秒序列完成、五秒 idle 等待和全部像素/源时间戳门槛保持；记录器本身不满足任何 latch，也不自动中止或重新启动相机。记录有观察开销，不是无干扰性能样本。

同时同步主分支已通过云端验证的托管运行机编译后存储清理。Python 工具 43 项与三个相关工作流 actionlint 通过，应用和原生测试 APK 实际编译 25 秒成功；`git diff 03c1ec4 -- app/src/main app/build.gradle` 为空，实验产品源码及版本相同。下一次云端执行用于取得状态证据，不提前称相机测试已修好或候选可发布。主分支产品和实际下载仍为 v0.4.37；没有实体设备结果。

## 实际首轮状态顺序与初始化修订

采证源码 `e7ef51c` 的[像素专项 41168543511](https://github.com/cyanrbt/walkbuddy/actions/runs/41168543511) API 26 仍在原 idle 门槛失败，没有重跑。[完整回调记录](brightness-camera-idle-first-2026-10-31.json)来自实际 ZIP，130,004 字节，SHA-256 `c2e8917b471c1de46532e92bace0fe83b9bc7e1d48fa096b98b8a0ced7148d45`，CRC 通过。

原始顺序为 `CONFIGURED → REPEATING_SUBMITTED → READY → ACTIVE → READY → 三帧 CAPTURE → STOP_REPEATING → SEQUENCE_COMPLETED`，随后五秒内没有新 ready。第二个 ready 发生在第一帧完成前约 223ms，三帧完成都晚于它，因而不能以最后 ready 为 true 接受“已冻结”。它与初始化和请求启动交错一致，但这些用户层回调不能独立证明 CameraDevice 内部状态写入顺序，也不将此轮数据倒推为前两轮的确定根因。

[官方状态回调说明](https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession.StateCallback)指出，没有提交采集请求的新会话会在 configured 后发出初始 ready。因此修订为 `onConfigured` 只保存会话，测试后台最多五秒等待初始 ready，再到主线程提交重复请求，并记录 `START_AFTER_INITIAL_READY`。这是新增的初始化前提；三帧要求、停止后五秒序列完成与五秒新 ready、像素/源时间戳对照全部保留，没有延长产品的亮度或画面截止，也没有用旧 ready 满足停止门槛。应用和原生测试 APK 实际编译 25 秒通过，diff 检查通过；产品源码、版本与工作流相对 `e7ef51c` 均无变化。云端结果待取得。

## 初始化修订的实际结果与后续产品失败

初始化修订 `ee5b7e8` 的[41169056590](https://github.com/cyanrbt/walkbuddy/actions/runs/41169056590)中，API 26/35/36 的冻结 Camera2/EGL 子检查全部通过，三份原始 ZIP 均匹配服务端长度、SHA-256 与 CRC。每套都有初始化 ready 在开始前到达、明确停止后又收到新的 ready、序列和 idle 等待通过的实际事件；全部 12 组生产者对照和 96 组冻结像素对照符合原门槛。[完整记录](brightness-initial-ready-native-first-2026-10-31.json)保留。

API 26、35 整轮首次成功；API 36 后续十项中 `pip` 首次失败，`pip-repeat` 通过，不能替代首轮。原 `pip` 在 `checkPipSourceBounds` 的 step 0 等待超时；该步骤尚未主动旋转，因此不能归因于旋转。实际停止提示为读回失败，日志 pid 5047 的采集帧龄 1033ms、显示帧龄 1004ms；原图显示应用停止页。对应 ZIP `13030178748` 为 14,033,001 字节 / SHA-256 `44c268d2b36d56ec8803426f9121092ade0e76ffa20cc6907b813c737e13dff2`，原始失败保留，没有重跑来替代产品失败。

另一次 `e7ef51c` 的[阶段采证 41168543464](https://github.com/cyanrbt/walkbuddy/actions/runs/41168543464)中，API 35 三条轨迹和十项通过；API 36 三次恢复及十项通过，但第一条轨迹有 `android_log_num_failed=1`，完整性失败。原始 50,673,154 字节文件及摘要 `5f2d31d55bc4e7e8db377665b169108a96b70e33ac64594c1ac8a606f8d5e913` 均匹配，六条原始 SQL 全部重放一致；原记录保持不完整。[Perfetto 官方定义](https://perfetto.dev/docs/reference/trace-packet-proto)将该计数定义为日志解析失败，不能确定被丢弃的是哪条消息。只对这个失败作业请求一次复验，结果仍待取得，应用失败轮未重跑。

[首轮五份附件及六条原始轨迹](brightness-early-queue-native-first-2026-10-31.json)保存全部结果。只有五条完整轨迹进入请求阶段统计：API 35 的 32 次和 API 36 的 24 次请求均完整且 ACCEPTED；到请求入口的等待中位数均为 0ms，最大分别 14ms、10ms，到实际排队最大分别 14ms、35ms。复制与主线程交付仍存在等待：到交付最大 374ms、696ms。不同运行机、阶段与观察开销不构成配对 A/B 或实体性能结论。

## 发现的原公开版语义差异

对照 [v0.4.37 的 PreviewActivity](https://github.com/cyanrbt/walkbuddy/blob/v0.4.37/app/src/main/java/com/cyanrbt/walkbuddy/PreviewActivity.java)，1000ms 是 `queueReadback` 前后检查中跳过旧观测的条件，`checkHealth` 并不因此停止；采集/显示以 1200ms 为截止。未发布的 `BrightnessFlight` 则新增最早到期起 1000ms 的全请求停止条件。因此此前文档将两者概括为“原截止不变”不准确；本次 1033/1004ms 停止不能归作旧公开版的画面已过期。具体复制/交付阶段没有在这次 `pip` 失败时落盘，后续采证需保留原首因报告。候选继续未发布；后续请求期限策略修订须另行记录、验证，不据本轮部分通过发布 v0.4.38。
