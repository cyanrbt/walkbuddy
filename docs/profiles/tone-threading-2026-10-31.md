# v0.4.31 提示音线程与取消

此前 `PreviewActivity.alert()` 在主线程创建并启动 `ToneGenerator`，主线程延时回调和停止入口也直接释放它。当前改为进程内一个独立串行音频线程；声音开启后准备一个输出，后续获准请求复用，关闭/停止后由同一线程释放。推理继续使用原独立识别线程。

参考 [AOSP AsyncRingtonePlayer](https://android.googlesource.com/platform/packages/services/Telecomm/+/refs/heads/main/src/com/android/server/telecom/AsyncRingtonePlayer.java) 将音频对象限定在独立线程的方式；本项目按自己的相机/检测会话及风险时效取消，不采用来电语义。API 的有限播放、布尔返回与资源释放依据 [ToneGenerator 文档](https://developer.android.com/reference/android/media/ToneGenerator)。这些参考支持设计选择，不证明本项目设备性能改善。

## 行为与边界

- 声音会话只读现有识别 Ticket，关闭声音不会取消识别。一个活动操作、至多一个最新排队请求；没有循环播放、重试队列或空闲轮询。
- 排队请求保留获准时的结果截止：基础捕获时间加 1000ms，深度采样时间加 500ms。较低风险结果不能延长更高风险请求；播放前重新检查会话和截止，超期丢弃但有效会话可复用已经准备好的输出。
- 风险降级/消失使旧请求失效并请求停止，风险重新出现不恢复旧请求。声音关闭、预览停止、检测关闭及几何清理使本音频会话失效，清除错误回调引用；旧会话的取消/错误不会改变新会话。
- 初始化异常或系统拒绝 `startTone` 后只关闭声音开关、显示一次提示，用户再次开启才能重试；画面、检测与振动独立。
- 原生调用均在控制锁外。取消入口不等待 native 返回；若系统调用已经开始，不能强制中断，返回后才能停止/释放。这不构成“物理声音瞬时停止”的保证。提示仍为通知音量流、40 音量参数、150ms 有限蜂鸣，没有端到端音频延迟或电量数据。
- 本版没有修改生产相机/显示/分析截止，不宣称已定位或修复 [v0.4.28 显示帧停顿](pip-display-expiry-2026-10-31.md)。

## 验证方法与当前结果

12 项新 JVM 测试使用可控时钟和阻塞输出，验证复用、关闭、初始化中取消、超期不补播、降级/消失、只保留最新请求、旧会话隔离、失败只报一次及关闭线程等待释放；所有记录的音频操作均在同一非调用线程。另 2 项核对确认前不产生音频截止、捕获时间/500ms/1000ms 边界、不同风险来源、未来时间和 clear。最终 0.4.31 / 43 完整 212 项 JVM 为 0 失败 / 错误 / 跳过；Lint 0 错误 / 22 警告，应用与原生测试包构建通过，发布元数据、产品表面及差异格式检查通过。

原生 `ToneProbe` 加入既有两轮完整 PiP 流程，原十项必过检查保持。它在真实虚拟 Camera2/识别活动期间阻塞替换输出的初始化，要求采集/显示时间继续推进、主线程能关闭声音、返回后零次晚播且资源关闭一次；受控音频失败后检查声音关、同相机/识别 Ticket/振动保持，再分开记录实际 `AndroidTone` 的创建/两次有限播放/关闭线程与 API 接受结果。系统音频不可用时检查关闭声音的回退，不冒称成功播放；原始 `tone-threading.txt` 只含线程和操作时间，没有相机图像或身份信息。候选 [41109403901](https://github.com/cyanrbt/walkbuddy/actions/runs/41109403901) attempt 1 已成功：API 35 job `124247612204`、API 36 job `124247611895` 首次十项全部通过。每套两轮新增三项音频检查均通过，实际 AndroidTone 每轮创建/关闭各一次、两次播放均被 API 接受，所有记录操作在 `WalkBuddy sound`。两套完整 ZIP 已下载、按服务端大小/SHA-256 核对并重放十项摘要与原始 PASS，[候选 JSON](tone-threading-native-candidate-2026-10-31.json)保存实际线程时间；两套摘要无 ANR 观察。此处时间为模拟器 API 调用时间，不是可听延迟。

[重新执行的时序对照](feedback-cooldown-v0.4.31-2026-10-31.json)保存当前五个核心文件摘要。baseline 与 fixed 各 147 行及首次获准时间均与历史记录逐项相同，只有新增截止查询的 DetectionFeedback 源码摘要变化。比较时按行为行与摘要字段核对，历史 JSON 额外的来源注释不参与整对象相等判断；历史对照记录保留。

实体设备可听效果、路由切换、勿扰/音量策略、蓝牙、主线程帧耗时、功耗、TalkBack 和真实 AR 均未验收。模拟器 API 接受不等于真实听见，也不能据线程迁移推算历史帧停顿原因。

## 发布状态

[正式发布 41110471304](https://github.com/cyanrbt/walkbuddy/actions/runs/41110471304) attempt 1 已终态成功：Android 15 job `124251260617`、Android 16 job `124251260100` 与 APK job `124254126316` 均首次成功。标签固定于 `9f46371b9281eb882413e813c9b932e3d27f7cff`，生产/测试代码与候选一致。两套标签原始日志各十项 PASS，每套两轮新增音频三项通过，真实 AndroidTone 各创建/关闭一次、两次播放请求均被接受、全部操作在后台线程。两套完整 ZIP 已下载核对服务端大小/SHA-256，重放摘要与原始记录一致，两套摘要无 ANR 观察，[发布原始 JSON](tone-threading-native-release-2026-10-31.json)保留线程时间。

Release `429386749` 的 asset `693103519` 实际存在 [v0.4.31 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.31/Walk-Buddy-v0.4.31-arm64-debug.apk)。通过 Release 下载地址完整下载，curl 退出 0；9,975,340 字节，SHA-256 `22a629c94a18f0b016bde7024111dd7fd9bc52310476655787da2d0bfc8b59a9` 与 GitHub 一致。apksigner verify 通过、单签名且 v2 有效，固定证书 `ffdf47c306d335d2ee3bc39256d43c470fda5572e80662155ed31dc1c16aae0d`。包名、0.4.31 / 43、min 26 / target 36、仅 ARM64/原两项权限、模型/中文表/11sp 均通过。主包存在新音频线程、输出与错误提示，预览 Activity 不直接引用 ToneGenerator；原生音频探针和记录不在主包，五个提醒核心文件与重新执行的时序对照摘要一致。[APK 核验 JSON](apk-v0.4.31.json)保留结果。

状态接口曾在原始日志结束、附件上传及 Release 已有 APK 后继续返回旧的 queued / in_progress，公开 API 也曾返回旧值；最终明确取得 completed / success 与三个作业终态成功后才记录流程成功。没有重跑，具体状态滞后原因未定位。这与音频功能或历史显示停顿根因分别处理。
