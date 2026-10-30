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
