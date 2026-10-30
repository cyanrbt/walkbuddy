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

布局另对原 30 组窗口/字号/日夜配置检查手动关闭后的完整说明，既有 60 组故障/小窗提示呈现保留。原完整模型、相机、锁屏和两轮 PiP 检查继续执行，实际结果见下节。

本地 `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest -PuniversalApk` 成功，146 项 JVM 测试无失败，Lint 0 错误 / 22 警告；产品表面、v0.4.20 发布元数据和八项 CI 摘要测试通过。首轮编译发现探针误用不存在的 Activity PiP 参数 getter，已改为检查成功提交的参数，不使用平台隐藏接口；最终应用与测试 APK 均已编译。

## 原生结果

源码 `b7628a0f612764b91ed07d5941deb06682205d13` 的 [41036811932](https://github.com/cyanrbt/walkbuddy/actions/runs/41036811932) attempt 2 最终成功。API 36 job `123999553366` 首次通过；API 35 首轮在最初的锁屏恢复输入处失败，[原始证据和原因边界](input-after-wake-2026-10-30-v0.4.20.md)保留，仅重跑原提交失败作业后 API 35 job `124001689872` 成功。没有改变注入方式、放宽截止或重试点击。

两套附件均已实际下载并校验摘要，每套九项探针齐全，两轮 PiP 各包含一次新增控制用例，30 组手动关闭布局、60 组既有故障提示呈现与原模型回归通过。两套摘要均无 ANR 观察；后续通过不能证明首轮输入根因消失。已查看 [API 36 小窗与展开原图](../design/v0.4.20/README.md)。

| API | artifact / 字节 | ZIP SHA-256 | 原始摘要 |
| --- | --- | --- | --- |
| 35（重跑） | 12968891150 / 7,788,863 | `962172fa003f142fcc9181b446681a875541de77500eb429746e84b040fc5e0a` | [api35.json](pip-detection-control-2026-10-30-api35.json) |
| 36（首次） | 12968786754 / 7,759,048 | `5c4bf126022d203cca260e66c4c0cc3b13a72c374bbbee2498334d5a2f566450` | [api36.json](pip-detection-control-2026-10-30-api36.json) |

## 发布附件

新标签 `v0.4.20` 固定指向 `07564302f843216e343d7bf0b70442b3abdc6f16`，与原生候选的 app 文件相同。[发布流程 41038083125](https://github.com/cyanrbt/walkbuddy/actions/runs/41038083125) attempt 1 成功：API 35 job `124003799755`、API 36 job `124003799471`、APK job `124005917650` 均首次通过；这不改变前述候选首轮失败的原因边界。

已实际下载 [发布 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.20/Walk-Buddy-v0.4.20-arm64-debug.apk)，独立核对摘要、固定签名、版本 0.4.20 / 32、ARM64、权限、模型、中文标签与菜单资源，见[附件核验](apk-v0.4.20.json)。原生截图与软件核验不替代实体设备菜单、TalkBack 或 AR 深度验收。
