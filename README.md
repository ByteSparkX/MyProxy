# MyProxy（我的代理）

MyProxy 是一个开源 Android VPN 客户端，用于连接用户本人拥有或已获授权使用的合法服务器，帮助保护个人网络隐私。

项目不集成广告、统计、第三方推送、账号系统或云同步。请勿把真实节点、订阅 URL、UUID、密码、服务器信息或签名文件提交到仓库。

## 平台状态

| 平台 | 状态 |
| --- | --- |
| Android | 已提供 APK |
| iOS | 计划中 |
| Windows | 计划中 |

当前只发布 Android 版本。iOS 和 Windows 客户端属于后续规划，不代表已承诺具体发布日期。

## 下载 Android APK

前往 [GitHub Releases](https://github.com/ByteSparkX/MyProxy/releases) 下载最新版本中的 `MyProxy-*-android.apk`，并使用同一 Release 附带的 `.sha256` 文件核对完整性。

Android 如果阻止安装，请按系统提示临时允许当前浏览器或文件管理器“安装未知应用”；安装完成后建议关闭该权限。

## 功能概览

- VLESS、VMess、Trojan、Shadowsocks 节点解析与配置生成
- Xray-core Android AAR
- Android `VpnService` 与 tun2socks 流量桥接
- 规则、全局、直连三种路由模式
- 分享链接、二维码与订阅导入
- 节点选择、测延、流量状态与分应用代理
- 开机恢复、自定义 DNS、浅色与深色主题

规则模式使用 [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat) 数据：国内及未命中代理规则的流量直连，命中受限域名规则的流量通过所选节点。具体版本和更新流程见 [docs/ROUTING_RULES.md](docs/ROUTING_RULES.md)。

## 技术栈

- Kotlin、Jetpack Compose、Material3
- Kotlin Coroutines、Room、DataStore
- Android `VpnService`
- Xray-core Android AAR 与 tun2socks native bridge
- Gradle Kotlin DSL、JDK 17
- GitHub Actions 云端构建

## 云端构建

项目使用 Gradle Wrapper 和 GitHub Actions 构建，不需要安装 Android Studio。

- Debug：push 到 `main` 或手动运行 `Android Debug Build`，在 Actions Artifact 下载 `myproxy-debug-apk`
- Release：推送 `v*` 标签后运行 `Android Release Build`，构建成功会自动创建 GitHub Release，并附加签名 APK 与 SHA-256 文件

详细步骤见 [docs/GITHUB_ACTIONS_BUILD.md](docs/GITHUB_ACTIONS_BUILD.md)。内核更新见 [docs/UPDATE_KERNEL.md](docs/UPDATE_KERNEL.md)。

## 安全约束

请勿提交或在 Issue、日志中公开：

- 真实节点链接、订阅 URL、UUID、密码和服务器地址
- 完整 Xray JSON 配置
- `local.properties`、`keystore.properties`
- `*.jks`、`*.keystore`、keystore Base64 或签名密码

安全问题请按 [SECURITY.md](SECURITY.md) 私下报告。

## 参与贡献

欢迎提交 Android 端问题与改进。开始前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。iOS 与 Windows 当前仅在路线规划中，后续实现应使用独立的平台目录或项目，并保持协议模型与安全约束一致。

## 许可证

本项目源代码以 [GNU General Public License v3.0 only](LICENSE) 发布。仓库包含或依赖不同许可证的第三方组件和数据，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
