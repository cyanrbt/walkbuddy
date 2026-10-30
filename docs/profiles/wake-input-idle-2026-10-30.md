# 唤醒后输入窗口同步

首个亮屏候选 `551403651fcbbb9254a8cd7a2393ff37fd3a70e9` 的 [41040368806](https://github.com/cyanrbt/walkbuddy/actions/runs/41040368806) 中，API 35 job `124011384225` 首次全部通过，含两轮真实自动息屏；API 36 job `124011384692` 在最先执行的锁屏恢复触摸失败，尚未运行新增息屏用例。没有重跑该轮来消除红叉。

已实际下载失败 artifact `12970207384`，64,741 字节，ZIP SHA-256 `67840615b7f54fb3560ba0d0cfffb9569b282e5c8c68dd9063f6817180958fc3`，复核原图与输入快照。前两轮恢复通过，锁屏/唤醒后的 restart_preview 按下报告 UID 不匹配。按钮与全部祖先已布局，focus=true；截图为正常的停止页，无 ANR。异常后 FocusedApplications / FocusedWindows 都属于 PreviewActivity，不能还原注入瞬间的接收窗口。

生命周期显示 10:26:26.879 UTC 首次恢复，27.116 再暂停，27.122 又恢复；27.187 已开始准备注入，27.680 又收到暂停。现有“已恢复/解锁、有焦点”检查可以落在尚未结束的唤醒窗口切换之间。Android Instrumentation 自身已在按下前同步动画，因此不能简单归因为完全缺少动画同步；具体平台事件因果仍未确定。

根据 [UiAutomation.waitForIdle 官方语义](https://developer.android.com/reference/android/app/UiAutomation#waitForIdle(long,%20long))，只在唤醒返回步骤增加事件流空闲 500ms、总上限 5000ms 的检查，之后重新验证 resumed、焦点、亮屏/解锁及当前无障碍窗口包名。等待是基于观察事件的就绪条件，不使用固定 sleep 或重试点击。之后的目标 UID 限制、真实按下/抬起和失败快照均保留；LIVE 后立即再次进入 PiP 的测试顺序与生产的全部截止不变。

这是一项测试同步修正，不能视为生产锁屏根因已解决；后续结果记录在[亮屏验证](screen-idle-2026-10-30.md)。系统事件、窗口焦点与实际输入路由仍有不同范围，OEM 和真实触摸仍需验收。

另已下载首轮 API 35 成功 artifact `12970287287`，7,949,506 字节，SHA-256 `d633ae02f64c0b74b9650edae0bd0f8eeebee18d479910280b813ad2c8df54bf`。两轮均记录 effectiveMinimumMs=10000、无输入观察 11500ms，完整预览电源快照 Awake，小窗自动息屏后 Asleep；两份快照的配置均为 screen_off_timeout=1000 / stay_on_while_plugged_in=0，随后手动恢复通过。该成功不补足同轮 API 36 的缺失验证。
