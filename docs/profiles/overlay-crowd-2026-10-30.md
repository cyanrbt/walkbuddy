# 密集目标标签排版

v0.4.22 已处理单标签字号、字形边界和对比度，但每个标签仍独立摆放，不能保证多目标之间可读。当前先增加 camera-free 原生截图基线：三档字号、三种宽度及同排、混合风险、31 个密集目标、留黑边四角四种构造场景，共 36 组。记录真实文字边界、可见数量和相交对数，不把基线采集通过当作已解决遮挡。

后续排版会参考 [Lookout 设计团队对清晰度的强调](https://design.google/library/google-lookout-design-accessibility)和 [Apple 相机设计中减少取景干扰的原则](https://developer.apple.com/design/human-interface-guidelines/camera-control)，优先重要信息并保留目标附近的对应关系。这是本项目的具体排版选择，不声称上述产品使用相同算法，也不引入其服务。

基线待云端实际运行；没有真实设备或识别能力的验收结果。
