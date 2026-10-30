# v0.4.20 小窗主动关闭检测

原图来自源码 `b7628a0f612764b91ed07d5941deb06682205d13` 的 [Android 16 原生检查 41036811932](https://github.com/cyanrbt/walkbuddy/actions/runs/41036811932/attempts/1)，API 36 job `123999553366` 首次成功。已实际下载 artifact `12968786754`，7,759,048 字节，ZIP SHA-256 `5c4bf126022d203cca260e66c4c0cc3b13a72c374bbbee2498334d5a2f566450`；两轮 PiP 的新增控制用例原始记录均通过。

- [小窗关闭检测后](pip-detection-off-api36.png)：实际系统 PiP、虚拟 Camera2，图像心跳仍推进；小窗持续显示“未检测 / 仅预览”，首页保留顶部停止与返回入口。
- [展开后](preview-after-detection-off-api36.png)：检测/声音均关闭，完整说明可见，重新开启需主动勾选。

探针发送实际提交给系统的 RemoteAction PendingIntent，并未实际点按系统菜单图标。图片经过人工查看，未编辑；不作为 OEM 菜单、TalkBack、真实物体识别或 AR 深度的实机验收。
