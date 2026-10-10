# Walk Buddy

走路时把后置摄像头留在一个小窗里。窗口可以拖走、改大小，切到别的应用也还在。画面给你自己看。

这是一个 Android 学习原型，当前版本 **0.2.4**。可选的本地识别只是说「画面里可能有人、车或路边的椅子」，不能替你判断前面能不能走。

## 打开之后

按顺序允许摄像头、悬浮窗和通知，再点「开启观察窗口」。

- 小窗贴在其他应用上面。按住顶栏拖动，右下角调整大小。
- 浮窗里或通知里都能停止。锁屏会关掉摄像头。
- 辅助识别和提示音每次打开都是关着的，要看检测时再在浮窗里勾选。

识别在手机上完成，只看这八类：人、自行车、汽车、摩托车、公交车、卡车、椅子、长凳。连续两次看到同一类，才会短震一下。它不量距离，也不管台阶、坑洞和玻璃。没有提醒，只说明这次没有出声。

画面不保存，也不上传。安装包没有网络权限。

## 适合的场合

白天、平路、你本来就会看周围的时候，拿来多留一个画面。先站着确认镜头对着你想看的方向。自然拿着手机时，后置镜头经常朝向地面。

不要在马路、楼梯或骑行时靠它决定下一步。

## 下载

推送 `v` 开头的标签后，GitHub Actions 会构建 arm64 调试包并挂到 [Releases](https://github.com/cyanrbt/walkbuddy/releases)。当前版本的文件名是 `Walk-Buddy-v0.2.4-arm64-debug.apk`，只含 arm64-v8a，最低 Android 8。

装好后按首页允许摄像头、悬浮窗和通知，再自己点开启。系统若拒绝覆盖安装，先卸载旧包：调试签名变了就不能直接升级。

想让以后每次发布都用同一把调试签名，把用密码 `android`、别名 `androiddebugkey` 生成的密钥库做一次 base64，存成仓库密钥 `DEBUG_KEYSTORE_BASE64`。没有这把密钥时，Actions 会把第一次生成的调试密钥库放进构建缓存。

## 构建

需要 JDK 17，以及 Android SDK 36（platforms、build-tools 36.0.0、platform-tools）。在项目根写下 `local.properties`：

```
sdk.dir=/绝对路径/android-sdk
```

```sh
python3 scripts/check_model.py
./gradlew assembleDebug testDebugUnitTest lintDebug
```

调试包在 `app/build/outputs/apk/debug/app-debug.apk`。默认只有 arm64-v8a，最低 Android 8。四 ABI 包用 `./gradlew assembleDebug -PuniversalApk`。

固定组合是 Java 17、Gradle 8.13、Android Gradle Plugin 8.10.1、LiteRT 1.4.0。第一次构建会下载依赖。应用运行时不联网。

## 文档

行为说明在 [方案](docs/方案.md)。预览方向见 [预览修复](docs/预览修复.md)。验证记录、云端构建阻塞、实机空白表和许可证分别在 [验证结果](docs/验证结果.md)、[云迁移验证](docs/云迁移验证.md)、[实机测试](docs/实机测试.md)、[许可证](docs/许可证.md)。

0.2.3 把显示名改成 Walk Buddy。0.2.4 重画了启动图标，并收起了悬浮窗的控件。识别与相机逻辑沿用 0.2.1。模型文件和 26 项纯 Java 单测已在当前环境复核。本机若没有 JDK 和 Android SDK，仍然不能在本地打包；打上 `v*` 标签后由 GitHub Actions 构建。真实摄像头烟测曾因采集时间戳过期未通过。握持视野、延迟、耗电和识别效果还没有实机记录。

源码：https://github.com/cyanrbt/walkbuddy
