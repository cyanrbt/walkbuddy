# 候选模型隔离核验

研究工具固定候选版本、文件大小和 SHA-256，检查真实 ONNX 图、输入/输出和内嵌类别顺序，再运行宿主 CPU 冒烟测试。Android 应用继续使用已发布的 EfficientDet Lite0；候选权重下载到临时目录，报告只保留元数据、分数、框坐标与短时计时。

`candidates.json` 记录已核对的来源。J4VIS 的权重声明 AGPL-3.0-only，并要求保留该归属原文：

> 이 결과물은 AI 허브의 「인도보행 영상」 데이터셋을 활용하였습니다.

核验结果不代表已完成应用分发许可、与现有依赖兼容性或真实道路识别验收。模型只提供 24 类，其中有人、围挡和立柱，没有锥桶、上台阶或下台阶。

## 运行

在独立 Python 3.12 环境安装 `requirements.txt` 后：

```sh
python -m unittest discover -s experiments/model_candidates -v
bash scripts/fetch_test_fixture.sh
python experiments/model_candidates/audit.py \
  --download --model /tmp/walkbuddy-sidewalk.onnx \
  --fixture app/src/androidTest/assets/portrait.jpg \
  --output /tmp/walkbuddy-sidewalk-audit.json
```

已有模型文件也必须通过固定摘要核对。下载只有完整且摘要匹配后才改名为目标文件；现有文件不匹配时直接报错，保留该文件。程序读取模型内 `names` 字典，使用 `ast.literal_eval`，不执行元数据表达式。

## 检查范围

- 输入实际为 FLOAT32 RGB、NCHW `[1,3,640,640]`；输出为 FLOAT32 `[1,28,8400]`，含四个框坐标与 24 类分数，没有额外 objectness 或图内 NMS。`person` 的真实索引是 7。
- 图片按比例用 Pillow 双线性缩放，灰值 114 补边，归一化为 0～1。记录实际缩放和整数补边供框回映使用。重采样没有与发布者的浏览器/OpenCV 路径做逐像素一致性验收。
- 每个单/双线程 CPU 会话运行黑色内容、均匀灰色、白色内容与现有人像；每组一次初始调用、两次预热和五次采样，重复输入的全部原始输出必须完全一致。报告分别记录准备、会话创建、推理和后处理，不能当作相机端到端速度。
- 按类别进行 NMS，记录默认 0.25 置信度下的结果，同时记录 0.35 和 0.50 下的数量，避免将新模型的分数直接套用当前应用阈值。
- 正确形状、有限输出和重复一致是冒烟检查；人像只有一个固定样本，三个常量背景不能替代标注集召回率、误报率、上下方向分类或实体设备性能测试。

`Candidate model audit` 在研究工具变更时运行，也可手动执行。产物只有 JSON 和文字日志，权重放在 runner 临时目录，不上传为构建附件；本工具不修改 Android 依赖、模型、阈值或版本。

## Open Images 台阶参考推理

Google 的 SSD MobileNet V2 Open Images V4 v1 是另一个候选，发布 API 标明 Apache 2.0。固定归档、解压文件、三个验证集 JPEG、标注来源和照片署名见 `openimages.json`。权重是 TF1 SavedModel，并非已经适配移动端的 TFLite 文件。照片从 CVDF 公开镜像下载，保留原作者、作品标题、原页与 CC BY 2.0 链接；不提交或上传照片、权重。

**另建 Python 3.12 环境**安装 `requirements-openimages.txt`，不要与 ONNX 环境混装（NumPy 版本不同），运行：

```sh
python -m unittest discover -s experiments/model_candidates -v
bash scripts/fetch_test_fixture.sh
TF_CPP_MIN_LOG_LEVEL=2 python experiments/model_candidates/openimages_reference.py \
  --download --cache /tmp/walkbuddy-openimages \
  --portrait app/src/androidTest/assets/portrait.jpg \
  --output /tmp/walkbuddy-openimages-reference.json
```

已有缓存可省去 `--download`；仍须通过大小和 SHA-256 检查。解压只允许清单中的两个普通文件，拒绝其它路径或类型。GitHub 工作流使用独立作业运行这份参考工具，附件只有 JSON 和文字日志。

- 输入为 RGB FLOAT32 `[1,H,W,3]`、0～1，使用原图尺寸，缩放交给模型。单线程、关闭 oneDNN，每个输入执行两次，全部原始输出含字符串必须逐项相同；计时仅为两次宿主调用，不能外推手机性能。
- 真实归档的 `detection_class_names` 为 MID，而 `detection_class_entities` 为可读名称，与发布模型卡的描述相反；实际分数/类别输出还带有第二维 `[N,1]`。工具核对真实形状及 MID、数字标签 93、`Stairs` 三者，字段不符时失败，避免静默得到零个台阶。
- 三张照片在推理前按验证集图像 ID 排序选择：仅保留非 GroupOf、非 Depiction 的 Stairs 框，要求元数据明确为数值旋转 0、CC BY 2.0；26 张符合条件的图像中取前三张。CSV 大小、摘要、所选框与照片摘要已固定，不根据模型输出挑样本。
- 报告保留全部输出中的台阶框，按 0.35 分数计算每个标注框的最大 IoU。这是独立重叠检查，没有一对一分配，不是 AP、总体召回率或正式验收；没有楼梯上下方向标注。七组输入含三个常量、人像和三张台阶照片，不能替代步行视角正负例评测。

参考结果与限制见 [Open Images 核验记录](../../docs/profiles/openimages-stairs-2026-10-30.md)。
