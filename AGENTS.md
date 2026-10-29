# 项目协作约定

- 用户要求：重大更新验证通过后，及时提交并推送，并触发 GitHub 打包；不要停留在本地改动。
- 发布前递增 `app/build.gradle` 中的 `versionName` 与 `versionCode`，更新对应 `docs/releases/v<版本>.md` 和 README。
- 推送新的 `v*` 标签触发 `.github/workflows/release.yml`；保留包名及固定签名，不覆盖历史标签。
- 查看对应 Actions 运行结果，确认 Release 中实际存在 APK，并向用户提供构建和下载链接。触发构建与构建成功要分别表述。
- 提交源码与验证记录；不提交 APK、构建缓存、机器配置或签名密钥。没有设备结果时，明确区分 JVM/构建验证与真实设备验收。
