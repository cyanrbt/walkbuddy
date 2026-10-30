# 2026-10-30 小窗内关闭检测

v0.4.19 的系统小窗菜单只有「停止整个预览」，关闭检测需展开再操作。参考 [Lookout 的直接相机开关](https://support.google.com/accessibility/android/answer/9031274?hl=zh-Hans)所体现的可直接停止原则，结合 [Android PiP 的菜单操作接口](https://developer.android.com/develop/ui/views/picture-in-picture#add_controls)，本轮为已开启检测的小窗提供「关闭检测」，保留画面；菜单中「停止」仍结束整个相机。

## 行为

- 仅 LIVE 且检测开启时增加关闭检测操作；相机停止操作始终排在第一位。系统只允许一个自定义操作时，优先保留结束相机。
- 使用现有非导出 receiver，显式 action 区分两个停止操作。关闭检测使用不可变 PendingIntent，绑定随机 Activity owner 和 generation；每会话的 Intent data 不同，避免更新新会话参数时改写旧动作。
- 只有相同 owner/generation、仍在 PiP 且检测开启时接受关闭检测。没有活动页面、已结束或退休会话、完整页面、未知 action 与重复关闭均不改变会话。
- 复用现有检测取消：清空 Ticket、检测/声音勾选、振动/提示音、图层与待朗读提醒。native 正在使用的帧仍由唯一 worker 持有直到返回，不在主线程提前回收。
- 实时画面保留，持续显示「未检测 / 仅预览」；展开后仍关闭，用户需主动勾选，声音不恢复。相机/显示已过期或锁屏时继续完整停止。分析 1500ms、相机 1200ms、输入/结果 1000ms、启动 5000ms 截止均保持。
- PiP 参数只在菜单状态变化与进入回调时更新，不在每帧向系统重复提交；更新操作列表时不覆盖既有比例、来源矩形等参数。

## 验证范围

原生探针从 Activity 已成功提交给系统的 PictureInPictureParams 取得 RemoteAction，核对标签、说明和操作顺序，发送真实 PendingIntent 并等待广播完成；检查检测/声音/反馈与 Ticket 取消、相机/纹理身份保留、心跳继续推进、提交的菜单删除关闭检测、重复动作、退休 owner/generation、完整页面重放及手动重开模型。Activity 没有公开的 PiP 参数查询方法，因此这不是从系统重新读取菜单；也不模拟点击系统菜单图标，不能替代 OEM 菜单与 TalkBack 实机验收。

布局另对原 30 组窗口/字号/日夜配置检查手动关闭后的完整说明，既有 60 组故障/小窗提示呈现保留。原完整模型、相机、锁屏和两轮 PiP 检查继续执行。结果待实际运行后补记。

本地 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -PuniversalApk` 成功，146 项 JVM 测试无失败，Lint 0 错误 / 22 警告；产品表面、v0.4.20 发布元数据和八项 CI 摘要测试通过。首轮编译发现探针误用不存在的 Activity PiP 参数 getter，已改为检查成功提交的参数，不使用平台隐藏接口；最终应用与测试 APK 均已编译。
