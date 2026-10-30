# 2026-10-30 Open Images 台阶候选实际核验

Google 发布的 SSD MobileNet V2 Open Images V4 v1 已通过固定文件校验和宿主参考推理。本轮确认它确实能输出 `Stairs`，但三个固定标注样本的结果不足以支持应用接入；没有交付台阶检测、上下方向分类、TFLite 转换或手机性能结论。Android 生产代码、权重和版本仍为 v0.4.18 / 30。

## 权重来源与实际契约

来源为 [Google 发布页面 v1](https://www.kaggle.com/models/google/mobilenet-v2/tensorFlow1/openimages-v4-ssd-mobilenet-v2/1)及其[元数据 API](https://www.kaggle.com/api/v1/models/google/mobilenet-v2/tensorFlow1/openimages-v4-ssd-mobilenet-v2/get)。实际 API 返回 id `2480`、versionId `3327`、versionNumber `1`、licenseName `Apache 2.0`；使用带 `/1/download` 的固定版本链接下载。完整来源清单见 [openimages.json](../../experiments/model_candidates/openimages.json)。

| 文件 | 字节数 | SHA-256 |
| --- | ---: | --- |
| 下载归档 | 53,210,955 | `bbdc9c5d34db1bbdcdf360a698ee3c7bc6f1346abec99aa40cbc018acd94bcf2` |
| saved_model.pb | 63,359,842 | `9f49e034537b2518001794019869df0710f3089b0859ff10600408931a6a1895` |
| tfhub_module.pb | 2 | `2d88972e9536ba54deed37db380728b3c58eab9bca1d173ae523098b0774cdf5` |

归档只有这两个普通文件，大小之和与 API 的 63,359,844 字节一致。它是 TensorFlow 1 SavedModel，使用 TensorFlow CPU 2.19.0 的 `tf.saved_model.load` 成功加载；不包含预制 TFLite。

实际 `default` 输入为 `images`、FLOAT32 `[None,None,None,3]`，本工具按单图 `[1,H,W,3]` RGB、0～1 输入，尺寸处理交给模型。输出有五个字段：

| 字段 | 实际类型 / 形状 | 实际含义 |
| --- | --- | --- |
| detection_boxes | FLOAT32 `[N,4]` | 归一化 ymin,xmin,ymax,xmax |
| detection_scores | FLOAT32 `[N,1]` | 分数 |
| detection_class_labels | INT64 `[N,1]` | 数字标签；Stairs=93 |
| detection_class_names | STRING `[N,1]` | MID；台阶为 `/m/01lynh` |
| detection_class_entities | STRING `[N,1]` | 可读名称；台阶为 `Stairs` |

发布模型卡将两个字符串字段的含义描述为相反顺序，且将若干实际 `[N,1]` 输出写作 `[N]`。最初按模型卡读取 MID 会静默筛掉全部台阶；检查实际输出及归档中的查找表后，已修正参考工具，以下结果均为修正后重跑。新增测试专门拒绝字段对调、标签不符和坏张量，避免把解析错误归因于识别能力。

实际图含 602 个 `NonMaxSuppressionV3`、多个 TensorArray 算子和两个字符串查找算子；这只是算子清点，尚未证明可由当前 Android TFLite 运行时执行。没有把说明中的 600 个语义类别当作张量槽数或直接套用现有基础模型解码器。

## 在推理前固定的三个标注样本

使用 [Open Images V4 官方下载页](https://storage.googleapis.com/openimages/web/download_v4.html)链接的验证集框和旋转元数据。分别校验完整 CSV：

- `validation-annotations-bbox.csv`：17,074,036 字节，SHA-256 `aebdfc1dbae39dc1f25d2079720f95c5963eeaf9ff23fe638522f09aeb306ee7`。
- `validation-images-with-rotation.csv`：15,245,485 字节，SHA-256 `ed93a0e121fe345effdfc7359b848dbc64a1ff6778c8c73563157cb500b33a17`。

仅保留 `Stairs` 的非 GroupOf、非 Depiction 框，图像元数据必须明确为数值旋转 0、CC BY 2.0；缺失旋转不当作 0。26 张合格图像按 ImageID 字典序取前三张，选择早于查看模型输出；不因结果差更换样本。共六个标注框，其中一个有遮挡标记；图片来自 [CVDF 镜像](https://github.com/cvdfoundation/open-images-dataset)，长边至多 1024 像素。

| 图像 ID | 原作者与原始作品 | 标注框数 |
| --- | --- | ---: |
| 0cccffa267fce7bb | David Blaikie，[Hotel Spiral Staircase](https://www.flickr.com/photos/nikonvscanon/1302296182) | 1 |
| 0d73caebb7a63351 | Chalky Lives，[ESCALATOR -TOKYO MIDTOWN](https://www.flickr.com/photos/traitlinburke/3085948363) | 3 |
| 11e182562844e361 | Barney Moss，[Who watches the watchmen? - 286/365](https://www.flickr.com/photos/barneymoss/22067593508) | 2 |

三张照片均按元数据标明的 [CC BY 2.0](https://creativecommons.org/licenses/by/2.0/) 使用。每张照片的标题、作者、原页、许可、图像 SHA-256 和原始标注行保存在清单中；仓库及 CI 附件不包含照片或权重。

## 本地参考结果与范围

独立 Python 3.12 环境固定 TensorFlow CPU 2.19.0、NumPy 2.1.3、Pillow 11.3.0；单线程、关闭 oneDNN。三个常量背景、现有人像、三张台阶照片各执行两次，全部原始输出（含字符串）重复一致。15 项工具单测在独立的 TensorFlow 与 ONNX 环境分别通过，工作流静态检查通过。原始结果见 [local.json](openimages-stairs-2026-10-30-local.json)，[运行命令](../../experiments/model_candidates/README.md#open-images-台阶参考推理)可复现。

| 图像 | 返回的最高台阶分数 | ≥0.35 台阶框数 | 每个 GT 的最大 IoU（仅使用 ≥0.35 台阶框） |
| --- | ---: | ---: | --- |
| 0cccffa267fce7bb | 无台阶条目 | 0 | 0 |
| 0d73caebb7a63351 | 0.397776 | 1 | 0.287202、0.252740、0 |
| 11e182562844e361 | 0.149642 | 0 | 0、0 |

常量背景和人像均没有台阶条目。上述“无条目”仅指模型返回的前 100 个结果中没有台阶，不能推断所有内部候选均为零。报告中的最大 IoU 是每个 GT 独立寻找重叠，没有一对一分配；同一个预测框可参与多个 GT 的计算，因此它不是 AP 或总体召回率。0.35 是本次工具记录的参考分数，未调整生产阈值；没有因某张照片得分较低而另选阈值报喜。

宿主加载和首次调用各有明显开销，原始毫秒计时完整保留。每组只有两次调用，没有稳定性能分布，也未覆盖设备端准备、相机、调度、热衰减和 PiP；不与现有模型作手机速度比较。

这三个样本含螺旋楼梯与扶梯类场景，并不是指定胸前步行视角测试集。现有证据只支持文件可执行、输出契约已查清、重复结果稳定，以及这个固定小样本的具体框结果。后续仍需更多台阶正负例、独立分组评测、上下方向标注和目标手机验证；不能据此提示道路安全。

云端参考作业随下一次推送触发；触发状态与通过状态另行核实，不将本地结果写作云端或设备验收。
