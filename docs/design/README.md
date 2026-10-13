# WalkBuddy 视觉更新

2026-10-13。保留实时取景、系统画中画、识别及空间增强的行为，更新应用图标、首页、预览页和通用组件。

## 视觉方向

参考 [WorkBuddy 官网](https://www.workbuddy.cn/) 与其[官方品牌图](https://codebuddy-1328495429.cos.accelerate.myqcloud.com/web/workbuddy/39688f38cefae008f3e7bcee09ad0ebea11fef28/assets/workbuddy-logo-WhgOvEF7.png) 的简洁、友好角色风格。WalkBuddy 使用原创相机小伙伴：镜头眼睛表达观察，迈步双脚对应步行。它不表示腾讯官方产品或合作关系。

- 主色：深绿 `#087B63`；点缀：薄荷绿 `#4DE4AC`。
- 浅底：`#F2F7F4`；卡片：白色；正文：`#183B32`。
- 取景界面：深墨绿 `#102C28`，降低画面周围干扰。
- 灰、黄、红继续表示原有检测状态，品牌绿色不表示道路安全。
- 卡片圆角 22–28dp，按钮 16–18dp，常用控件至少 52dp 高，文字支持换行。

首页依次展示品牌、观察入口、权限准备和使用指南。当前状态和打开预览按钮组成同一张操作卡片，停止作为次级动作。预览页使用同一角色、配色和组件，开关保留原生勾选及开启/关闭文字。控制区高度有上限并可滚动。

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
