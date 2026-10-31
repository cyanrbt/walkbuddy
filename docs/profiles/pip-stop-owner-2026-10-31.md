# 小窗停止命令归属

## 发现与参考

当前声音和关闭检测动作已携带归属信息，但 `StopPreviewReceiver` 对 `STOP_PREVIEW` 直接调用 `stopActivePreview()`，操作的是发送时的全局当前 Activity。旧停止 PendingIntent 没有 data 或 owner；从源码可推导，旧命令若在主动重开或新 Activity 启动后送达，会停止后来的观察。本轮先修正这个确定的路由缺口，不把它解释为先前的声音静音超时，也不声称已捕获用户设备上的同类自然故障。

参考 Android 开源的 [ApiDemos 画中画示例](https://android.googlesource.com/platform/development/+/master/samples/ApiDemos/src/com/example/android/apis/app/PictureInPicture.java)：自定义关闭由当前 Activity 的接收器调用自身 finish，停止时解除接收器。本项目据此采用“动作归属于发出它的观察”的约束，并额外覆盖同一 Activity 中的主动重开和多次小窗访问。参考时该文件 blob 为 `c1568e791ef3f61b18b0cb885098f153a3572890`。

[PendingIntent 官方说明](https://developer.android.com/reference/android/app/PendingIntent)指出，仅 extras 不参与操作身份匹配，UPDATE_CURRENT 会更新既有操作的 extras，IMMUTABLE 也不禁止创建方更新。因此本轮同时用 data URI 区分身份，避免旧对象通过 extras 更新变成新观察的有效按钮。

## 行为与实现

每次主动开启被接受后更新观察 owner；小窗尺寸、方向、声音选择、检测开关及进入/返回小窗不重新生成 owner。沿用当前 PiP 访问令牌，停止 PendingIntent 的 URI 和 extras 同时携带 owner 与 visit。接收器只允许当前观察、当前小窗访问、仍处于 PiP 的命令停止预览。

| 场景 | 结果 |
| --- | --- |
| 展开后送达旧小窗停止，或在完整页送达当前身份的停止 | 忽略，完整预览继续 |
| 同一次观察再次进入小窗后送达先前访问的停止 | 忽略，当前取景继续 |
| 主动重开后或创建新 Activity 后送达旧停止 | 忽略，新的相机与模型不受影响 |
| 无 owner/visit 的旧式停止命令 | 忽略 |
| 当前小窗停止，包括画面故障后的当前观察 | 释放资源并关闭预览 |

停止不依赖 LIVE、帧新鲜度或检测结果，也不使用会在故障时变化的 frame generation：故障后仍应能关闭当前小窗。首页和完整预览页的直接停止继续作用于用户当前看到的预览。权限、默认检测/声音选择、固定签名与模型保持。

## 验证安排

新增原生八场景，使用实际 Camera2、小窗进入/展开、真实 RemoteAction PendingIntent 发送与完成回调，记录实际送达的 owner、visit 和 data；旧动作送达后检查原相机、TextureView、亮度读回、模型 ticket 与 generation 保持，采集/显示继续前进。另在主线程临时仅改变 owner、用生产 stopAction 构造旧身份动作、立即恢复字段；要求 visit 相同而 PendingIntent 身份不同，并实际发送验证拒绝，以隔离 owner 校验及 data 身份。当前停止仍验证关闭 Activity；额外主动制造故障，再发送故障前当前菜单的停止，要求仍能关闭。每次运行独立保存 JSON；这里的 RemoteAction 发送不冒充用户在系统菜单上点击停止按钮。

本地 273 项 JVM、Lint、双 APK 编译通过（46 秒），补充 owner 独立校验后原生 APK 与 Lint 再次通过（31 秒），四个相关工作流 actionlint 通过。[源码和结果摘要](pip-stop-owner-local-2026-10-31.json)保留。八场景的实际 Android 运行待云端取得。候选为 v0.4.41 / 53，当前正式下载仍为 v0.4.40；正式标签与实际 APK 需要在候选通过后独立确认。没有实体设备、TalkBack、真实 AR、声音、帧率或功耗验收。既有小窗第二次静音超时与自然画面过期仍保持未定位，不据本修复宣称根因解决。
