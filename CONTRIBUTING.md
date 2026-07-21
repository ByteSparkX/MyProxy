# Contributing

感谢参与 MyProxy。

## 当前范围

当前仓库维护 Android 与 Compose Desktop 客户端。iOS 处于后续规划阶段；开始相关实现前，请先通过 Issue 讨论目录结构、核心复用边界和发布方式。

## 提交要求

- 使用 Kotlin、Jetpack Compose、Compose Multiplatform 和现有分层结构，不引入广告、统计、第三方推送、账号系统或云同步。
- 不提交真实节点、订阅 URL、UUID、密码、服务器地址、完整配置、keystore 或签名凭据。
- 不在日志、测试数据、截图、Issue 或 Pull Request 中泄露上述信息。
- 修改 AAR、JNI、回调接口、ABI 或 R8 规则时，必须基于实际导出和二进制内容验证。
- 提交前运行相关单元测试；完整 APK 以 GitHub Actions 构建结果为准。

## 构建

项目保留 Gradle Wrapper，并以 JDK 17 在 GitHub Actions 构建。Debug workflow 会在 push 到 `main` 时运行；版本标签 `v*` 会触发签名 Release 构建和 GitHub Release 发布。
