# 2026-10-30 Actions 失败原因核对

本文件按历次核对保留 Actions 失败证据，下面的历史版本和“本次”“最新”“未发布”等措辞均对应各段记录时的状态；当前结果先看最上方核对。编译、模拟器检查、APK 发布与实体设备验收分别记录。

## v0.4.41 正式发布与一次声音开菜单失败

停止命令归属修复的[候选首轮](pip-stop-owner-first-status-2026-10-31.json)八作业成功；两核 Android 15 `124532261583` 先 `BUILD SUCCESSFUL in 52s`，后续声音检查在寻找第四次「关闭提示音」菜单项时失败。83,683,181 字节完整附件核对 SHA/CRC 并重放，新增清理前记录明确显示 LIVE、声音仍勾选、应用提交菜单正确，但只有三次按钮触摸，第四次尚未执行；小窗已移到中部，菜单窗口未显示。此次失败限定到开菜单阶段，不是该次静音命令被应用拒绝，移动及未展开原因仍未定位。[完整原始状态和实际截图](pip-stop-owner-2026-10-31.md#候选原生结果与保留的菜单失败)保留，不与旧第二次静音确认超时混为已证实的同一原因。

两套普通 UI 完整附件已重放，四次各八场景、共 32 次停止归属检查通过。仅上述失败作业单次同源码复验成功，Android 16 沿用首轮结果，没有再次执行；[最终候选记录](pip-stop-owner-final-status-2026-10-31.json)区分八个首次成功与一个单次复验。[正式 v0.4.41 41201310263](https://github.com/cyanrbt/walkbuddy/actions/runs/41201310263) attempt 1 的 Android 15、16 与 APK 三作业均成功；实际 10,000,447 字节 [APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.41/Walk-Buddy-v0.4.41-arm64-debug.apk)已完整下载核对 0.4.41 / 53、原固定签名、模型与服务端摘要，[正式证据](pip-stop-owner-release-2026-10-31.json)和[APK 记录](apk-v0.4.41.json)保留。正式 UI 和复验 ZIP 未本地重放，无实体设备验收，不称菜单或自然画面过期根因已修复。

## 小窗声音清理前采证补强（应用发布仍为 v0.4.40）

开发源码 `6e1b598` 为声音命令补充具体处理结果，并在异常清理前保存选择、令牌、菜单与截图；原十四项判断及顺序保持。273 项 JVM、57 项工具检查通过，四流程九作业均首次成功，无重跑。两套完整普通 UI ZIP 已实际核验并重放，四次声音交互共 68 条命令与 16 次菜单触摸可对应，四次明确标记的受控异常证明清理后文件仍保留原开启选择。[完整本轮结果与原始来源](pip-sound-diagnostics-2026-10-31.md)保留；专项仅核对终态和原日志，没有本地重放本轮轨迹。

旧的第二次静音等待超时本轮未重现，根因仍未定位；受控异常只验证采证路径，不冒充自然失败复现或修复。本轮为开发诊断补强，不覆盖 v0.4.40 标签和 APK，也不生成重复安装包，无实体设备验收。

## v0.4.40 发布后再次核对（2026-10-31）

再次直接查询最近 30 次 Actions，当前终态为 22 次成功、8 次失败，最前面的 14 次当前均成功；这个计数包含两次同源码单作业复验后的成功，不能称连续首次成功。[本次机器可读快照](ci-status-2026-10-31-v40.json)保留查询范围，并直接重读三份相关原始日志。

连续八个红叉来自两轮开发候选，每次推送各触发四个流程：`4837876` 在横屏两倍字号下等待文案纵向裁切；修订后的 `cef8575` 遗漏更新测试中的原 28dp 恢复样式基准。这是实际布局问题和测试维护遗漏，不是八次独立 APK 编译故障，也不能统一归因于模拟器。重读普通 API 35 原日志分别有 `BUILD SUCCESSFUL in 49s` 和 `BUILD SUCCESSFUL in 37s`，随后才是布局断言失败。两项已修订，新候选与正式标签通过；[原始失败与修订验证](preview-readiness-2026-10-31.md)保留。

较新的四核 API 35 作业 `124518183842` 先 `BUILD SUCCESSFUL in 55s`，后在 `pip-repeat` 的 `Timed out: second SystemUI mute` 失败。完整失败附件保留，单次同源码复验通过，但原因仍未定位；现有日志不足以区分触摸未交付、旧动作拒绝或其他原因，不能称系统问题或已修复。[完整失败](enlarged-viewfinder-sound-first-2026-10-31.json)和[最终候选状态](enlarged-viewfinder-final-status-2026-10-31.json)分别记录。

[v0.4.39 正式发布](https://github.com/cyanrbt/walkbuddy/actions/runs/41191378780)和最新 [v0.4.40 正式发布](https://github.com/cyanrbt/walkbuddy/actions/runs/41196091510)均为 attempt 1，Android 15、16 与 APK 三个作业全部成功。最新 Release 的 10,000,447 字节 [APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.40/Walk-Buddy-v0.4.40-arm64-debug.apk)已实际下载核对版本 0.4.40 / 52、包名、固定签名和服务端摘要；[正式日志记录](enlarged-viewfinder-release-2026-10-31.json)与[安装包核验](apk-v0.4.40.json)保留。历史红叉不删除，正式成功也不等同所有历史问题已解决；无实体设备验收。

## CPU 容量实验与一次输入复验（应用仍为 v0.4.38）

源码 `3cc077c` 保留默认两核检查，增加按实际运行机容量限制的最多四核专项。57 项工具检查通过；[四核专项 41180528007](https://github.com/cyanrbt/walkbuddy/actions/runs/41180528007)、[两核轨迹 41180528013](https://github.com/cyanrbt/walkbuddy/actions/runs/41180528013)和[三套像素 41180528061](https://github.com/cyanrbt/walkbuddy/actions/runs/41180528061)均首次成功。九套核数逐一核对，四核专项实际四核，其余七作业实际两核。四核两份完整附件与六条原始轨迹已实际核验重放；另外两核成功作业仅核对终态与原始日志，不冒充本轮完整 ZIP 重放。

[普通 UI 41180527990 首轮](https://github.com/cyanrbt/walkbuddy/actions/runs/41180527990/attempts/1) API 36 成功，API 35 的恢复点击失败。原图配套输入记录明确显示目标窗口 paused 时 DOWN 被拒；应用未收到 `recover-request`。快照 1201/95ms 属于探针主动制造的旧停止，不能把结尾“画面已过期”当作新自然停顿。该失败原始附件已完整核验并重放，只请求 API 35 单次同源码、同两核复验，十项原生检查和复验附件均通过。

当前四个流程终态成功，普通 UI 为 attempt 2，其余为 attempt 1；保留八个首轮成功与一个单次复验成功的区别。应用源码、签名和发布版本未变，不生成重复 APK。[完整原始来源、时序图、核数与复验记录](brightness-wait-2026-10-31.md)保留；旧 1436/1420ms 停顿和瞬时输入竞态仍未确认根因修复，不据四核通过宣称设备性能提升。

## Surface 前序采证修订（应用仍为已发布 v0.4.38）

源码 `1b52177` 的[普通 UI 41178081273](https://github.com/cyanrbt/walkbuddy/actions/runs/41178081273)和[系统轨迹 41178081292](https://github.com/cyanrbt/walkbuddy/actions/runs/41178081292)均首次终态成功，四个 Android 作业成功。51 项工具检查通过，四份完整附件与六条原始轨迹实际核验重放；系统轨迹第一轮现包含相机/像素专项及原有两次 APK 安装，再执行恢复。原采证首轮 `346e13e` 漏相机授权而失败，已补齐并保留两套原生权限错误。应用源码与发布版本不变，[完整本轮验收](surface-startup-trace-2026-10-31.md)保留全部来源。

此前 `2b71a39` 的首页失焦已核对原图、当前输入焦点与同设备事件顺序：Launcher 无响应弹窗覆盖首页，其 ANR 早于应用测试进程启动；不能归因于之后的相机专项，也不解释另一次 1436/1420ms 画面过期。新完整场景有一个 1134ms 首请求，仍需检查其中的原生/回调等待，不称历史停顿根因解决。

## v0.4.38 正式发布与独立失败

[正式标签 41172338591](https://github.com/cyanrbt/walkbuddy/actions/runs/41172338591) attempt 1 的 Android 15、16 与 APK 三作业均成功，实际 [v0.4.38 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.38/Walk-Buddy-v0.4.38-arm64-debug.apk) 已完整下载核对版本 0.4.38 / 50、原固定签名、模型与摘要；两份正式 UI 附件核验重放通过。[正式证据](brightness-preview-budget-2026-10-31.md#正式标签与开发复验分别核对)保留。

不同开发运行仍有真实失败：主分支 API 35 一次采集/显示 1436/1420ms 过期；API 26 独立冻结夹具通知交错，后续显式排空修订的三套冻结子检查通过，但 API 35 另有首页焦点与设备交互前提失败，整轮仍失败。[逐项边界](camera-explicit-drain-2026-10-31.md)与[原作业状态](v038-publication-status-2026-10-31.json)保留；不把正式成功等同于所有开发失败已解决，也不扩大 1200ms 截止来改写结果。

## v0.4.38 源码合入验收

已核对旧候选 API 26 的 ready/采集交错并修正测试初始化顺序；也发现未发布的 1000ms 全请求停机与公开版取样跳过语义不同，现将新增亮度有界等待与 1200ms 画面时效对齐，原模型时效保持。修订源码 `1759d0b` 的三套像素与两套系统轨迹均首次成功，五份附件和六条原始轨迹已实际核验重放；259 项 JVM 通过。[完整策略、原始失败与验收](brightness-preview-budget-2026-10-31.md)保留。当前进入 v0.4.38 源码，正式标签构建和实际 APK 仍需独立确认，不能提前称为发布成功。

## 最新运行机存储与实验失败核对

main `3184e10` 的普通 UI 首次成功，系统轨迹 API 36 则在编译成功后因空闲 7169.88 MB、小于需要的 7372.80 MB 而无法启动模拟器；应用探针尚未运行。修订 `b714c9f` 的普通 UI 与系统轨迹均首次成功，四套清理、模拟器启动、各十项检查及六条完整轨迹已实际下载核验与重放。实验分支的 Android 8 Camera2 停止等待连续两次失败，与磁盘故障不同，未发布 v0.4.38。正式 v0.4.37 的两套 UI、APK 和 Release 附件仍确认成功。[本次原始来源、43 项工具测试、清理结果与验证边界](ci-storage-2026-10-31.md)保留；本轮清理前空闲已多于旧失败轮，不称为旧低空间边界的受控复现。

## 输入焦点解析与撤回复验

源码 `066e224` 的系统轨迹和像素专项首次成功；普通 UI 的 Android 15 首轮出现唤醒后任务仍 sleeping、输入无焦点的失败，原提交失败作业复验后通过，Android 16 首次成功保留。七份成功原始 ZIP 和六条轨迹已实际下载核验并重放；首轮黑屏失败与精确根因未定的事实保留。[本轮核对](ci-input-focus-2026-10-31.md)区分该次黑屏、新实验中有原图和 InputDispatcher 焦点证据的 Launcher ANR，以及另一次 adb 传输离线。

现在 CI 摘要补充解析 `FocusedWindows` 当前区段，38 项工具测试通过，旧失败保持。原采证文件第二次只读复制前有界等待恢复连接，INFO 阶段记录进入后备文本；不改变原生检查、截止、最多两次同文件复制或摘要校验，不自动关闭系统弹窗。新工具推送后的云端结果仍待取得；v0.4.38 未发布，现有安装包仍为 v0.4.37。


## 本次工作中新增的候选失败

前述查询之后，新异步亮度候选的普通 UI 和像素专项首次成功，但[系统轨迹 41160797255](https://github.com/cyanrbt/walkbuddy/actions/runs/41160797255) 的 API 35 第二轮实际恢复失败：进入小窗前已提示读回失败并停机，触摸准备随后超时。编译成功不是整轮通过；该次失败与早期 Perfetto 缓冲/flush/传输问题不同。七份原始 ZIP 和六条 trace 均实际下载复核，应用候选已撤回主分支且未发布 v0.4.38，完整范围见[本次失败记录](brightness-async-2026-10-31.md)。当前稳定下载仍为 v0.4.37。

## v0.4.37 后再次核对

用户询问“GitHub 打包一直在失败”后，重新查询最近 30 次 Actions、最新 Release 和最近三个红叉的作业步骤，直接重读四份原始 job 日志。查询时最近六次运行均 attempt 1 终态成功，最新两个为 [Surface 专项 41158068308](https://github.com/cyanrbt/walkbuddy/actions/runs/41158068308)及[普通 UI 41158068207](https://github.com/cyanrbt/walkbuddy/actions/runs/41158068207)。[机器可读状态](ci-status-2026-10-31-v37.json)保留来源，不把此历史快照称为永久状态。

最近三个红叉来自 `Preview system trace`：41150469510 的环形缓冲覆盖早期记录；41151682335 的流式配置未定期 flush；41152965922 的 API 35 第一次文件传输失败、API 36 第一次内核事件丢失。直接重读日志确认编译均 `BUILD SUCCESSFUL`，失败属于轨迹完整性；具体原始轨迹与边界见[专门记录](preview-system-trace-2026-10-31.md)。这些是实际失败，包括实现配置错误，不能全部归因于模拟器不稳定。

修订后 [41154316569](https://github.com/cyanrbt/walkbuddy/actions/runs/41154316569) 已首次成功，旧红叉保留。最新 [v0.4.37 发布 41147755325](https://github.com/cyanrbt/walkbuddy/actions/runs/41147755325) 的 Android 15、16 与 APK 三个作业均成功，Release 再次确认存在 9,980,497 字节的 [APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.37/Walk-Buddy-v0.4.37-arm64-debug.apk)，摘要与此前实际下载核验相同。未因此另发重复版本或覆盖历史标签；原来偶发 1246ms 停顿根因仍未确定，后续优化需独立验收。



## 预览系统诊断工具核对（应用仍为 v0.4.37）

源码 `355e004fef713c514f3990b6d9ef071b80df6f77` 的[专项系统轨迹 41154316569](https://github.com/cyanrbt/walkbuddy/actions/runs/41154316569)和[常规 UI 41154316656](https://github.com/cyanrbt/walkbuddy/actions/runs/41154316656)均 attempt 1 终态成功。四个完整附件下载核对长度、SHA-256 和 ZIP CRC；六条实际原始轨迹经固定版官方 SQL 引擎本地重读，原有十项报告重放一致。31 项工具测试通过；[完整结果与原始来源](preview-system-trace-2026-10-31.md)区分工具、模拟器和实体范围。

此前新专项流程的三个红叉均保留：[41150469510](https://github.com/cyanrbt/walkbuddy/actions/runs/41150469510) 是中央环形缓冲覆盖早期事件；[41151682335](https://github.com/cyanrbt/walkbuddy/actions/runs/41151682335) 是流式模式未设置数据源提交周期；[41152965922](https://github.com/cyanrbt/walkbuddy/actions/runs/41152965922) 是 API 35 一次轨迹传输失败，以及 API 36 CPU 1 丢失 5,350 个内核事件。相应同源码常规 UI 均通过，各轮编译与受采证恢复探针也通过；专项完整性失败仍令整轮失败，没有删检查、删除旧记录或重跑来消除红叉。修订包含周期写盘/提交、内核缓冲与读取周期，以及同一个停止文件的有界传输和摘要校验，保留每次传输结果，不重跑探针更换样本。

生产源码与发布依赖均与 v0.4.37 相同，现有正式 APK 发布成功状态不变；未生成重复应用版本。新六条轨迹本轮未见未标记的超时停止，不能据此称历史 1246ms 停顿根因已解决。没有新的实体设备验收。

## v0.4.37 正式发布核对

亮度/读回改进的[基线 41145407736](https://github.com/cyanrbt/walkbuddy/actions/runs/41145407736)、[候选 41146872248](https://github.com/cyanrbt/walkbuddy/actions/runs/41146872248)和[正式发布 41147755325](https://github.com/cyanrbt/walkbuddy/actions/runs/41147755325)均已取得终态成功，原始附件分别核对摘要并重放。正式 APK 已实际下载核验版本、固定签名、摘要、模型和新实现，具体 attempt 与来源见[本轮记录](brightness-readback-2026-10-31.md)。四轮受控位图计时不代表真实 GPU/相机性能，新增墙钟/CPU 记录不证明之前自然停顿根因修复，历史失败继续保留。

## v0.4.36 正式发布核对

[正式流程 41143546103](https://github.com/cyanrbt/walkbuddy/actions/runs/41143546103) 的两套 Android、固定签名 APK 与整体终态均成功，原始附件和实际 APK 均已下载核对摘要。版本 0.4.36 / 48、固定签名与恢复实现已核验；[完整来源与结果](recovery-presentation-2026-10-31.md#正式发布与实际安装包)保留。新配置将临时模拟器用户分区限定为 2048M；旧报告覆盖问题已复现修正，原始候选失败和自然 1246ms 过期重验不删除，不将成功等同于全部瞬时故障根因已解决。

## v0.4.36 开发验证

[首轮 41139401074](https://github.com/cyanrbt/walkbuddy/actions/runs/41139401074)两套 APK 编译成功后失败：API 35 的新恢复检查发现旧首页缓存覆盖停止报告；API 36 模拟器因可用 7202.88 MB、需要 7372.80 MB 的用户分区而启动失败，十项应用检查均未开始。已在 JVM 复现并修正报告缓存；模拟器用户分区改为 2048M，保持全部检查与发布依赖。

[报告修订 41140406598](https://github.com/cyanrbt/walkbuddy/actions/runs/41140406598)两套实际恢复均通过，API 35 十项成功，API 36 support 的默认振动断言失败，原始消息未区分 enabled/checked。补充实际控件渲染就绪条件及前后状态采证，原断言保留。候选尚待完整验收，不提前创建发布标签；[本轮完整证据与因果边界](recovery-presentation-2026-10-31.md)保留，未重跑首轮消除红叉。

## 2026-10-31 06:31 核对快照（北京时间）

用户再次询问“GitHub 打包一直失败”后，直接查询最近 40 次 Actions、最新 Release 和三个相关流程的作业步骤，并重读最近失败候选的两套完整日志及正式 APK 作业日志。[本次状态、作业与原始日志摘录](ci-status-2026-10-31-0631.json)保留查询范围和时间，下面为该时点状态：

- 最近 40 次运行的当前结果为 35 次成功、4 次失败、1 次进行中；包含重跑后成功的历史任务，不作连续首次成功计数。
- 最近保留红叉的[开发候选 41131327523](https://github.com/cyanrbt/walkbuddy/actions/runs/41131327523)两套均先 `BUILD SUCCESSFUL`，再出现 `Toggle label wrapped: 600x360-font150-day` 和系统小窗菜单操作超时。前者是本次实际布局回归；已按完整文字宽度调整侧栏。首轮菜单没有逐次触摸轨迹，不能把每次超时归因于相同原因，也不能统一归因于模拟器。
- [修订候选 41133558071](https://github.com/cyanrbt/walkbuddy/actions/runs/41133558071)两套 API 均 attempt 1 成功；[正式 v0.4.35 41134864899](https://github.com/cyanrbt/walkbuddy/actions/runs/41134864899)的两套 UI、APK 和整体终态均 attempt 1 成功。直接重读 APK 日志确认 `BUILD SUCCESSFUL in 1m 5s`、固定签名检查及发布步骤成功；Release 实际有 9,978,729 字节的 [ARM64 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.35/Walk-Buddy-v0.4.35-arm64-debug.apk)，服务端摘要与[此前完整下载核验](apk-v0.4.35.json)相符。
- 最新[既有恢复界面基线观测 41137921559](https://github.com/cyanrbt/walkbuddy/actions/runs/41137921559)当时仍在运行，API 36 已成功、API 35 编译成功且在模拟器检查；不能提前写为成功或失败。运行中暂时取不到归档日志不构成构建失败。

随后再次查询，基线观测 `41137921559` 于北京时间 06:31:39 终态成功，两套 Android 作业均成功，仍为 attempt 1。JSON 的 `terminal_follow_up` 保留后续查询；上面的 06:31 快照不回写为当时已完成。本次仅核对终态，不冒充已完成新基线附件重放或新版本发布。

`Release APK` 保留 `apk.needs: ui`：标签发布必须先通过 Android 15/16 检查，前置失败会跳过打包。main 上的 `Android UI checks` 本身是开发检查；Actions 列表同时保留这些候选红叉和后续成功的正式发布，因此应点开具体流程与失败步骤判断。已有布局和输入/窗口问题分别记录；没有用重跑抹去红叉，也不以当前成功宣称旧瞬时故障已全部定位。模拟器及发布验证不替代实体设备验收。

## v0.4.35 发布核对

[首轮 41131327523](https://github.com/cyanrbt/walkbuddy/actions/runs/41131327523) attempt 1 两套编译成功后各有三项失败：大字体横屏「提示音」换行，以及两轮系统菜单触摸未完成。原图确认固定侧栏宽度不够，是本次真实布局回归；菜单首轮缺少逐步轨迹，不能认定每次触摸的精确原因。完整失败附件已下载核对摘要，API 35 另有外部系统服务 ANR，与两套共同的换行问题分开记录。

按实际标签宽度适配侧栏、补充系统菜单屏幕边界与新鲜稳定目标之后，[修订 41133558071](https://github.com/cyanrbt/walkbuddy/actions/runs/41133558071) attempt 1 两套各十项完整成功，ZIP 均下载匹配摘要并重放，原样菜单及横屏图已查看。实际四次触摸、旧按钮拒绝与音频失败回退通过；首轮未重跑。证据与因果边界见[本轮记录](pip-sound-controls-2026-10-31.md)。

[正式 v0.4.35 41134864899](https://github.com/cyanrbt/walkbuddy/actions/runs/41134864899) attempt 1 两套十项、APK 作业与整体终态均成功，原始附件和实际 [APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.35/Walk-Buddy-v0.4.35-arm64-debug.apk) 均已完整下载，核对版本 0.4.35 / 47、固定签名、服务端摘要和新实现。API 35 外部 `com.google.android.as` onStartJob ANR 单独保留，无本应用 ANR 或聚焦外部弹窗观察；正式流程未重跑，不以发布成功消除旧红叉或推断全部输入根因已解决。

## v0.4.34 发布核对

首页事件同步改进的[首轮候选 41125243534](https://github.com/cyanrbt/walkbuddy/actions/runs/41125243534) attempt 1 失败：两套编译均成功，API 35 的首页安静期及两套的小窗恢复提示可见性检查失败。真实小窗的零重复访问查询、连续采集/显示、通知合并和回退检查通过，不能将部分通过写成整轮成功。两个完整失败附件均已下载核对摘要，原图及证据边界见[本轮记录](home-events-2026-10-31.md#首轮云端失败与采证修订)。

生产代码未变的[采证修订候选 41126691713](https://github.com/cyanrbt/walkbuddy/actions/runs/41126691713) attempt 1 两套十项全部成功，终态与原始日志已核对，完整附件已下载匹配摘要并重放。修订增加聚焦、实际小窗尺寸/布局以及缩小后新帧的测量前提，原严格断言保留；记录确认部分原等待点根视图尚未缩小，但不称首轮瞬时失败根因已完全解决。首轮未重跑；下方连续成功计数仅属于 04:34 那次历史快照。

[正式标签 41127878227](https://github.com/cyanrbt/walkbuddy/actions/runs/41127878227) attempt 1 的 Android 15/16、APK 三个作业及整轮终态成功，原始标签附件均下载核对摘要并重放通过。实际 [v0.4.34 APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.34/Walk-Buddy-v0.4.34-arm64-debug.apk) 已完整下载核验 0.4.34 / 46、固定签名与服务端摘要。API 36 另有外部 `com.google.android.as` onStartJob ANR 观察，没有本应用 ANR 或聚焦外部弹窗观察；[正式证据与设备边界](home-events-2026-10-31.md#正式标签与实际安装包)分别保留，不能归作首轮失败的根因。

## 2026-10-31 04:34 核对快照（北京时间）

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
