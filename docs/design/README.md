# WalkBuddy 视觉更新

2026-10-13。保留实时取景、系统画中画、识别及空间增强的行为，更新应用图标、首页、预览页和通用组件。

## 视觉方向

参考 [WorkBuddy 官网](https://www.workbuddy.cn/) 与其[官方品牌图](https://codebuddy-1328495429.cos.accelerate.myqcloud.com/web/workbuddy/39688f38cefae008f3e7bcee09ad0ebea11fef28/assets/workbuddy-logo-WhgOvEF7.png) 的简洁、友好角色风格。WalkBuddy 使用原创相机小伙伴：镜头眼睛表达观察，迈步双脚对应步行。它不表示腾讯官方产品或合作关系。

- 主色：深绿 `#087B63`；点缀：薄荷绿 `#4DE4AC`。
- 浅底：`#F2F7F4`；卡片：白色；正文：`#183B32`。
- 取景界面：深墨绿 `#102C28`，降低画面周围干扰。
- 灰、黄、红继续表示原有检测状态，品牌绿色不表示道路安全。
- 卡片圆角 22–28dp，按钮 16–18dp，常用控件至少 52dp 高，文字支持换行。
- 跟随系统深色时，首页用 `values-night` 的暗底/卡片/正文色；取景页保持墨绿，不随主题变化。

首页依次展示品牌、观察入口、权限准备和使用指南。当前状态和打开预览按钮组成同一张操作卡片，停止作为次级动作。预览页使用同一角色、配色和组件，开关保留原生勾选及开启/关闭文字。控制区高度有上限并可滚动。

## 首次体验迭代（2026-10-29，工作区）

参考 [Google Lookout 的取景与开始说明](https://support.google.com/accessibility/android/answer/9031274?hl=en)，把摄像头授权、看到实时画面和进入小窗分开反馈；保留站着确认镜头朝前的提示。[Android 画中画文档](https://developer.android.com/develop/ui/views/picture-in-picture)提供模式变化回调，本次在系统确认进入小窗后记录体验，不再把画中画可用等同于用户已经用过。

- 首页三步用勾选、数字和可跳过标记区分状态；步骤上下排列图形与文字，字号和徽章随系统字体放大。
- 实时预览和小窗历史采用新的独立记录；旧的 `preview_opened` 只代表尝试打开，不迁移为成功记录。
- 主按钮明确显示「允许摄像头并预览」，仅这次请求授权成功后接着打开预览；准备区的单独授权不启动相机。拒绝/取消留在首页，从系统设置返回也需主动点击打开。
- 无法再弹出授权框时，主按钮明确指向应用设置。小窗关闭时操作卡片直接展示设置按钮；设备不支持时标为可跳过，不阻止应用内观察。
- 首页权限与进度只在输入状态变化时更新文字/背景，减少原来每半秒重复设置视图。说明区展开状态在 Activity 重建后保留。

本次验证及设备限制见[验证结果](../验证结果.md)。此处记录工作区迭代，不代表已经发布新版本。

## 0.4.5 预览绘制优化

检测合并结果在新数据或过期边界发生变化时更新；轮廓路径、标签宽度和绘图资源在数据/画面几何改变时准备，重复绘制复用结果。四步引导和高频状态文字也只在展示内容变化时更新。参考 [Android 自定义 View 优化](https://developer.android.com/develop/ui/views/layout/custom-views/optimizing-view)减少绘制中的对象分配与无效重绘。

黄色深度轮廓与基础角框统一使用虚线；遮罩边界先合并连续直线，再应用虚线，保留孔洞与离散区域。灰色、红色使用实线，红色使用较粗线宽。绘图缓存会在检测清空和几何变化时失效，不改变基础/深度结果各自的过期时间。

## 0.4.6 窗口自适应预览

参考 [Android 媒体应用的自适应布局](https://developer.android.com/media/implement/surfaces/large_screens)，以实际窗口可用尺寸安排相机和设置，不依赖物理屏幕高度。`PreviewContentLayout` 在宽度至少 600dp 且横向空间更充足时使用左右分栏，右侧面板宽 240–360dp；窄屏上下排列，设置最多占剩余内容区域的一半。相机、完整设置面板一起参与测量，图例、状态和引导不再独立挤占取景高度。

页头保留停止操作；页底只放当前主要操作：实时预览时进入小窗，故障/停止时重新开启。滚动面板把常用开关置前，再展示增强引导与说明。PiP 隐藏整个设置面板和固定操作，精简深度状态叠加在画面底部；故障遮挡层仍位于最上方。

新增 `LayoutProbe` 在真实 Android 资源/控件中测量并渲染 320×480、360×640、640×360、800×600 四种配置，分别检查 100%/200% 字体和深浅色。截图明确显示“布局测试”占位，不作为实际相机效果图。发布流程在模拟器布局及软件 Canvas 检查通过后才构建正式发布 APK。

[云端验证](https://github.com/cyanrbt/walkbuddy/actions/runs/40854253928)的 16 组布局与轮廓像素检查已通过。代表截图：[320×480、200% 字体](v0.4.6/small-font200.png)、[640×360 分栏](v0.4.6/wide.png)。截图为 Android 控件的软件 Canvas 输出，画面区域是明确标注的测试占位。

## 0.4.7 最近提醒与恢复引导

依据 [Android 16 对无障碍 announcement 的调整](https://developer.android.com/about/versions/16/behavior-changes-all#disruptive-a11y)，把风险提醒迁移到实际可见的礼貌 live region。检测详情不再同时作为自动播报源；画面底部“最近提醒”只显示简短风险文字，在应用提醒策略允许时更新其完整无障碍描述。详情仍能通过焦点读取最新位置/距离，最近提醒明确表示较早一次提示，避免把低频内容冒充实时测量。相同内容不重复提交；过期、降级与停止会隐藏旧提醒。

恢复页把短标题放在不透明遮挡层，具体操作说明放在滚动面板首部，固定主要按钮指向设置、重新开启、基础预览或返回首页。动作由明确的故障分类选择，不解析错误文案。等待安装/切换时禁用重复恢复；从设置返回后仍需主动开启。这里的语义与策略检查不能证明 TalkBack 实际朗读顺序或设备效果。

[Android 15/16 检查](https://github.com/cyanrbt/walkbuddy/actions/runs/40859349638)均通过，代表截图：[小屏 200% 字体的简短提醒](v0.4.7/recent-alert.png)、[横屏故障恢复](v0.4.7/recovery-wide.png)。完整提醒保留在无障碍描述中，停止状态清除旧检测详情并禁用检测/声音控件。

## 0.4.8 控件状态与画中画恢复

空间模式切换只在实时预览时可用；启动、停止和故障页禁用该按钮。主按钮的背景增加原生 disabled 状态，等待时显示灰暗薄荷色，保留文字与圆角。停止说明指向当前页面的恢复操作。完整预览恢复时根据系统实际 PiP 状态校正控件可见性，避免切换中断后仍显示精简界面。

布局探针除检查按钮 enabled 标记，还比较原生 drawable 在启用/禁用状态下的绘制结果；恢复探针检查停止页不能开启模式切换。系统 PiP 与 Camera2 的实际生命周期由独立模拟器探针覆盖，范围与设备限制见[实机测试](../实机测试.md)。

[Android 15/16 云端检查](https://github.com/cyanrbt/walkbuddy/actions/runs/40864777367)均通过。[Android 16 系统 PiP 截图](v0.4.8/pip-camera-api36.png)展示实际小窗和虚拟 Camera2 场景，与前两版的布局占位图不同；仍不代表实体相机效果。

## 0.4.10 首页固定操作与对比度

参考 [Google Lookout 的无障碍设计实践](https://design.google/library/google-lookout-design-accessibility)对清晰视觉、熟悉操作和减少查找负担的重视，结合 [Android 无障碍控件建议](https://developer.android.com/guide/topics/ui/accessibility/apps)，将首页主要操作与长说明分开。底部保持授权/打开/返回预览，运行中增加停止；简短状态说明与操作放在一起，正文允许完整滚动。顶部品牌介绍收紧，详细用途仍能展开查看。

夜间强调文字从深薄荷绿改为明亮薄荷绿，按钮背景独立保留。首页普通文字按至少 4.5:1 检查，操作触达区域至少 48dp。无预览会话时不再每半秒刷新首页，进入前台和重新获得焦点仍会同步权限与小窗状态。

原生布局探针按 320×480、360×640、640×360、800×600 与深浅色、100%/200% 字体测量，覆盖七种操作状态和详细说明展开后的滚动可达性。截图标注“布局检查”，不代表实时相机。实际小窗展开和首页停止由独立 Camera2/PiP 探针检查，TalkBack 朗读与 OEM 小窗遮挡仍待实体设备确认。

[Android 15/16 原生检查](https://github.com/cyanrbt/walkbuddy/actions/runs/40887205991)最终通过；代表截图为布局验证：[小屏 200% 字体](v0.4.10/home-font200.png)、[夜间](v0.4.10/home-night.png)、[横屏](v0.4.10/home-wide.png)。各操作文字允许完整换行，底部操作不随正文滚动。首轮大字体徽标裁切及重复识别超时记录见[验证结果](../验证结果.md)。

## 0.4.12 首页阅读顺序与文字空间

参考 [Lookout 对清晰视觉和可学习体验的设计说明](https://design.google/library/google-lookout-design-accessibility)与 [Android Views 无障碍建议](https://developer.android.com/guide/topics/ui/accessibility/views/apps-views)，减少顶部重复营销文字，保留用途和品牌；按实际文字宽度让装饰图让出空间，用户选择的字号保持不变。首次当前说明放在步骤之前，完成后突出当前观察状态；限制变化时重新引导。

宽度至少 600dp 时品牌与状态/引导并排，让横屏短窗口保留状态空间；窄窗口纵向展示。原生布局检查包含 100%/150%/200% 字体与深浅色，另在同一品牌行上测 180px→500px→原窗口，显式核对大字体的装饰隐藏、恢复与字号保持。截图在初始视口生成，状态标注“布局检查”；仍需检查实际图片与实体设备。按钮触达面积和夜间对比度沿用既有标准。

## 图标资源

- `app/src/main/res/drawable-nodpi/walkbuddy_mascot.png`：内置 imagegen 生成的 1254 × 1254 透明 PNG，首页和预览页共用。
- `walkbuddy_foreground.xml`：PNG 前景，增加自适应图标安全边距。
- `walkbuddy_background.xml`：深绿到薄荷绿的渐变背景。
- `walkbuddy_mono.xml`：与新角色相对应的简化单色矢量，用于 Android 主题图标。
- `mipmap-anydpi-v26/ic_launcher*.xml`：普通与圆形桌面图标接入。

原有未提交的 `ic_launcher_mark.xml` 和 `ic_launcher_mono.xml` 文件保留，新资源独立命名。生成使用内置 imagegen，没有调用 CLI 或付费 API 脚本。

## 最终生成提示词

```text
Use case: logo-brand. Create a finished original Android app mascot icon for WalkBuddy, a walking camera companion with picture-in-picture observation and obstacle awareness. Similar visual spirit to Tencent WorkBuddy: extremely simple friendly white geometric buddy, mint/teal accents, clean soft rounded forms. Distinct original character: a small rounded white camera-bodied walking buddy, two tiny feet in a gentle walking stride, two eyes with one eye a bold teal camera lens ring, short rounded ear-like top corners. Front view, confident friendly expression. Flat vector-like graphic, clean bold silhouette, very few details, no 3D, no texture, no text, no wordmark, no background tile, no drop shadow. Single centered mascot fully visible, occupying central 62 percent of a square canvas with generous transparent margin for Android adaptive-icon masks. White body #F5FAF7, near-black green eyes #102C28, mint accent #4DE4AC and dark teal lens #087B63. Real transparent background. Designed to remain legible at 48px.
```

## 验证

构建命令：`./gradlew testDebugUnitTest lintDebug assembleDebug --offline --no-daemon --no-watch-fs`。

结果：53 项单元测试通过；Lint 无错误；调试 APK 构建成功。`python3 scripts/check_product_surface.py` 和 `git diff --check` 均通过。

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`，包名不变，发布版本为 0.4.3 / 15，签名 SHA-256 为 `ffdf47c306d335d2ee3bc39256d43c470fda5572e80662155ed31dc1c16aae0d`，与当前发布证书一致。通过 `v0.4.3` 标签触发 GitHub Actions 构建与 Release 发布。

检查覆盖界面资源及画中画权限约束。未连接 Android 设备，未进行真机截图和交互验收；小屏、大字体、横屏、圆形图标裁切和画中画切换仍需设备确认。
