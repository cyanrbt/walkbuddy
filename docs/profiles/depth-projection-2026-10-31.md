# v0.4.32 增强测距投影缓冲

v0.4.31 的有效深度处理每轮创建三个 64×96×2 坐标数组、两个内参数组，每个有效点另创建输入和 Pose 返回的两个三元素数组。本版由 `DepthProjection` 持有这些工作缓冲；首次取得有效深度后才在现有 AR 后台线程创建，关闭相机时清除引用。每帧仍运行两次坐标变换、读取当前内参、使用当前 Pose；只保留可复用的存储，不保留上一帧位姿或投影值。当前网格继续新建并复制标量，发布结果不引用工作点数组。

参考 [ARCore Raw Depth 示例](https://github.com/google-ar/arcore-android-sdk/blob/main/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/DepthData.java) 的当前帧图像、内参与采样范围处理。实现继续使用本项目既有映射和 64×96 网格；不照搬示例的渲染点数或参数。[Pose 文档](https://developers.google.com/ar/reference/java/com/google/ar/core/Pose#transformPoint(float[],int,float[],int))提供写入已有数组的重载；[CameraIntrinsics 文档](https://developers.google.com/ar/reference/java/com/google/ar/core/CameraIntrinsics)要求属性随 Session.update 更新，本版每轮重新读取。

[Frame 文档](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#transformCoordinates2d(com.google.ar.core.Coordinates2d,float[],com.google.ar.core.Coordinates2d,float[]))说明旧 Frame 调用可能保持输出不变。当前串行工作线程在下一次 update 前完成处理；同时复用前将坐标和内参填为 NaN，缺失/部分/无效映射不进入采样，避免保留输出被当成本轮值。原深度时间、置信度、范围、网格阈值和提醒规则不放宽。

## 可复现验证

新增共享检查源目录只进入 JVM 与原生测试编译，不进入应用主包。六项 JVM 包含旧表达式与实际 ARCore 1.56.0 Pose 的对照、采样中心、未写入/部分变换清理、相机间/既有网格隔离、非有限边界数学以及红色障碍/落差。96 组情况覆盖 12 种姿态、两组内参、地面确认/未知和两次稀疏更新，共 589,824 个网格位置，比较全部四个浮点字段的原始位和障碍/落差的名称、级别、距离、边界及轮廓。此处是构造数据，未打开 AR Session。

可用 JDK 及锁定 ARCore AAR 内的 classes.jar 执行：

```sh
JAVA_HOME=/path/to/jdk17 python3 docs/profiles/reproduce_depth_projection.py \
  --arcore-jar /path/to/arcore-1.56.0/classes.jar --output /tmp/depth-projection.json
```

[独立 JDK 重现 JSON](depth-projection-2026-10-31.json)已保存当前源码与前版源文件摘要、依赖 JAR 摘要、对照结果及静态数组构造范围。满 6144 个点有效时，被替换路径的可见数组创建表达式/便利 API 共 12,293 次，复用路径每轮为 0、每个工作区首次为 7；保留 float 内容 147,496 字节，不含对象头。该计数只覆盖坐标、内参和点数组，网格/轮廓/遍历及 ARCore 内部分配仍存在，JVM/ART 也可能消除分配；不能据此推算实际 GC、耗时或功耗改善。

最终 0.4.32 / 44 完整 218 项 JVM 为 0 失败 / 错误 / 跳过；Lint 0 错误 / 22 警告，应用及原生测试包、发布元数据、产品表面和差异格式检查通过。Android 15/16 原生同一对照与发布附件待确认。实体 AR 跟踪/测距准确性、帧时、GC、温度和耗电仍待验收；本版不对历史小窗显示停顿下根因结论。
