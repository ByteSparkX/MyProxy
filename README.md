# 我的代理

这是一个仅供个人使用的 Android 网络工具 App，用于连接本人拥有或有权使用的合法服务器，保护个人隐私。

项目不对外分发，不做商业化，不集成广告、统计、第三方推送、账号系统或云同步。仓库中不得写入真实节点、订阅 URL、UUID、密码、服务器地址、keystore 或签名密码。

## 技术栈

- Android
- Kotlin
- Jetpack Compose
- Material3
- Kotlin Coroutines
- Room
- DataStore
- Android `VpnService`
- Xray-core Android AAR：`app/libs/libv2ray.aar`
- tun2socks native bridge：`app/src/main/jniLibs`
- Gradle Kotlin DSL
- GitHub Actions
- JDK 17

## 构建路线

本项目按 GitHub 私有仓库 + GitHub Actions 云端构建 APK 的方式维护。本地电脑只负责编辑、提交和下载 APK，不作为主要构建环境。

不需要安装 Android Studio。云端构建使用 Gradle Wrapper：

- `gradlew`
- `gradlew.bat`
- `gradle/wrapper/gradle-wrapper.jar`
- `gradle/wrapper/gradle-wrapper.properties`

## GitHub Actions

Debug workflow:

```text
.github/workflows/android-debug.yml
```

触发方式：

- 手动运行 `Android Debug Build`
- push 到 `main`

Artifact：

```text
myproxy-debug-apk
```

Release workflow:

```text
.github/workflows/android-release.yml
```

触发方式：

- 手动运行 `Android Release Build`
- push tag `v*`，例如 `v1.0.0`

Artifact：

```text
myproxy-release-apk
```

下载 APK：

1. 打开 GitHub 仓库。
2. 进入 `Actions`。
3. 打开成功的 workflow run。
4. 在 `Artifacts` 下载对应 APK artifact。
5. 解压 zip 得到 APK。

## Release 签名

Release APK 在 GitHub Actions 中签名。需要在 GitHub 仓库 Secrets 中配置：

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

详细说明见：

```text
docs/GITHUB_ACTIONS_BUILD.md
docs/GITHUB_UPLOAD_AND_BUILD.md
docs/GITHUB_SECRETS.md
```

不要提交：

- `keystore.properties`
- `*.jks`
- `*.keystore`
- keystore Base64 文本
- 密码或 alias 记录

## 敏感信息约束

不得提交：

- 真实节点链接
- 订阅 URL
- UUID
- 密码
- 服务器地址
- 完整 Xray JSON 配置
- keystore 或签名配置

日志中也不得打印完整节点链接、订阅 URL、二维码原文、UUID、密码或完整配置 JSON。

## 内核更新

Xray-core AAR 更新流程见：

```text
docs/UPDATE_KERNEL.md
```

替换 `app/libs/libv2ray.aar` 后必须重新检查：

- AAR 真实导出的包名、类名和方法签名。
- `app/proguard-rules.pro` keep 规则。
- `app/build.gradle.kts` ABI filters。
- `app/src/main/jniLibs` tun2socks ABI 是否匹配。

## 本地可选检查

本地只建议做轻量检查：

```powershell
.\gradlew.bat :app:compileDebugKotlin --stacktrace
```

Release 完整构建、R8、签名和 APK Artifact 以 GitHub Actions 为准。

## 常见问题

Debug Artifact 找不到：

- 确认 `Android Debug Build` workflow 成功。
- 打开对应 run 底部的 `Artifacts`。

Release 构建提示签名环境变量缺失：

- 检查 GitHub Secrets 是否包含 `KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。
- 确认 workflow 已把 `KEYSTORE_BASE64` 解码为 `KEYSTORE_FILE`。

Release 安装失败：

- 检查 APK 是否来自成功的 Release workflow。
- 检查签名 keystore 是否和旧版本一致。
- 检查 Android 设备是否允许安装未知来源应用。

能连接但没有流量：

- 检查 VPN 权限和前台通知。
- 检查本地 SOCKS 入站 `127.0.0.1:10808`。
- 检查 tun2socks 是否启动。
- 检查分应用代理和自定义 DNS 设置。
