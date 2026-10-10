# 第三方归属与NOTICE记录

本交付包含TensorFlow EfficientDet Lite0 detection metadata v1权重与其90槽labelmap：作者由内嵌元数据声明为TensorFlow；内嵌许可Apache License Version2.0；官方发行记录亦明确Apache2.0。源URL、版本、摘要及JSON见许可证.md和证据/官方模型发行.json。

模型官方下载archive只含1.tflite；未附独立NOTICE文件。本NOTICE为项目自行提供的中文归属记录，不冒充上游NOTICE。附TensorFlow官方Apache2.0完整LICENSE原文；其附带第三方文本保留。没有修改权重，重命名为efficientdet_lite0_v1.tflite，并从模型内取出labelmap.txt用于校验和映射。

运行解释器LiteRT1.4.0及litert-api1.4.0按官方POM为Apache2.0，附官方LICENSE与POM/锁文件。应用原生Java代码由本任务生成，未复制外部业务实现。

模拟器正例使用官方演示测试图portrait.jpg，来源 https://storage.googleapis.com/mediapipe-assets/portrait.jpg ，只用于测试；不进入运行APK，也不纳入源码ZIP。测试复现脚本单独下载并校验摘要，无运行时下载。本项目不对该照片另行授予许可。
