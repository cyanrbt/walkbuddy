# v0.4.19 候选锁屏恢复输入失败记录

来源：[41029357076](https://github.com/cyanrbt/walkbuddy/actions/runs/41029357076)，源码 `8349f2be1c1f5113fddd47d0572fadda3ba40031`，API 35 job `123975080161`。失败在首项 `pip_recovery`、检测尚未开启时发生；API 36 同提交成功。后续提交只修正布局构造中的开关状态，生产代码无差异，其 [41031071106](https://github.com/cyanrbt/walkbuddy/actions/runs/41031071106) 两套 API 首次通过。没有据后续通过称输入问题已修复。

实际下载 artifact `12966081998`，60,665 字节，ZIP SHA-256 `8996d2dbcf7b1a3ea9a337cd29cffb845244e6234eae28a1b4269d81cdc06f9e`。09:09:51 UTC 的失败为 `restart_preview` 的 ACTION_DOWN 注入目标 UID 不匹配：Activity 观察到 focus=true、pip=false、attached/shown/enabled=true，按钮及全部祖先 laidOut=true、layoutRequested=false；目标 UID 为 10209。此时相机按锁屏规则已停止，未执行本轮模型故障注入用例。

本次确实触发新增采集分支，并在 Activity/instrumentation 清理前写出：

| 快照 | 解码文字长度 |
| --- | ---: |
| window | 44,817 |
| input | 67,536 |
| activity | 34,597 |

`input` 快照中的 FocusedApplications、FocusedWindows 均已指向 `com.cyanrbt.walkbuddy/.PreviewActivity`。这是注入异常之后采集的状态，不能倒推注入瞬间的目标窗口，尚未证明具体的窗口转换或输入同步根因。该次已收集日志没有 ANR 条目，也没有外部弹窗焦点证据，不能沿用此前 Pixel Launcher ANR 的归因。

本次 Android 15 `window windows` 输出不含报告器当前匹配的 `mCurrentFocus=` 字段，摘要中的 failure_time_focus 因而为空；原始 input 快照仍可人工核对。后续可改善窗口/输入时序取证，但不能把缺字段解释成“没有焦点问题”。新的快照采集和原始异常保留已得到一次真实失败运行验证，根因仍待定位。
