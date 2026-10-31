# 冻结相机夹具显式排空

v0.4.38 正式发布流程与 APK 已成功，固定签名、版本、模型和文件摘要已经实际下载核验。本文件处理一个独立测试夹具问题；生产 `app/src/main`、版本、包名、签名和历史标签不改。

主分支发布源码 `a4bbb53` 的[像素专项 41172338642](https://github.com/cyanrbt/walkbuddy/actions/runs/41172338642)中，API 36 成功，API 26、35 失败。API 26 原始 ZIP `13031446586`，129,922 字节，SHA-256 `32b279d330346454531f02188d74c18e0c3393e0137bb637d2ae7aebad9c5946`，实际下载匹配 CRC；[原始事件](camera-drain-first-2026-10-31.json)保留，没有重新运行此旧源码失败作业。

API 35 的独立 Camera2/EGL 四组像素与 32 组冻结对照均成功，后续标准 `pip_recovery` 却在初次进入小窗的触摸准备处失败，应用已因画面过期停止；这是实际运行中的另一项失败，不能归因于 API 26 冻结夹具，也不称本次 drain 修改解决了它。正式标签两套 UI 与 APK 均首次成功，主分支另外两套普通 UI 和两套系统轨迹也成功；这些不同运行各自记录，部分成功不能覆盖原失败。

初始 ready 虽已在启动前到达，约 30ms 后仍有另一个 ready 夹在 active 与第一帧完成之间；随后继续收到捕获结果，stopRepeating 的序列完成后五秒没有新的 ready。因此前一修订不能排除全部交错，不将它描述为底层通知问题已解决，也不能将旧 ready 为 true 当作源已冻结。

夹具现在先明确 stopRepeating，保留原五秒重复序列完成等待，再调用公开的 `abortCaptures()` 排空剩余采集工作，并保留五秒新 ready 等待。ready 只有在 drain 阶段开始后才满足最终门槛；没有增加任意睡眠、重启相机或放宽像素、源时间戳、产品 1200ms 截止。[官方接口](https://developer.android.com/reference/android/hardware/camera2/CameraCaptureSession#abortCaptures())规定排空后通知 onReady。已经完成的重复序列不被替换成另一个更容易比较的图像源。

这是更明确的测试冻结前提，不是生产相机实现的修复。应用和原生测试包编译 26 秒成功，`git diff v0.4.38 -- app/src/main app/build.gradle` 为空。旧结果继续按各自源码保留，实体设备未验收。

## 显式排空后的实际结果

修订 `2b71a395b54a72deeda17449bb532789c7979fa5` 的[41173728712](https://github.com/cyanrbt/walkbuddy/actions/runs/41173728712)中，API 26、36 整轮首次成功；API 35 的 32 组冻结与 Camera2/EGL 四组子检查成功，但后续标准检查失败，不能写成整轮通过。同源码[普通 UI 41173728725](https://github.com/cyanrbt/walkbuddy/actions/runs/41173728725)两套首次成功。

API 26 原始 ZIP `13032202750`，130,380 字节 / SHA-256 `732107166e116d0197d1b3dd6b231ce072d6eb3f9f7adb8a7331518f64e99aed`，实际完整下载并核对 CRC；明确观察到 ABORT_DRAIN 之后新的 READY，四组像素/时间戳完全符合门槛。[原始记录](camera-explicit-drain-api26-2026-10-31.json)保留。API 35/36 本次重读作业日志确认生产者通过标记，没有将其称为另两份 ZIP 已在本地完整核验。

API 35 的不同失败为首次首页触摸准备时 focus=false，随后首页焦点等待失败、部分小窗检查 interactive=false；首个首页入口尚未打开预览，不能归因于亮度复制或以此前 1436ms 过期原因替代。没有失败时窗口原图的本地复核，不能认定具体系统弹窗或精确根因。全部实际作业及原始失败行见[状态记录](v038-publication-status-2026-10-31.json)。本次结论仅覆盖三套云端冻结子检查、实际下载的 API 26 新 ready 和生产源码未变；其它失败继续保留。
