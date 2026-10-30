# 唤醒后等待真实输入窗口

用户再次询问 Actions 持续失败，核对后连续三轮旧红叉均在 Android 16 锁屏恢复的目标 UID 注入检查。v0.4.21 发布、main 检查及 v0.4.22 首个标签候选随后首次通过，不能以这些成功认定旧问题已解决。

## 本次终于取得的失败瞬间

标签尾部改进候选 `1f7e512` 的 [41050305115](https://github.com/cyanrbt/walkbuddy/actions/runs/41050305115) 中，API 35 job `124044410999` 首次全部通过，API 36 job `124044411424` 再次在旧锁屏恢复按下失败。应用与测试包编译成功。脚本保留该失败并继续取得其余八项成功记录；字号矩阵 1530 组和对比度 18 组均通过，整轮仍判失败，未重跑来消除红叉。

两个附件均实际下载并核对服务端摘要：

| API | artifact | 字节 | ZIP SHA-256 |
| --- | --- | --- | --- |
| 35 | 12974073057 | 8,014,914 | `670bec8f6d3ba201481cb05bd741bbe108f721de1b115af9af14e233150b4fa0` |
| 36 | 12975140411 | 7,940,710 | `49826f90263c4b717642d0e40d1fb31cd71af2a67cf911dc7587181276efe0dc` |

失败附件新增路由日志已在原 instrumentation 清理前采集，完整运行的路由日志补足后续恢复时刻。UTC 时间线如下：

| 时间 | 原始事件 |
| --- | --- |
| 11:48:21.345 | 输入视口 `isActive: false` |
| 11:48:21.614 | PowerManager 开始从 Asleep 唤醒 |
| 11:48:21.683 | PreviewActivity 恢复，仍为 UNAVAILABLE |
| 11:48:21.787 | 原事件空闲、resumed、焦点、解锁和无障碍包名检查已通过 |
| 11:48:21.789 | 准备在 `(160,578)` 点击 restart_preview |
| 11:48:21.801 | InputDispatcher 报没有有效触摸窗口，拒绝目标 UID 10216 注入 |
| 11:48:22.093 | 输入视口才变为 `isActive: true` |

失败时 `dumpsys input` 的 FocusedApplications / FocusedWindows 已属于应用，但输入视口 `isActive=[0]`，目标 PreviewActivity 窗口 `inputConfig=NOT_TOUCHABLE`。截图为正常的停止页，没有 ANR。此处没有另一个 UID 的接收窗口，而是**没有有效接收窗口**；同一附件中更早的 PiPMenuView 路由日志不是这次失败的事件，不能混为一个原因。

[官方 InputDispatcher 实现](https://android.googlesource.com/platform/frameworks/native/+/04d24da36e/services/inputflinger/dispatcher/InputDispatcher.cpp)会排除 NOT_VISIBLE / NOT_TOUCHABLE 窗口；目标校验在窗口为空时同样返回 TARGET_MISMATCH。结合本次原始日志，可以解释：测试过早把应用焦点等同于系统输入就绪，按下比视口恢复早 292ms。不能据此认定所有历史输入失败都具有相同原因，也不推断实体设备会出现相同时间差。

## 修正与验证门槛

只修改 emulator instrumentation：唤醒事件空闲后，最多等待 5 秒，读取系统输入状态，要求同一 display 的视口实际活动、指定组件与 UID 的输入窗口可触摸、可见、具有通道且未暂停分派。之后重新核对应用 resumed、焦点、亮屏/解锁和无障碍包名；自动息屏返回首页的触摸也先检查对应首页输入窗口。保留原 `Instrumentation.sendPointerSync` 的真实按下/抬起和目标 UID 限制，不重试点击、不改成 performClick 或全局 shell 点击。

成功与超时都保存本次输入快照；不认识的 dump 格式无法通过就绪判断。该解析仅针对固定 Android 15/16 模拟器，不进入主 APK。真实按钮就绪、相机/模型截止与整个探针失败门槛保持。

将失败快照的相关字段摘为测试资产，10 组规则检查覆盖焦点已恢复但视口关闭、窗口仍不可触摸、全部就绪、错误 UID/组件/display、空数据、不可见和无通道；本地独立 javac 执行通过，并在原生恢复探针起始自动复核。146 项原 JVM 测试、Lint 0 错误 / 22 警告、应用与最终测试包构建通过。旧失败与第一次成功记录保留。

## 修正后实际结果

`a6d5f91bf720d0b48f268af9fa2382b4e2228c57` 的 [41051697662](https://github.com/cyanrbt/walkbuddy/actions/runs/41051697662) attempt 1 中，API 35 job `124049071761` 与 API 36 job `124049071471` 首次全部成功。两个附件均下载并按服务端摘要复核，[结构化记录](overlay-fonts-native-2026-10-30.json)保存来源、归档摘要、九项探针与输入就绪日志；没有 FAIL、缺失探针或 ANR 观察。

每套都得到 7 份就绪快照。旧失败位置在 API 35 等待 39ms、API 36 等待 151ms 后通过系统输入检查，随后真实按下/抬起和四次恢复全部成功。相应快照显示输入视口 `isActive=[1]`，完整预览目标窗口 `inputConfig=0x0`、具有通道与完整触摸区域。等待时长包含 dumpsys 读取开销，不能直接当作系统恢复延迟，也未记录每个未就绪瞬间，不能声称本轮恰好拦住了旧的 292ms 窗口。

本次证明补充后的条件与完整原生流程在两套固定模拟器通过；先前失败快照被回归规则明确拒绝，仍不以本次成功证明全部历史输入异常或实体设备问题都已解决。
