# 首页按状态变化更新

基线为 v0.4.33。原首页在 STARTING/LIVE 时每 500ms 执行刷新，权限区和引导区分别查询相机权限、小窗功能及小窗授权；预览的 render 还会逐帧或随保护检查重新拼接同一段首页报告。

v0.4.34 通过 `HomeState` 发布实际变化的文字、阶段和首次使用进度。`MainActivity` 仅在 resumed 时订阅，并监听本应用的小窗 AppOps 变化；通知合并为下次主线程刷新，每轮共用一份访问状态。暂停解除两种观察，旧授权回调以订阅代次拒绝；预览用独立所有者令牌拒绝旧实例的迟到文字和销毁。相同状态不构建新报告或通知首页。监听注册失败才沿用原来的活动相机 500ms 回退，停止/故障时不循环。

参考 [Android UI state production](https://developer.android.com/topic/architecture/ui-layer/state-production) 的状态变化与生命周期原则；小窗监听使用 [AppOpsManager.startWatchingMode](https://developer.android.com/reference/android/app/AppOpsManager#startWatchingMode(java.lang.String,%20java.lang.String,%20android.app.AppOpsManager.OnOpChangedListener)) 的本 UID 模式变化通知，离开前台用 `stopWatchingMode` 解除。不新增权限或依赖。

## 验证范围

- JVM：新增七项检查覆盖 10,000 次相同状态报告对象复用/通知抑制、识别与提示切换、旧预览无法覆盖新会话、文字相同的阶段/历史变化仍通知、存在性变化、分发中退订和故障后重新启动。旧产品表面测试的逐字实现断言迁移到状态发布入口；精确文字由行为测试及真实首页检查覆盖。
- 原生模拟器：新增 `HomeEventProbe`，首页实际更改本应用 AppOps 的 ignore/allow 并等待显示，最终恢复原 raw mode；Activity 重建确认旧订阅解除、非主线程旧监听回调被拒绝；真实 Camera2 小窗中检查 1100ms 采集/显示继续而首页访问查询为零，20 次通知合并为一次快照，移除监听后验证定时回退，再注册后恢复安静。保留既有故障、权限、停止/重开、旋转、大小字、实际小窗和十项完整门槛。
- 相机保护仍每 100ms 检查，显示/采集 1200ms、分析 1500ms 等截止不变。本次不将首页刷新变化当成历史显示停顿根因的修复。

本地首轮发现旧测试硬编码原报告赋值表达式；迁移入口断言后 JVM 全部通过，第二轮原生测试编译发现遗漏 TextView 导入，已补齐。完整最终验证尚待完成，不能把这两轮称为成功构建。

本地最终完整验证成功：231 项 JVM，0 失败 / 错误 / 跳过；Lint 0 错误 / 22 警告；通用应用与原生测试包构建通过。源文件与既有预览 UI 守卫见[结构化记录](home-events-2026-10-31.json)。

当前等待 Android 15/16 候选和正式发布检查。云端结果与实际 APK 将在完成后追加；没有真实设备、真实 AR、TalkBack、帧率、耗电或 GC 收益结论。
