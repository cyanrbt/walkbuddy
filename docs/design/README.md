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
