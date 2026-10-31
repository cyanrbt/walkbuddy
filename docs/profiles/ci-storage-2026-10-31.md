# 2026-10-31 运行机存储与近期红叉核对

北京时间 12:11 左右重新读取 Actions、正式 Release 和两个失败作业的原始日志。此次查询不是重新运行历史失败。

| 运行 | 实际结果 | 已取得的失败证据 |
| --- | --- | --- |
| [正式 v0.4.37 / 41147755325](https://github.com/cyanrbt/walkbuddy/actions/runs/41147755325) | attempt 1 成功 | Android 15、16 和固定签名 APK 三个作业均成功；Release 仍有 9,980,497 字节 APK |
| [main 普通检查 / 41164516904](https://github.com/cyanrbt/walkbuddy/actions/runs/41164516904) | attempt 1 成功 | 两套模拟器通过 |
| [main 系统轨迹 / 41164516839](https://github.com/cyanrbt/walkbuddy/actions/runs/41164516839) | attempt 1 失败 | API 35 成功；API 36 编译成功后，模拟器因磁盘不足无法启动，应用探针未运行 |
| [实验阶段采证 / 41165858460](https://github.com/cyanrbt/walkbuddy/actions/runs/41165858460) | attempt 1 成功 | Android 15、16 作业成功；本次状态核对不冒充新增完整附件重放 |
| [实验像素专项 / 41165858560](https://github.com/cyanrbt/walkbuddy/actions/runs/41165858560) | attempt 2 仍失败 | Android 8 冻结 Camera2 测试连续两次等待停止后的 idle 超时；Android 15、16 首次成功保留 |

## 存储失败与修订

main `3184e1028e1a60a5d4dd8dda30fabed923f35ede` 的 API 36 job `124429091435` 在 03:29:25 UTC 编译成功，03:30:11 UTC 报告可用 **7169.88 MB**、创建 userdata 需要 **7372.80 MB**。此后模拟器启动等待超时。原配置已有 `disk-size: 2048M`，本次实际仍发生上述空间检查，不能将既有配置称为已解决此问题。

首轮失败附件 `13028581900`：993 字节，SHA-256 `58af22c6ae5822351188d69115bef57d672fcc9cb10aebab057f0a3101ef80c8`，已完整下载并核对 ZIP CRC；十项应用记录缺失符合尚未启动模拟器的事实。

新增 `scripts/prepare_ui_storage.py`，仅允许在 GitHub 托管临时运行机上、两份编译后的 APK 均存在时执行。编译后、模拟器安装启动前清理 SDK 的 NDK/CMake 与 Gradle 构建缓存/分发包；保留模拟器、系统镜像、SDK 平台与构建工具、源码、APK 和签名。清理前后比较两份 APK 的 SHA-256，在 `runner-storage.json` 保存实际空闲字节、清理路径和结果。脚本拒绝本地或 self-hosted 环境，不跟随清理目标的外部符号链接。

43 项 Python 工具测试通过，包含清理范围、APK/源码/密钥与运行工具保留、缺失 APK、错误 SDK、环境限制和外部链接保护；相关工作流 actionlint 通过。系统轨迹工作流新增对公共启动步骤的路径触发，以便验证本次实际修改。云端修订后的启动与完整检查结果待新运行取得，不能仅凭本地测试称为问题已解决。

## 实验失败的边界

实验源码 `03c1ec480ff5a331ab41c524cdb435d84d17e857` 的 Android 8 job `124433153068` 首次失败，单独复验 job `124435561386` 仍为同一 `Camera2 idle after explicit stopRepeating` 超时。两轮新的 draw 提交检查通过；EGL 两组像素相同且异步读取通过。这些部分通过不能代替 Camera2 门槛，精确的 idle 时序原因尚未证明，不再盲目重跑。

原失败 ZIP 与复验 ZIP 均保留：首次 128,748 字节 / SHA-256 `376acc589855798fd1071c170b846fed40fb48565491abca01677bd225e41bed`；复验 129,124 字节 / SHA-256 `9045539fcc164971eb53ccb87eac8805fd3fff369764e5d89daafba6ea858bfb`，两者 CRC 均通过。

应用仍为 v0.4.37 / 49；未发布未通过门槛的 v0.4.38。本次修订属于 CI 工具，不改产品与固定签名，也不生成重复的 APK 版本。已有正式 [APK](https://github.com/cyanrbt/walkbuddy/releases/download/v0.4.37/Walk-Buddy-v0.4.37-arm64-debug.apk) 可下载；模拟器检查与 APK 校验均不代替实体设备验收。
