# 唤醒后输入窗口同步

首个亮屏候选 `551403651fcbbb9254a8cd7a2393ff37fd3a70e9` 的 [41040368806](https://github.com/cyanrbt/walkbuddy/actions/runs/41040368806) 中，API 35 job `124011384225` 首次全部通过，含两轮真实自动息屏；API 36 job `124011384692` 在最先执行的锁屏恢复触摸失败，尚未运行新增息屏用例。没有重跑该轮来消除红叉。

已实际下载失败 artifact `12970207384`，64,741 字节，ZIP SHA-256 `67840615b7f54fb3560ba0d0cfffb9569b282e5c8c68dd9063f6817180958fc3`，复核原图与输入快照。前两轮恢复通过，锁屏/唤醒后的 restart_preview 按下报告 UID 不匹配。按钮与全部祖先已布局，focus=true；截图为正常的停止页，无 ANR。异常后 FocusedApplications / FocusedWindows 都属于 PreviewActivity，不能还原注入瞬间的接收窗口。

生命周期显示 10:26:26.879 UTC 首次恢复，27.116 再暂停，27.122 又恢复；27.187 已开始准备注入，27.680 又收到暂停。现有“已恢复/解锁、有焦点”检查可以落在尚未结束的唤醒窗口切换之间。Android Instrumentation 自身已在按下前同步动画，因此不能简单归因为完全缺少动画同步；具体平台事件因果仍未确定。

根据 [UiAutomation.waitForIdle 官方语义](https://developer.android.com/reference/android/app/UiAutomation#waitForIdle(long,%20long))，只在唤醒返回步骤增加事件流空闲 500ms、总上限 5000ms 的检查，之后重新验证 resumed、焦点、亮屏/解锁及当前无障碍窗口包名。等待是基于观察事件的就绪条件，不使用固定 sleep 或重试点击。之后的目标 UID 限制、真实按下/抬起和失败快照均保留；LIVE 后立即再次进入 PiP 的测试顺序与生产的全部截止不变。

这是一项测试同步修正，不能视为生产锁屏根因已解决；后续结果记录在[亮屏验证](screen-idle-2026-10-30.md)。系统事件、窗口焦点与实际输入路由仍有不同范围，OEM 和真实触摸仍需验收。

另已下载首轮 API 35 成功 artifact `12970287287`，7,949,506 字节，SHA-256 `d633ae02f64c0b74b9650edae0bd0f8eeebee18d479910280b813ad2c8df54bf`。两轮均记录 effectiveMinimumMs=10000、无输入观察 11500ms，完整预览电源快照 Awake，小窗自动息屏后 Asleep；两份快照的配置均为 screen_off_timeout=1000 / stay_on_while_plugged_in=0，随后手动恢复通过。该成功不补足同轮 API 36 的缺失验证。

## 事件空闲不足以排除重复启动

增加事件等待的 `4f490ae` 在 [41041267876](https://github.com/cyanrbt/walkbuddy/actions/runs/41041267876) 中 API 35 job `124014374468` 成功，API 36 job `124014375304` 仍在相同恢复触摸处失败。已下载 API 36 artifact `12971170157`，64,616 字节，SHA-256 `169391a3c5b949001f149347e42682771263acbc97f3f19b4aa30960483ed8e3`。不能将事件等待单独称为修复。

该轮时序更清楚：10:33:41.034 UTC 系统唤醒，41.094 应用自然恢复；41.193 测试又发出指向 PreviewActivity 的 START / LAUNCH_SINGLE_TASK，随后 41.204 暂停、41.214 恢复。41.294 事件空闲/包名检查通过，之后目标输入仍失败。最后一次 pause 可能已属于失败清理，不能将所有后续 pause 都当作异常原因。

[Activity.onNewIntent 官方生命周期](https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent))明确说明，给已恢复 Activity 送新 Intent 会先暂停，再恢复。因此原测试在系统自然返回后重复发启动 Intent，会制造额外生命周期转换；空闲事件流也不能证明尚未发出事件的启动请求已经完成。这是有证据的测试序列问题，仍不把它等同于所有历史 UID 失败的完整根因。

后续验证将完整预览锁屏改为直接观察系统自然返回，不重复启动；小窗自然息屏后则等待首页恢复，通过真实首页按钮主动展开，再检查相机仍停止和手动重开。两种用户路径分别覆盖，原事件就绪、目标 UID 输入和严格失败检查保留。生产代码与首轮候选无差异；结果仍由后续完整原生流程确认。

## 自然唤醒后仍需路由证据

自然唤醒候选 `58e3900` 的 [41042398884](https://github.com/cyanrbt/walkbuddy/actions/runs/41042398884) API 36 job `124018124877` 仍在旧恢复触摸处报告相同 UID 异常，未运行新增息屏检查。因此删除重复 Intent 也不是充分解释，不能将同步调整称为平台根因修复。

直接读取 [Android 16 InputDispatcher 官方实现](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-16.0.0_r1/services/inputflinger/dispatcher/InputDispatcher.cpp)后，确认拒绝目标注入时会通过 InputDispatcher 日志写出实际窗口/UID 或没有有效目标的原因。新增失败瞬间的 input-routing 快照，并在退出时保留同类日志，避免仅依赖异常后的焦点快照推断。源文件 tag 为 `android-16.0.0_r1`。

默认探针脚本保留每项失败，随后继续执行其余 instrumentation 进程，最终只要任一失败或命令非零便返回失败；同文件同时含 PASS/FAIL 也失败。原有 finally 清理仍执行，后续结果不能自动视为与前项失败完全独立，但能够补足原先因首项退出而缺失的诊断。12 项工具测试通过，其中四项通过假的 adb 执行实际 shell 脚本，核对全成功、首项失败后继续、非零退出与冲突标记均保留正确门槛。
