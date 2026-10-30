# 跟随真实相机内容的小窗动画边界

参照 [Android PiP 官方指南](https://developer.android.com/develop/ui/views/picture-in-picture#set-sourcerecthint)和其链接的官方样例：动画来源/目标应跟随实际取景位置，尤其是布局和方向改变后的展开目标。本项目仍由用户主动点击进入，不启用 Home 自动进入。

2026-10-31 实际读取 [platform-samples 的 PiPSampleActivity](https://github.com/android/platform-samples/blob/main/samples/user-interface/picture-in-picture/src/main/java/com/example/android/pip/PiPSampleActivity.kt)：其通过 `trackPipAnimationHintView` 跟踪内容区域。本项目沿用平台 API，结合已有相机变换计算内容矩形，没有引入示例的媒体会话、自动播放或新的 Jetpack 依赖。

## 具体缺口与实现

v0.4.28 的 `pipParams()` 仅在进入时使用整个 TextureView 的 `getGlobalVisibleRect`，包含 fit-center 黑边；之后菜单更新只提交动作，没有在布局变化时更新取景边界。当前几何已能算出实际图像位置，因此动画参数有更精确的数据可用，无需改变模型、显示矩阵或安全截止。

0.4.29 用图像左/上/宽/高与 root 坐标偏移计算像素矩形，向外取整后和当前可见区域相交；不相交不编造矩形。全局布局与几何变化时更新，按矩形/方向去重；相机停止移除监听，新视图重新跟踪。动作状态更新携带当前完整参数，提交失败保留再次尝试机会。

## 验证方法

七项新增 JVM 回归覆盖横竖黑边、窗口偏移、全部 16 种传感器/屏幕方向、可见裁切、空相交、亚像素边缘和展开尺寸变化。原有 187 项保持。

原生检查用 Android `TextureView.getTransform().mapRect()` 独立计算实际图像范围，与应用成功提交的 PiP 参数对照，允许像素取整引起的 1px 差；不调用新的纯 Java 边界函数作为预期值。真实 Activity 转向后恢复，要求相机、纹理和会话代数保持，采集/显示继续推进，至少一组实际排版存在黑边；进入实际系统小窗和展开后另核对当前图像边界。

现有十项探针、真实触摸与全部 1200ms/1500ms/启动截止保持。原始坐标和截图将由独立测试包导出，不进入主 APK。本地完整 194 项 JVM 为 0 失败 / 错误 / 跳过，Lint 0 错误 / 22 警告，应用与原生测试包构建通过；发布元数据、产品表面和差异格式检查通过。云端实际结果待完成后补记。

此处证明参数与实时几何一致，不以它证明动画帧率提升、v0.4.28 候选显示停顿已修复或实体设备体验已验收。

## 候选实际结果

[41096792807](https://github.com/cyanrbt/walkbuddy/actions/runs/41096792807) attempt 1：Android 15 job `124204502189` 与 Android 16 job `124204501664` 均首次十项全部成功。两份完整原始 job 日志已读取，两个原始附件已下载，大小与 SHA-256 匹配 GitHub；本地重放摘要与十项原始 PASS 一致，[结构化附件记录](pip-source-native-candidate-2026-10-31.json)保留来源。

每套两轮完整小窗流程均通过新增三组真实旋转/恢复与两组实际小窗进入/展开核对。三个矩形在 Android Matrix 与提交参数之间实际一致，相机、纹理和 generation 保持，采集/显示继续推进。两套原始坐标相同：

| 实际配置 | 原整个控件矩形 | 当前图像/提交矩形 |
| --- | --- | --- |
| 竖屏 | `(0,95)–(320,364)`，320×269 | `(59,95)–(261,364)`，202×269 |
| 横屏 | `(0,95)–(384,224)`，384×129 | `(106,95)–(278,224)`，172×129 |
| 恢复竖屏 | `(0,95)–(320,364)` | `(59,95)–(261,364)` |

[两张原始系统截图](../design/v0.4.29/README.md)已查看。API 35 观察到系统服务 `com.google.android.as` 的 `No response to onStartJob` ANR，未观察到本应用 ANR；API 36 无 ANR 观察。不把同时出现的系统记录解释为初轮显示停顿的原因，亦不据本轮成功声称全部输入/图形问题消失。

标签版本将独立执行 Android 15/16 发布检查，实际发布与 APK 核验待完成后记录。
