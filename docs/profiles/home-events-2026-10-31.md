# 首页按状态变化更新

基线为 v0.4.33。原首页在 STARTING/LIVE 时每 500ms 执行刷新，权限区和引导区分别查询相机权限、小窗功能及小窗授权；预览的 render 还会逐帧或随保护检查重新拼接同一段首页报告。

v0.4.34 通过 `HomeState` 发布实际变化的文字、阶段和首次使用进度。`MainActivity` 仅在 resumed 时订阅，并监听本应用的小窗 AppOps 变化；通知合并为下次主线程刷新，每轮共用一份访问状态。暂停解除两种观察，旧授权回调以订阅代次拒绝；预览用独立所有者令牌拒绝旧实例的迟到文字和销毁。相同状态不构建新报告或通知首页。监听注册失败才沿用原来的活动相机 500ms 回退，停止/故障时不循环。

参考 [Android UI state production](https://developer.android.com/topic/architecture/ui-layer/state-production) 的状态变化与生命周期原则；小窗监听使用 [AppOpsManager.startWatchingMode](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,%20java.lang.String,%20android.app.AppOpsManager.OnOpChangedListener)) 的本 UID 模式变化通知，离开前台用 `stopWatchingMode` 解除。不新增权限或依赖。

同类参考为 Google 官方 Camera2 QR scanner [状态持有实现](https://github.com/android/camera-samples/blob/2f10e46fc7a09f7208f8d6de72482ecf3e5fb85a/samples/camera2-qrscanner/src/main/java/com/android/camera2/qrscanner/Camera2QrScannerViewModel.kt)：扫描结果写入状态，界面消费只读 StateFlow。本项目将这一状态发布方式用于已有 Java Activity 的首页报告；只发布有意义的会话变化，并单独保留相机安全检查。参考不构成对对方项目小窗、耗电或性能的比较结论。

## 验证范围

- JVM：新增七项检查覆盖 10,000 次相同状态报告对象复用/通知抑制、识别与提示切换、旧预览无法覆盖新会话、文字相同的阶段/历史变化仍通知、存在性变化、分发中退订和故障后重新启动。旧产品表面测试的逐字实现断言迁移到状态发布入口；精确文字由行为测试及真实首页检查覆盖。
- 原生模拟器：新增 `HomeEventProbe`，首页实际更改本应用 AppOps 的 ignore/allow 并等待显示，最终恢复原 raw mode；Activity 重建确认旧订阅解除、非主线程旧监听回调被拒绝；真实 Camera2 小窗中检查 1100ms 采集/显示继续而首页访问查询为零，20 次通知合并为一次快照，移除监听后验证定时回退，再注册后恢复安静。保留既有故障、权限、停止/重开、旋转、大小字、实际小窗和十项完整门槛。
- 相机保护仍每 100ms 检查，显示/采集 1200ms、分析 1500ms 等截止不变。本次不将首页刷新变化当成历史显示停顿根因的修复。

本地首轮发现旧测试硬编码原报告赋值表达式；迁移入口断言后 JVM 全部通过，第二轮原生测试编译发现遗漏 TextView 导入，已补齐。完整最终验证尚待完成，不能把这两轮称为成功构建。

本地最终完整验证成功：231 项 JVM，0 失败 / 错误 / 跳过；Lint 0 错误 / 22 警告；通用应用与原生测试包构建通过。源文件与既有预览 UI 守卫见[结构化记录](home-events-2026-10-31.json)。

当前等待 Android 15/16 候选和正式发布检查。云端结果与实际 APK 将在完成后追加；没有真实设备、真实 AR、TalkBack、帧率、耗电或 GC 收益结论。

## 首轮云端失败与采证修订

[41125243534](https://github.com/cyanrbt/walkbuddy/actions/runs/41125243534) attempt 1 失败。两套均编译成功；API 36 的十项中 `pip_recovery` 失败，其余九项通过；API 35 的 `pip_recovery` 和 `home` 失败，其余八项通过。两套 `pip` 与 `pip-repeat` 中新增的真实小窗 1100ms 零首页访问查询、连续画面、20 次通知合并及无监听回退均通过。两套摘要均无 ANR 观察，不能把失败归因于系统 ANR。

两个完整附件已下载，大小和服务端 SHA-256 匹配，摘要与原始文件重放一致。[首轮原始记录](home-events-native-initial-2026-10-31.json)包含失败文字及截图来源；[API 35 原图](home-events-initial-api35.png)、[API 36 原图](home-events-initial-api36.png)未裁切或重绘。API 36 在恢复 cycle 0、API 35 在 cycle 1 的 `Preview-only warning is not visible` 处失败。原条件仅包含系统 PiP 标志及后续 300ms 的新采集/显示，未记录断言瞬间的根视图尺寸或布局就绪；失败采集期间的窗口 dump 已是小窗尺寸，不能据此倒推断言瞬间已完成布局，也不能据现有证据排除短暂显示问题。

修订保留原真实触摸、相机截止、提示完整可见和不截断断言，增加实际根窗口双向缩小、根和文字布局就绪、缩小后新帧条件，再执行原断言；每轮记录过渡尺寸、提示可见矩形及最终尺寸。若实际小窗或提示一直无法满足条件，仍失败。修订没有改动生产 UI 或相机实现。

API 35 首页的安静期计数为 1，但首轮未记录刷新来源或最初焦点状态，不能认定为周期轮询。首页本来需要在首次获得焦点时刷新；安静期修订先等已 resumed、已聚焦、监听已注册且待处理刷新清空，初始焦点另行记录。Activity 重建后的旧回调测量也采用相同前提。回退检查改为在原 5s 观察截止内等待实际两次刷新，代替固定睡眠后推断系统调度次数；未改生产 500ms 调度或相机保护。

以上是测量前提和诊断的补充，尚待新候选完整结果，不将成功重跑或后续通过直接称作首轮瞬时提示/刷新根因已修复；首轮未发版本标签，也未重跑。
