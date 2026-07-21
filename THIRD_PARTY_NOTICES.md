# Third-Party Notices

MyProxy 使用下列第三方组件和数据。各组件仍受其原始许可证约束；本文件不替代对应上游许可证文本。

## AndroidLibXrayLite

- 上游：https://github.com/2dust/AndroidLibXrayLite
- 版本：`v26.6.14`
- 文件：`app/libs/libv2ray.aar`
- SHA-256：`2b9ba4fc61cf6a7f0bfc3018856c6e2065be23bcad203140c2395a52b0bbd670`
- 许可证：LGPL-3.0

仓库中的 AAR 与该上游 Release 发布资产的大小及 SHA-256 一致。更新时必须重新核对来源、哈希、导出 API、ABI 和许可证。

## Xray-core

- 上游：https://github.com/XTLS/Xray-core
- 用途：AndroidLibXrayLite 中封装的代理核心，以及桌面发布包中的官方可执行文件
- 许可证：MPL-2.0

桌面端锁定 `v26.3.27`，GitHub Actions 只从该上游 Release 下载并校验：

- Windows x64：`Xray-windows-64.zip`
  - SHA-256：`d004c39288ce9ada487c6f398c7c545f7d749e44bdfdd59dbc9f865afba4e1ad`
- macOS Intel：`Xray-macos-64.zip`
  - SHA-256：`f5b0471d3459eff1b82e48af0aeac186abcc3298210070afbbbd8437a4e8b203`
- macOS Apple Silicon：`Xray-macos-arm64-v8a.zip`
  - SHA-256：`2e93a67e8aa1936ecefb307e120830fcbd4c643ab9b1c46a2d0838d5f8409eaf`

## hev-socks5-tunnel

- 上游：https://github.com/heiher/hev-socks5-tunnel
- 用途：TUN 到 SOCKS 的 native bridge
- 许可证：MIT
- `arm64-v8a` SHA-256：`08207be511d72ac39bd5eb1ee602392712b1037686b8b809237552a1393bc935`
- `armeabi-v7a` SHA-256：`32573375fd602cff0a911f2b0ca942d85a95d6c1075b7332f6626a248d22b228`

## v2ray-rules-dat

- 上游：https://github.com/Loyalsoldier/v2ray-rules-dat
- 锁定版本：`202607202253`
- 用途：规则模式的 `geoip.dat` 与 `geosite.dat`
- 许可证：GPL-3.0
- `geoip.dat` SHA-256：`af332ab88eb4bb15e3cd10f03f5542e90655ee4bd5bf0e23949cfbd1e46bc20f`
- `geosite.dat` SHA-256：`32ef2379df257042dbf9d3e6779b42f1533ffece918a009470f4851ff2277923`

## Android 与 Kotlin 生态依赖

AndroidX、Jetpack Compose、Kotlin、Kotlin Coroutines、Room、DataStore、OkHttp、ZXing、CameraX 和 ML Kit 等依赖由 Gradle 按构建配置解析。它们分别受各自上游许可证或使用条款约束，准确版本见 `gradle/libs.versions.toml` 与 Gradle 依赖锁定结果。
