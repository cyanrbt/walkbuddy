# 2026-10-30 候选识别模型实际核验

本轮新增隔离的[核验工具](../../experiments/model_candidates/README.md)与固定候选清单，Android 生产代码、依赖、权重与版本保持 0.4.18 / 30。候选权重仅下载到临时目录，未装入 APK 或提交；八项预处理、类别、NMS、坐标及错误报告测试通过，GitHub 结果待记录。

## 来源与可用性

- YOLOX-Pylon-S：发布者的 `1e2dd8f37b26a486dfebb154304294e52a6b1335` 文件清单只有 `.gitattributes` 和 `README.md`，没有权重。此前模型卡提供的 81 类与速度说明不能替代可加载文件。[固定版本文件](https://huggingface.co/empirischtech/yolox-pylon-s/tree/1e2dd8f37b26a486dfebb154304294e52a6b1335)。
- J4VIS sidewalk：实际下载 revision `8feeca4dbf386e77bc76ce93f34caa7301d76c84` 的 `model.onnx`，3,503,070 字节，SHA-256 `731c55985f7dd1bd41c9112e295c9aa7f411f82c3af4903d496f448525028463`。发布说明为 AGPL-3.0-only，模型内含 AGPL-3.0 许可字段；归属要求保存在候选清单中。应用分发条件与现有依赖兼容性尚未完成核对。[固定版本模型卡](https://huggingface.co/J4VIS-2026/yolov8n-sidewalk-obstacle/blob/8feeca4dbf386e77bc76ce93f34caa7301d76c84/README.md)。
- Yesh492 stair 项目：GitHub commit `1a84e65db3eb611e220c7cf5e4a92419adeefa17` 的仓库许可字段为空，完整文件树未发现 `.tflite` / `.onnx`；未核实到可分发移动端权重，训练项目说明不作为模型已接入的证据。[固定版本](https://github.com/Yesh492/stair-detection-training/tree/1a84e65db3eb611e220c7cf5e4a92419adeefa17)。

## 实际契约与类别

J4VIS 文件通过 ONNX 检查及 ONNX Runtime 1.23.2 CPU 会话创建。实际输入为 FLOAT32 RGB NCHW `[1,3,640,640]`，输出为 `[1,28,8400]`，四个框通道加 24 类分数。图中用到的节点均在标准域，含内部 QuantizeLinear/DequantizeLinear；导入列表中的额外域不等于实际使用了那些自定义算子。

类别顺序直接读取模型内 `names`，`person` 的实际索引为 **7**，不是当前基础模型的 0；含围挡、立柱、轮椅等，**没有锥桶、上台阶或下台阶**。前处理采用 640 灰值 114 补边与 0～1 归一化，需要类内 NMS。不能直接沿用当前 320 UINT8 输入、90 槽标签或四输出解码器。

## 本地 CPU 冒烟结果

原始记录：[local.json](model-candidate-sidewalk-2026-10-30-local.json)。独立 x86_64 Linux 宿主，固定 Python 工具版本；每个会话每组一次初始调用、两次预热与五次采样，所有重复输入的全部原始输出完全一致。下表为推理调用本身，准备、会话创建和后处理另见 JSON；两个线程配置按 1→2 单次运行，未用于选择应用线程策略。

| 线程 | 输入 | 推理 median/ms | max/ms | 0.25 下框数 | 0.35 下框数 | 0.50 下框数 |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| 1 | black | 54.78 | 71.07 | 0 | 0 | 0 |
| 1 | pad_gray | 57.22 | 63.63 | 0 | 0 | 0 |
| 1 | white | 65.44 | 70.40 | 0 | 0 | 0 |
| 1 | portrait | 53.91 | 57.96 | 1 | 0 | 0 |
| 2 | black | 42.08 | 50.98 | 0 | 0 | 0 |
| 2 | pad_gray | 37.87 | 41.07 | 0 | 0 | 0 |
| 2 | white | 40.61 | 43.01 | 0 | 0 | 0 |
| 2 | portrait | 40.52 | 43.53 | 1 | 0 | 0 |

固定人像在此 Pillow 双线性预处理下得分约 **0.291849**，类别为 `person`；套用当前 0.35 显示阈值会过滤该框。这只是单样本兼容性发现，不能推断总体识别质量，也不能用降低生产阈值替代标注评测。

本次证明文件真实、输入/输出可执行、元数据顺序明确、重复输入稳定及基本后处理可运行。尚缺新增类别的标注正负样本、与参考端的像素/结果一致性、Android 运行时及指定手机性能、真实步行视角召回与误报评测、分发条件核对。固定人像与三个常量对照不能验收围挡、锥桶或台阶能力；宿主 CPU 计时也不能外推手机端到端速度。
