# Walk Buddy

白天步行时，把后置摄像头留在系统画中画里，切到别的应用后继续看实时画面。这是一个 Android 学习原型，当前版本 **0.3.2**（versionCode 10）。

它多留一个画面，并可选地标出画面里的人、车或路边的椅子。提醒只说明「这次检测到了这些目标」。路还是要自己看。不要在马路、楼梯或骑行时靠它决定下一步。

## 怎么用

1. 允许摄像头，点「打开摄像头预览」。不需要悬浮窗或通知授权。
2. 先站着确认镜头方向。手机自然握在手里时，后置镜头经常朝向地面。
3. 确认取景后，点「以小窗观察」。拖动、缩放和返回完整界面由系统画中画管理，具体操作随系统版本变化。
4. 用小窗菜单里的「停止」、完整预览页或首页结束观察。

- 识别和声音在完整预览页设置，每次打开都默认关闭。声音只在识别开启后可用。
- 锁屏、关掉小窗，或预览页完全离开屏幕时，会释放摄像头并停止提醒。回到预览页后要自己重新打开。
- 应用不会自动进入画中画。在完整预览里直接按 Home 会停掉相机。先点「以小窗观察」。
- 首页能查看这台设备是否支持画中画，并打开系统设置。不支持、被关掉或进入失败时，画面留在应用内，并给出设置入口。

## 打开识别之后

识别在手机上完成，使用 TensorFlow EfficientDet Lite0（LiteRT 1.4.0）。只看这八类：人、自行车、汽车、摩托车、公交车、卡车、椅子、长凳。

- 绿色框是检测到的目标。黄色框表示进入画面中央的关注区。连续匹配到同类、位置相近的目标后，才会短震一下。
- 儿童归入「人」，不判断年龄。
- 竖屏、胸前持机时，如果手机持续明显朝上或朝下，会提示调整姿势。
- 锥桶、围挡、上下台阶、坑洞和玻璃目前都不支持。不测量距离，也不判断前面能不能走。

画面不保存，也不上传。安装包没有网络权限。

## 安装

推送 `v` 开头的标签后，GitHub Actions 会构建 **arm64** 调试包，并挂到 [Releases](https://github.com/cyanrbt/walkbuddy/releases)。文件名是 `Walk-Buddy-<标签>-arm64-debug.apk`，只含 arm64-v8a，最低 Android 8。同一页上的 Source code 压缩包是源码，不是可安装的应用。

从 0.3.2 起，包名是 `com.cyanrbt.walkbuddy`。系统把它当成新应用，可以和旧包 `cn.study.window` 同时安装，旧应用里的设置不会迁过来。

覆盖安装要满足三点：包名相同、签名证书相同、versionCode 不低于已装版本。只改 `versionName` 或标签不会抬高 versionCode。装不上时先核对这三项。卸载会清掉这个包名下的应用数据。

已核对、且证书彼此不同的历史包不能互相覆盖：

| 发布 | versionCode | 证书 SHA-256 |
| --- | --- | --- |
| v0.2.4 | 6 | `bac8ab9c0a50066dc91f51c1715c6b566bcbebaacdbf5c4233bfe1ffa3bbd95a` |
| v0.3.0 | 8 | `1c4b3a2ce0652541d28b2ae2bb152a684974b881a4028f9ab4e90b06a0edad9a` |

当前发布用的固定证书 SHA-256 是 `ffdf47c306d335d2ee3bc39256d43c470fda5572e80662155ed31dc1c16aae0d`。

## 本地构建

需要 JDK 17，以及 Android SDK 36（platforms、build-tools 36.0.0、platform-tools）。在项目根目录写 `local.properties`：

```
sdk.dir=/绝对路径/android-sdk
```

```sh
python3 scripts/check_model.py
./gradlew assembleDebug testDebugUnitTest lintDebug
```

调试包在 `app/build/outputs/apk/debug/app-debug.apk`。四 ABI 包用 `./gradlew assembleDebug -PuniversalApk`。

固定组合是 Java 17、Gradle 8.13、Android Gradle Plugin 8.10.1、LiteRT 1.4.0。第一次构建会下载依赖。应用运行时不联网。

## 发布

仓库 Actions Secret `DEBUG_KEYSTORE_BASE64` 里要有固定签名密钥库的 base64。密码是 `android`，别名是 `androiddebugkey`。原始密钥另外备份，不要提交进仓库，也不要传到 Release。私钥不能从 APK 里还原。

工作流只使用这份 Secret。密钥缺失或无效时，发布会停下来，不会改用缓存，也不会临时生成一把新钥匙。别名和密码相同，不代表证书相同。换了证书之后，已安装的用户需要卸载再装，之后才能继续覆盖升级。

每次发布前递增 `app/build.gradle` 里的 `versionCode`，并保持 `applicationId` 为 `com.cyanrbt.walkbuddy`。推送 `v*` 标签后确认 Release 页面上的 APK 附件可以下载。

## 更多说明

- [方案](docs/方案.md)：界面、生命周期、识别和提醒规则
- [预览修复](docs/预览修复.md)：预览方向
- [模型扩展](docs/模型扩展.md)：锥桶、围挡、台阶等待接入的权重
- [验证结果](docs/验证结果.md)、[云迁移验证](docs/云迁移验证.md)、[实机测试](docs/实机测试.md)
- [许可证](docs/许可证.md)

源码：https://github.com/cyanrbt/walkbuddy

## 版本

- **0.3.2**：包名和代码命名空间改为 `com.cyanrbt.walkbuddy`，versionCode 10。
- **0.3.1**：加上目标框、位置与提醒原因、逐目标连续确认，以及持机倾斜提示。模型没有更换。
- **0.3.0**：小窗改为系统画中画。去掉悬浮窗、通知和前台服务。相机由可见的预览页持有。
