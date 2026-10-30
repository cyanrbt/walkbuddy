# v0.4.18 首轮锁屏唤醒输入失败原始摘录

来源：[运行 41009472668](https://github.com/cyanrbt/walkbuddy/actions/runs/41009472668)，首次 API 36 job `123911844285`，artifact `12957355801`，源码 `243fb795c88d6a2a229f5161fa7cba0777479e8f`。从已下载 ZIP 中提取 `pip_recovery.txt` 与 `pip-timing.txt`；仅含模拟器应用状态和触摸坐标，不含环境画面。

前两次循环成功，第三次在锁屏/唤醒后的重新开启触摸处遇到目标 UID 注入错误。同步的系统时序显示：05:40:38.950 开始睡眠，05:40:40.152 进入睡眠，05:40:40.693 唤醒，05:40:41.278 报告 Screen on took 614ms。focus/resumed 的应用观察与实际输入目标不等价，具体根因尚未证明。原提交仅重跑失败作业后成功，发布流程与 main 全部原生检查随后首次通过；这不构成该输入时序问题已修复的证据。

```text
RECOVERY_BEFORE_INPUT cycle=0 resumed=true phase=LIVE fresh=true focus=true enabled=true attached=true shown=true listener=true current=true input=touch
PASS immediate PiP recovery cycle=0
RECOVERY_BEFORE_INPUT cycle=1 resumed=true phase=LIVE fresh=true focus=true enabled=true attached=true shown=true listener=true current=true input=touch
PASS immediate PiP recovery cycle=1
PIP_RECOVERY_PROBE_FAIL: java.lang.IllegalArgumentException: Targeted input event injection from pid 2138 was not directed at a window owned by uid 10216.; 预览已离开屏幕，摄像头已停止；请主动重新开启
识别关着
```

关键触摸与生命周期摘录：

```text
10-30 05:40:37.042  2138  2248 I WalkBuddyInput: tap id=enter_pip bounds=Rect(16, 552 - 304, 604) focus=true pip=false
10-30 05:40:37.075  2138  2138 I WalkBuddyInput: after-down id=enter_pip bounds=Rect(16, 552 - 304, 604) focus=true pip=false
10-30 05:40:37.089  2138  2138 I WalkBuddyPip: request resumed=true phase=LIVE pip=false focus=true captureGapMs=67 displayGapMs=18
10-30 05:40:37.125  2138  2138 I WalkBuddyPip: accepted=true resumed=true phase=LIVE pip=true focus=true captureGapMs=103 displayGapMs=54
10-30 05:40:37.134  2138  2138 I WalkBuddyPip: pause resumed=false phase=LIVE pip=true focus=true captureGapMs=7 displayGapMs=4
10-30 05:40:37.502  2138  2248 I WalkBuddyInput: tap id=start bounds=Rect(116, 62 - 300, 114) focus=true pip=false
10-30 05:40:38.201  2138  2138 I WalkBuddyPip: mode=true resumed=false phase=LIVE pip=true focus=false captureGapMs=10 displayGapMs=17
10-30 05:40:38.344  2138  2138 I WalkBuddyInput: after-down id=start bounds=Rect(116, 62 - 300, 114) focus=true pip=false
10-30 05:40:38.436  2138  2138 I WalkBuddyPip: mode=false resumed=false phase=LIVE pip=false focus=false captureGapMs=42 displayGapMs=30
10-30 05:40:38.857  2138  2138 I WalkBuddyPip: resume resumed=true phase=LIVE pip=false focus=true captureGapMs=58 displayGapMs=20
10-30 05:40:40.098  2138  2138 I WalkBuddyPip: pause resumed=false phase=LIVE pip=false focus=true captureGapMs=34 displayGapMs=8
10-30 05:40:40.883  2138  2138 I WalkBuddyPip: resume resumed=true phase=UNAVAILABLE pip=false focus=false captureGapMs=-1 displayGapMs=-1
10-30 05:40:41.126  2138  2138 I WalkBuddyPip: pause resumed=false phase=UNAVAILABLE pip=false focus=false captureGapMs=-1 displayGapMs=-1
10-30 05:40:41.128  2138  2138 I WalkBuddyPip: resume resumed=true phase=UNAVAILABLE pip=false focus=false captureGapMs=-1 displayGapMs=-1
10-30 05:40:41.210  2138  2248 I WalkBuddyInput: tap id=restart_preview bounds=Rect(16, 552 - 304, 604) focus=true pip=false
10-30 05:40:41.496  2138  2138 I WalkBuddyPip: pause resumed=false phase=UNAVAILABLE pip=false focus=true captureGapMs=-1 displayGapMs=-1
```
