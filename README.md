当前版本 **0.2.1 / versionCode 3**，修复重复旋转与预览比例；详见 [预览修复说明](docs/预览修复.md)。

# WalkBuddy · Android Java 学习原型

中文界面，用户主动开启后置摄像头浮窗，可移动/调整大小，切换App继续，浮窗与通知均有停止入口。主要由用户观察；仅白天正常步行实验，可能误报漏报，没有提醒不代表安全。

辅助识别和声音**每次启动默认关闭**。用户主动开启识别后，固定许可核验模型在本地筛选人、自行车、汽车、摩托车、公交车、卡车、椅子、长凳，不判断距离/深度/通行安全。默认不保存、不上传画面；最终APK无网络权限，没有模型运行时下载。

- [本次云迁移验证与构建阻塞](docs/云迁移验证.md)
- [完整中文技术方案](docs/方案.md)
- [实机测试步骤与空白结果表](docs/实机测试.md)
- [精确模型和依赖许可证据](docs/许可证.md)
- [验证结果与剩余限制](docs/验证结果.md)
- [第三方归属记录](docs/NOTICE.md)

## 可复现构建

固定Java源级17，Temurin OpenJDK17.0.13+11，Gradle8.13（wrapper含官方SHA256），AGP8.10.1，compile/target API36，Build Tools36.0.0，minAPI26。安装官方Android SDK，在项目根建立`local.properties`：`sdk.dir=/绝对路径/android-sdk`，SDK安装platforms;android-36 / build-tools;36.0.0 / platform-tools。工具来源：https://developer.android.com/studio ，https://adoptium.net/ ，https://gradle.org/releases/ 。

```sh
python3 scripts/check_model.py
./gradlew assembleDebug testDebugUnitTest lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。默认ARM64-only（arm64-v8a），最低Android8/API26，适合本次ARM64手机目标；不适用于32位ARM或x86设备。要恢复四ABI通用APK，执行`./gradlew assembleDebug -PuniversalApk`。固定运行依赖LiteRT1.4.0/litert-api1.4.0，测试JUnit4.13.2/Hamcrest1.3；间接版本锁在app/gradle.lockfile。首次构建下载依赖，不等于运行时联网。公开源码内置经核验权重、标签、许可全文、官方发行JSON和公开许可证明；不含大截图、重复APK、SDK/模拟器/JDK缓存或私人密钥。禁止用旧版本含未确认MediaPipe权重的交付替代当前版本。

## 仅模拟器复现真实模型测试

测试人像图片不随源码包或运行APK分发。脚本从官方演示地址下载并校验SHA256，仅进入androidTest资产。

```sh
./scripts/fetch_test_fixture.sh
./gradlew assembleDebug assembleDebugAndroidTest
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w cn.study.window.test/cn.study.window.RuntimeProbe
```

运行命令前，将示例 emulator-5554 替换为自己的模拟器序号。未经授权不在实体手机安装或操作。历史模型验证结果见 docs/验证结果.md。

## 代码入口

MainActivity：权限、中文说明、主动启动。CameraWindowService：相机/浮窗/FGS、过期遮挡、锁屏、停止资源释放。SessionState：纯Java截止锁存状态机。InferenceWorker：进程内唯一worker、最多1个排队帧、创建/推理前取消检查、过期结果丢弃、串行关闭。LocalDetector：LiteRT解释器、张量检查、letterbox、90槽标签映射及8类筛选。AlertPolicy：双观测与3秒冷却。测试包含阻塞fake detector及Android RuntimeProbe。

源码公开于 https://github.com/cyanrbt/walkbuddy ，不承诺安全保障。实机视野、延迟、耗电与识别效果尚未验证。
