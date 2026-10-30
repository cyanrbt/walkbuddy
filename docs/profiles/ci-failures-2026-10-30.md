# 2026-10-30 Actions 失败原因核对

本文件按历次核对保留 Actions 失败证据，下面的历史版本和“本次”均对应各段记录时的状态；当前结果先看最新核对。编译、模拟器检查、APK 发布与实体设备验收分别记录。

## 最新核对：2026-10-31 04:34（北京时间）

再次直接查询最近 50 次 Actions：最近 18 次运行的**当前终态**均为成功，其中最近 12 次均为 attempt 1 成功。18 次包含 v0.4.28 候选的失败后重跑，不能表述为连续 18 次首次通过。[本次状态与原始日志摘录](ci-status-2026-10-31-0434.json)保留查询范围、运行和作业 ID。

- 最近发生过失败的首轮仍是 [41091065722 attempt 1](https://github.com/cyanrbt/walkbuddy/actions/runs/41091065722/attempts/1)：重读 API 36 job `124184760241`，`BUILD SUCCESSFUL in 51s` 后在 `recovery PiP frames advance` 超时。原附件的显示帧龄为 1352ms，超过 1200ms 截止；同源码重跑通过，停顿根因仍未确定，见[显示截止记录](pip-display-expiry-2026-10-31.md)。
- 最近保留红叉的 [41073282182](https://github.com/cyanrbt/walkbuddy/actions/runs/41073282182)：重读 API 35 job `124122673655`，`BUILD SUCCESSFUL in 54s` 后在 `recovery manual restart` 超时。原始输入记录显示目标窗口暂停；后续增加展开后的系统输入就绪检查，见[输入证据与修正范围](input-after-pip-expand-2026-10-30.md)。历史上还包含已单独处理的布局和 RGB 回归、Launcher ANR 等，不能统一归因于模拟器。
- 最新 [v0.4.33 正式发布 41120796047](https://github.com/cyanrbt/walkbuddy/actions/runs/41120796047) 的 Android 15、Android 16、APK 三个作业均已终态成功，均为 attempt 1。重读 APK 日志确认 `BUILD SUCCESSFUL in 58s` 和发布地址；Release 实际存在 9,975,340 字节的 [ARM64 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.33/Walk-Buddy-v0.4.33-arm64-debug.apk)，服务端 SHA-256 与此前[实际下载核验记录](apk-v0.4.33.json)一致。

本次只核对状态与日志并更新记录，没有重跑历史任务、修改应用或生成重复标签。当前发布成功与历史故障根因未完全解决分别表述；模拟器与 APK 核验不替代实体设备验收。

## v0.4.33 后续发布核对

[首轮候选 41117582608](https://github.com/cyanrbt/walkbuddy/actions/runs/41117582608)、[仅采证修订候选 41119141180](https://github.com/cyanrbt/walkbuddy/actions/runs/41119141180) 与[正式发布 41120796047](https://github.com/cyanrbt/walkbuddy/actions/runs/41120796047) 均 attempt 1 成功。首轮原图没有证明缩小后尺寸，修订等待实际窗口缩小和新帧后再截图，生产代码未改；两轮候选和标签附件均保留。修订 API 36 有外部系统服务 ANR 观察，无聚焦对话框或本应用 ANR 观察；标签两套摘要无 ANR 观察，不能据此称外部问题或旧显示停顿已修复。

三个发布作业与整轮终态成功已确认，原始附件均下载复核，实际 Release APK 已下载核验 0.4.33 / 45、固定签名、摘要、新状态实现及测试/样例排除。状态接口在实际日志结束、Release 已有 APK 时仍返回 queued/in_progress，最终终态已取得，正式流程未重跑，具体滞后原因未定位；[完整状态与来源](preview-only-default-2026-10-31.md)区分触发、构建日志、实际附件、整体终态和设备范围。

## v0.4.32 后续发布核对

[候选 41113481089](https://github.com/cyanrbt/walkbuddy/actions/runs/41113481089) 与[正式发布 41114533950](https://github.com/cyanrbt/walkbuddy/actions/runs/41114533950) attempt 1 两套 Android 首次十项通过，APK 作业首次成功，三个发布作业与整轮终态成功已确认。四套原始附件均下载核对摘要并重放通过，无 ANR 观察；实际 Release APK 已下载核验 0.4.32 / 44、固定签名、摘要、新投影实现及测试代码排除。[投影验证记录](depth-projection-2026-10-31.md)区分构造数据、真实 AR 和性能范围，未重跑或覆盖旧失败，不对历史显示停顿作新根因结论。

## v0.4.31 后续发布核对

[候选 41109403901](https://github.com/cyanrbt/walkbuddy/actions/runs/41109403901) 和[正式发布 41110471304](https://github.com/cyanrbt/walkbuddy/actions/runs/41110471304) attempt 1 两套 Android 检查均首次十项通过，正式 APK 作业首次成功，实际 Release APK 已完整下载核验 0.4.31 / 43、固定签名与 GitHub 摘要。四套原始附件均下载匹配摘要并重放通过，无 ANR 观察；[音频验证记录](tone-threading-2026-10-31.md)保留来源及实体边界。正式流程状态查询曾滞后于日志/附件/实际发布，包括公开 API 返回旧状态；最终终态及三个作业成功已确认，没有重跑，也不认定具体缓存层或调度根因。历史显示停顿和旧红叉均保留，不据本版音频线程迁移推断根因修复。

## 2026-10-31 02:35 核对（北京时间）

用户再次询问持续失败后，重新查询最近 40 次 Actions、最新 Release、最近保留红叉和最近失败首轮的作业步骤，并直接重读两个失败 job 的完整日志。最近连续五次运行均为 attempt 1 成功：v0.4.28 正式发布、v0.4.29 候选与正式发布、v0.4.30 候选与正式发布。

- 最近发生过失败的首轮是 [41091065722 attempt 1](https://github.com/cyanrbt/walkbuddy/actions/runs/41091065722/attempts/1)：API 36 job `124184760241` 已 `BUILD SUCCESSFUL`，随后在 `recovery PiP frames advance` 超时；已保留的失败附件记录显示显示帧龄 1352ms，超过 1200ms 保护截止。原提交失败作业重跑后整轮成功，但停顿根因仍未确定，不能称为已修复，见[原始时序与截图](pip-display-expiry-2026-10-31.md)。
- 最近仍保留红叉的 [41073282182](https://github.com/cyanrbt/walkbuddy/actions/runs/41073282182) 也是编译成功后失败：API 35 job `124122673655` 在 `recovery manual restart` 超时。其失败时输入附件明确目标窗口暂停，后续补充展开后系统输入就绪检查；证据与修正范围见[输入追踪](input-after-pip-expand-2026-10-30.md)。不能将末尾画面过期提示直接当作这次触摸失败的原因。
- 最新 [v0.4.30 正式发布 41103784224](https://github.com/cyanrbt/walkbuddy/actions/runs/41103784224) 的 API 35 job `124227479617`、API 36 job `124227479142`、APK job `124230917143` 均已终态成功。Release 实有 [Walk-Buddy-v0.4.30-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.30/Walk-Buddy-v0.4.30-arm64-debug.apk)，9,974,776 字节，GitHub 摘要 `00d0e8968610879741256cf3806dbbe464ad05987a986b0a4ab5a53a7c69956e`，与此前实际下载核验的[附件记录](apk-v0.4.30.json)一致。

本次核对没有重跑或删除历史失败，也未放宽检查门槛。旧红叉包含应用/布局回归、测试输入时序和系统异常等不同问题，不能全部归因于模拟器不稳定。此次只更新核对记录，没有应用版本变更；不另发标签制造重复安装包。

最初核对时，直接查询最近 20 次 Actions、最近五个 Releases，并读取相关失败 job 日志。当时 v0.4.15、v0.4.16、v0.4.17、v0.4.18 的 `Release APK` 均首次成功，各自的 Release 确有 APK；红叉主要发生在 main 分支的 `Android UI checks` 模拟器运行步骤，之前的 `Build emulator test APKs` 已成功。

## 再次核对：最近红叉与当前状态

v0.4.30 的 [候选 41101431137](https://github.com/cyanrbt/walkbuddy/actions/runs/41101431137) 与 [正式发布 41103784224](https://github.com/cyanrbt/walkbuddy/actions/runs/41103784224) 两套 Android 检查均首次十项全部成功，APK 作业首次成功；四套原始附件实际下载复核，摘要无 ANR 观察，实际 APK 已下载核对版本 42、固定签名和 GitHub 摘要。候选状态接口曾返回 queued / in_progress、steps 为空，而原始日志显示检查已经完成；此前排队判断已更正，不认定具体缓存或调度原因。公开未登录 API 限流与测试结果分别处理，没有重跑来改变状态；[振动设置记录](vibration-choice-2026-10-31.md)保留原始来源和验收范围。

v0.4.29 的 [候选 41096792807](https://github.com/cyanrbt/walkbuddy/actions/runs/41096792807) 与 [正式发布 41098199220](https://github.com/cyanrbt/walkbuddy/actions/runs/41098199220) 两套 Android 检查均首次十项全部通过，标签 APK 作业首次成功。Release 实际 APK 已下载核对版本 41、固定签名和 GitHub 摘要；本版只修正动画来源/目标边界及跟踪，不称旧显示停顿已修复。候选 API 35 有外部系统服务 ANR 观察，标签两套摘要无 ANR 观察，原记录均保留；[完整来源与范围](pip-source-bounds-2026-10-31.md)区分参数、模拟器行为与真机效果。

v0.4.28 候选同源码单次重跑 Android 16 后十项通过，Android 15 首次结果保留，初轮失败仍保留。随后 [正式发布 41093630773](https://github.com/cyanrbt/walkbuddy/actions/runs/41093630773) attempt 1 的 Android 15/16 与 APK 作业均首次通过，Release 实际 [v0.4.28 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.28/Walk-Buddy-v0.4.28-arm64-debug.apk)已下载核对版本 40、固定签名与 GitHub 摘要。两套原始标签附件已下载，API 36 有系统服务 `com.google.android.as` ANR 观察，不能归因于初轮显示停顿；[完整结果](diagnostics-2026-10-31.md)保留来源和边界。未把重跑通过称为根因修复。

随后 v0.4.28 新候选 [41091065722 attempt 1](https://github.com/cyanrbt/walkbuddy/actions/runs/41091065722/attempts/1) 出现新的失败，不能沿用此前“最近五次成功”当作当前结果：API 35 十项通过，API 36 旧恢复流程的第二次小窗进入时显示龄 1352ms，超过原 1200ms 截止，应用停止预览，其余九项通过。编译和新增复制功能均通过，整轮仍失败，未发标签。原始截图和时序已下载核对，[专门记录](pip-display-expiry-2026-10-31.md)区分本次显示停顿与旧触摸输入失败；仅请求失败作业重跑一次，不改安全截止，不称根因已修复。

2026-10-31 用户再次询问后，直接读取最近 30 次 Actions、最近六轮失败作业步骤和三份失败原始日志。最近连续五次运行均为首次成功，包括 v0.4.26、v0.4.27 发布；最新 [v0.4.27 发布 41083803493](https://github.com/cyanrbt/walkbuddy/actions/runs/41083803493) 的两套 Android 检查与 APK 作业均成功，Release 实有 9,953,236 字节 APK。最近红叉仍为下述 `41073282182` 的旧恢复测试，编译成功后在 `recovery manual restart` 超时。没有新的持续打包失败，也没有通过重跑或删除消除旧红叉；此结论仅对应本次查询时的运行状态。

v0.4.27 触觉候选 [41081394886](https://github.com/cyanrbt/walkbuddy/actions/runs/41081394886) attempt 1 两套 API 首次九项全部成功。原始附件已实际下载核对摘要；API 36 同时记录外部 Google 服务域验证广播 ANR，未观察到应用 ANR，不能将通过写成无 ANR，也不据共存推断因果。[触觉原始记录](haptics-2026-10-30.md)保留结果。读取状态期间连接工具和公开 API 发生过网络错误，恢复后核对原运行成功，没有重跑构建。

v0.4.26 提醒候选 [41073282182](https://github.com/cyanrbt/walkbuddy/actions/runs/41073282182) 的 API 35 首次失败在原 PiP 展开后的恢复点击，API 36 九项成功；两套新增提醒检查均成功。实际输入路由明确目标 `because it is paused`，应用未收到恢复请求；不能把末尾“画面已过期”误判为自然相机故障。已下载复核附件并保留[时间线、原图和测试修正范围](input-after-pip-expand-2026-10-30.md)，该轮未发标签、未重跑。

补充展开后系统输入就绪检查后的新提交 [41075111797](https://github.com/cyanrbt/walkbuddy/actions/runs/41075111797) attempt 1 两套 API 首次九项全部成功；完成后的原始 job 日志已读取，新 11 项输入规则、四轮实际恢复及提醒检查通过。前述红叉保留，不能把本次成功扩大为全部历史输入异常或实体设备均已修复。

用户再次询问后，直接重新查询最近 20 次 Actions，并读取最近失败 API 36 job `124044411424` 的原始日志：`BUILD SUCCESSFUL` 后在 `PIP_RECOVERY_PROBE_FAIL` 的目标 UID 触摸注入处失败。查询时最近连续 10 次运行均为 attempt 1 成功，包括 v0.4.22、v0.4.23、v0.4.24 三次标签发布；最新候选 [41067870083](https://github.com/cyanrbt/walkbuddy/actions/runs/41067870083) 两套 API 也首次成功。v0.4.24 三个 job 和实际 APK 附件均再次确认存在。旧红叉仍保留，不能把历史检查失败当成当前发布仍失败，也不能以这组成功保证后续运行不会失败。

再次查询最近 100 次运行，并读取最近失败 job `124018124877` 的原始日志：最近连续三个红叉 [41040368806](https://github.com/cyanrbt/walkbuddy/actions/runs/41040368806)、[41041267876](https://github.com/cyanrbt/walkbuddy/actions/runs/41041267876)、[41042398884](https://github.com/cyanrbt/walkbuddy/actions/runs/41042398884) 均为 Android 16 唤醒后恢复触摸检查失败，编译已通过。日志显示目标 `restart_preview` 的按下被系统以目标 UID 不匹配拒绝；不能据此确定注入瞬间的实际窗口，也不能把所有历史失败都归为同一原因。

后续 [41043678006](https://github.com/cyanrbt/walkbuddy/actions/runs/41043678006)、[41044965636](https://github.com/cyanrbt/walkbuddy/actions/runs/41044965636) 及当前标签改进首个候选 [41048695368](https://github.com/cyanrbt/walkbuddy/actions/runs/41048695368) 的两套 Android 检查均首次成功；v0.4.21 发布流程和 APK 附件也已成功。历史失败保留，不用后续成功覆盖其原因未定的事实。向用户报告时分别给出“触发”“模拟器结果”“APK 发布及下载”，避免将任何 Actions 红叉统称为打包失败。

随后标签尾部改进候选 [41050305115](https://github.com/cyanrbt/walkbuddy/actions/runs/41050305115) 的 API 36 再次失败，API 35 通过。本次新路由证据明确显示：点击时输入视口尚未活动，应用窗口仍不可触摸，292ms 后视口才恢复；并非 APK 编译失败。已据此补充系统输入就绪条件，保持原真实触摸、截止和失败门槛，详见[失败时间线与修正](input-target-ready-2026-10-30.md)。该失败轮次未发布 v0.4.22，不能把此前绿勾当作后续结果。

修正后的 [41051697662](https://github.com/cyanrbt/walkbuddy/actions/runs/41051697662) 两套 API 首次全部成功；随后 v0.4.22 标签的发布流程也首次全部成功，实际 APK 已下载并核验，详见下一节。此次确认的是输入就绪条件补充及本轮回归，仍保留其它历史故障与实机边界。

## 已查明的失败

| Actions | 日志中的具体失败 | 后续处理与当前边界 |
| --- | --- | --- |
| [40999704272](https://github.com/cyanrbt/walkbuddy/actions/runs/40999704272) | `Native checkbox lost label/state`；在未附着窗口快照读取节点元数据 | 将严格语义断言移到实际相机窗口；不是跳过断言，后续通过 |
| [41000875826](https://github.com/cyanrbt/walkbuddy/actions/runs/41000875826) API 36 | 控件底边 381px，视口底边 377px | 修正 320×480 / 200% 字体时说明区域留白，后续严格可见检查通过 |
| [41006155456](https://github.com/cyanrbt/walkbuddy/actions/runs/41006155456)、[41006806141](https://github.com/cyanrbt/walkbuddy/actions/runs/41006806141) | `RGB parity failed for [137, 59] rotation=90 ... byte=91473` | 两次尝试合并绘制均不满足逐字节对照；撤回该实现，最终保留旧绘制顺序并复用 worker 旋转缓冲，未放宽断言 |
| [41014998568](https://github.com/cyanrbt/walkbuddy/actions/runs/41014998568) attempt 1 API 35 | `Timed out: touch target ready ... interactive=true keyguard=false; 已停止；请主动开启` | 原提交重跑失败 job 后 attempt 2 成功；应用与 v0.4.18 无源码差异，触摸/窗口时序根因仍未定位，不能将重跑通过称为修复 |

已直接读取的 job：`123880977735`、`123884714492`、`123901411736`、`123929214335`。完整历史和对应应用修正见[验证结果](../验证结果.md)。不重跑已被后续实现替代的历史失败来消除红叉，也不删除失败记录。

后续实际下载并复核 `41014998568` 首轮失败附件，确认该次首页被 **Pixel Launcher 未响应弹窗**遮挡，Launcher ANR 早于应用启动；这是这一次首次触摸超时的直接阻挡证据。原图、时间线与其它锁屏失败仍未证明因果关系的边界见[系统 ANR 复核](input-2026-10-30-launcher-anr.md)。本轮补充失败时焦点/输入快照和 Actions 分阶段摘要，没有放宽触摸条件或自动关闭系统弹窗。

诊断提交 `a408b0d` 的 [41022973332](https://github.com/cyanrbt/walkbuddy/actions/runs/41022973332) 两套 API 首次通过。已下载两套 artifacts，确认各九项探针原始记录齐全，新增 JSON/Markdown 摘要与本地重放一致；本轮未触发失败时采集分支，不能据成功结果宣称其它故障已修复，完整验证边界见上方复核记录。

## v0.4.19 后续核对

[41025609152](https://github.com/cyanrbt/walkbuddy/actions/runs/41025609152) 的新增提示条检查误用了按钮最小 48px 触摸尺寸要求，已改成非交互提示的完整边界和文字断言，按钮尺寸要求保留。最终候选 [41031071106](https://github.com/cyanrbt/walkbuddy/actions/runs/41031071106) API 35/36 均首次成功；中间锁屏输入失败仍单独保留[快照与范围](input-after-wake-2026-10-30-v0.4.19.md)，没有凭重跑下根因结论。

标签发布 [41033072437](https://github.com/cyanrbt/walkbuddy/actions/runs/41033072437/attempts/1) 首轮 API 35 在首次首页触摸准备处失败，此时相机和模型尚未开启。已下载并查看原图：**Pixel Launcher 未响应弹窗**挡住首页；失败时 input 快照也明确显示该弹窗占有焦点。API 36 首次通过，APK 作业因前置检查未过而跳过，并非 APK 编译失败。随后仅重跑原标签失败作业，attempt 2 的 API 35 和 APK 作业成功。该次证据见[发布首轮记录](release-v0.4.19-first-attempt.md)，不能推断所有旧触摸失败都由 Launcher 引起。

## v0.4.27 发布

[v0.4.27 发布流程 41083803493](https://github.com/cyanrbt/walkbuddy/actions/runs/41083803493) attempt 1 成功，Android 15/16 与固定签名 APK 作业均首次通过，同提交 main 两套也首次成功。两套发布原始日志各九项 PASS，新触觉、图例/缩放和两轮 Activity 接线检查通过；标签 API 36 附件已实际下载核对摘要并查看新图，摘要没有 ANR 观察。Release 实际存在 [v0.4.27 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.27/Walk-Buddy-v0.4.27-arm64-debug.apk)，已完整下载核对版本 0.4.27 / 39、固定签名和 GitHub 摘要，详见[附件 JSON](apk-v0.4.27.json)。候选外部 Google 服务 ANR 仍保留，真实设备仍待验收。

## v0.4.26 发布

[v0.4.26 发布流程 41076188374](https://github.com/cyanrbt/walkbuddy/actions/runs/41076188374) attempt 1 成功，Android 15/16 与固定签名 APK 作业均首次通过，各九项 PASS、无探针 FAIL，包含新增提醒时序与 11 项输入规则。Release 实际存在 [v0.4.26 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.26/Walk-Buddy-v0.4.26-arm64-debug.apk)，已完整下载核对版本 0.4.26 / 38、固定签名与 GitHub 摘要；[附件 JSON](apk-v0.4.26.json)保存结果。此前候选 PiP 展开输入失败保留；下载的 TLS 连接重试没有改变或重跑构建，真实设备仍待验收。

## v0.4.25 发布

[v0.4.25 发布流程 41070696616](https://github.com/cyanrbt/walkbuddy/actions/runs/41070696616) attempt 1 成功，Android 15/16 与固定签名 APK 作业均首次通过。原始 UI 日志各九项 PASS、无探针 FAIL，包含原锁屏唤醒检查和 48 帧零垂直换位、三种重置对照。Release 实际存在 [v0.4.25 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.25/Walk-Buddy-v0.4.25-arm64-debug.apk)，已通过官方资产 API 完整下载并核对版本 0.4.25 / 37、固定签名及 GitHub 摘要；[附件 JSON](apk-v0.4.25.json)保存结果。历史红叉保留，真实设备仍待验收。

## v0.4.24 发布

[v0.4.24 发布流程 41061317021](https://github.com/cyanrbt/walkbuddy/actions/runs/41061317021) attempt 1 成功，Android 15/16 与固定签名 APK 作业均首次通过，各 14 组风险颜色检查全部匹配。Release 实际存在 [v0.4.24 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.24/Walk-Buddy-v0.4.24-arm64-debug.apk)，已完整下载核对版本 0.4.24 / 36、固定签名及 GitHub 摘要；[附件 JSON](apk-v0.4.24.json)保存结果。本环境的浏览器地址下载超时后，改用官方资产 API 下载成功，构建本身没有失败或重跑。真实设备仍待验收。

## v0.4.23 发布

[v0.4.23 发布流程 41056676300](https://github.com/cyanrbt/walkbuddy/actions/runs/41056676300) attempt 1 成功，Android 15/16 和 APK 作业均首次通过。密集标签改进另有基线、候选两套 API 首次成功及已下载的矩形/截图对照，未重跑掩盖失败。[v0.4.23 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.23/Walk-Buddy-v0.4.23-arm64-debug.apk)已实际下载核对版本 0.4.23 / 35、固定签名和资产摘要；[附件 JSON](apk-v0.4.23.json)保留结果。历史输入故障的因果边界仍按原记录，真实设备仍待验收。

## v0.4.22 发布

[v0.4.22 发布流程 41052842543](https://github.com/cyanrbt/walkbuddy/actions/runs/41052842543) attempt 1 成功：Android 15/16 和 APK 作业均首次通过，原始 job 日志各有九项 PASS、10 项输入规则、1530 组标签布局和 18 组对比度，无探针 FAIL。Release 实际存在 [Walk-Buddy-v0.4.22-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.22/Walk-Buddy-v0.4.22-arm64-debug.apk)，已实际下载核验版本 0.4.22 / 34、固定证书、模型、中文表和 11sp 资源；[附件 JSON](apk-v0.4.22.json)保存完整摘要。真机仍待验收。

## v0.4.21 发布

[v0.4.21 发布流程 41044965502](https://github.com/cyanrbt/walkbuddy/actions/runs/41044965502) attempt 1 成功：Android 15/16 与 APK 作业均首次通过，同提交 main 两套 API 也首次成功。Release 实际存在 [Walk-Buddy-v0.4.21-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.21/Walk-Buddy-v0.4.21-arm64-debug.apk)，已下载核对固定签名、0.4.21 / 33、资产摘要和亮屏资源，见[附件 JSON](apk-v0.4.21.json)。

本版此前三轮 API 36 候选仍在旧锁屏恢复的目标 UID 注入处失败，事件空闲和删除重复启动 Intent 都未单独解决。后续 [41043678006](https://github.com/cyanrbt/walkbuddy/actions/runs/41043678006) 两套 API 首次成功，实际短超时与自然息屏记录已下载；失败历史、因果边界与新输入路由采证见[追踪](wake-input-idle-2026-10-30.md)。脚本现在保留每项失败后继续取得其它探针记录，整轮仍因任一失败而失败；四项真实 shell 假 adb 测试和八项报告测试均通过，不以重跑或通过结果宣称所有输入问题已修复。

## v0.4.20 发布

[v0.4.20 发布流程 41038083125](https://github.com/cyanrbt/walkbuddy/actions/runs/41038083125) attempt 1 成功，Android 15/16 与 APK 作业均首次通过。Release 实际存在 [Walk-Buddy-v0.4.20-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.20/Walk-Buddy-v0.4.20-arm64-debug.apk)，已下载核对固定签名、版本和资产摘要，详见[核验 JSON](apk-v0.4.20.json)。

本版候选 [41036811932](https://github.com/cyanrbt/walkbuddy/actions/runs/41036811932) 的 API 35 首轮仍在锁屏恢复触摸处出现目标 UID 不匹配，API 36 首次通过；原提交仅重跑失败作业后 API 35 成功，两套附件已下载。异常后快照与截图没有系统 ANR 弹窗，焦点属于应用，不能套用前一轮的 Launcher 原因；[具体证据与边界](input-after-wake-2026-10-30-v0.4.20.md)保留。新发布首次通过不意味着旧输入根因已修复，实机仍待验收。

## v0.4.19 发布

[v0.4.19 发布流程 41033072437](https://github.com/cyanrbt/walkbuddy/actions/runs/41033072437) attempt 2 已成功，Release 实际存在 [Walk-Buddy-v0.4.19-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.19/Walk-Buddy-v0.4.19-arm64-debug.apk)。已下载核对 9,950,381 字节、GitHub 资产摘要、固定签名与版本 0.4.19 / 31，详见[核验 JSON](apk-v0.4.19.json)。此为模拟器及发布附件检查，实机仍待验收。

## 此前发布

[v0.4.18 发布流程 41011135290](https://github.com/cyanrbt/walkbuddy/actions/runs/41011135290) 首次成功，含 Android 15/16 检查及固定签名 APK 作业。Release 实际存在 [Walk-Buddy-v0.4.18-arm64-debug.apk](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.18/Walk-Buddy-v0.4.18-arm64-debug.apk)，9,947,545 字节。发布后已实际下载核验包名、版本、模型和签名，记录见[发布附件核验](../验证结果.md#v0418-发布附件核验)；不代表 Find X8 Ultra、真实道路或 TalkBack 已验收。

## 本轮 CI 触发范围

此前每次 main 推送都会跑完整模拟器，包括没有 Android 改动的候选模型研究。现将仅包含 `docs/**`、`experiments/**`、`README.md` 或模型核验工作流的推送从 Android push 检查中排除；有任何其它文件变更仍触发，修改 UI 工作流本身也会触发。模型研究继续由专用 `Candidate model audit` 验证。

标签发布仍通过 `workflow_call` 调用原 Android 15/16 检查，`apk` 作业仍 `needs: ui`，必须通过后才打包和发布；未改发布依赖、检查项目、超时、签名或生产应用。此项调整减少无关运行，不解决已有窗口/触摸时序问题。

本轮提交 `c95082d93771ca261195055590afe097bcad799c` 修改了 UI 工作流本身，故仍触发完整模拟器检查。[41021191593](https://github.com/cyanrbt/walkbuddy/actions/runs/41021191593) attempt 1 成功，API 35 job `123948653586` 与 API 36 job `123948653341` 均首次成功；[研究检查 41021191647](https://github.com/cyanrbt/walkbuddy/actions/runs/41021191647) 的 ONNX 和 Open Images 两个作业也首次成功。本地两个隔离环境的 15 项工具测试、三个相关工作流的 actionlint、v0.4.18 发布元数据与生产模型摘要检查通过。应用源码与 v0.4.18 无差异，本次交付为 CI 和研究工具，不生成新应用版本；这些通过结果不构成此前输入/分析时序问题已修复的证据。
