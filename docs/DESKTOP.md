# MyProxy Desktop

MyProxy Desktop 当前支持 Windows x64、macOS Intel 和 macOS Apple Silicon。桌面端基于 Compose Multiplatform，并调用随发布包提供的官方 Xray-core。

## 下载文件

从 GitHub Releases 下载与设备匹配的文件：

- Windows 10/11 x64：`MyProxy-<version>-windows-x64.zip`
- Intel Mac：`MyProxy-<version>-macos-x64.zip`
- Apple Silicon Mac：`MyProxy-<version>-macos-arm64.zip`

每个压缩包都有同名 `.sha256`。只从项目 Releases 下载，并在运行前核对校验值。

## Windows x64

1. 解压整个 ZIP，不要只单独拖出 `MyProxy.exe`。
2. 打开解压后的 `MyProxy` 文件夹，运行 `MyProxy.exe`。
3. 如果 SmartScreen 出现提示，核对下载来源和 SHA-256 后，选择“更多信息 -> 仍要运行”。
4. 导入自有服务器分享链接或订阅，选择节点与模式，再点击“连接”。
5. 退出或断开时，应用会恢复连接前的 Windows 系统代理设置。

## macOS

1. 根据处理器下载 Intel 或 Apple Silicon ZIP。
2. 解压后把 `MyProxy.app` 移到“应用程序”。
3. 首次启动使用 Finder 右键 `MyProxy.app`，选择“打开”。
4. 设置系统代理时，macOS 可能弹出管理员授权窗口，这是修改当前网络服务代理所需权限。
5. 退出或断开时，应用会恢复连接前的系统代理设置。

如果当前 macOS 网络服务使用带用户名/密码的认证系统代理，MyProxy 会拒绝覆盖它，因为系统接口无法安全读取并恢复原认证凭据。

当前 macOS 包采用 ad-hoc 签名，尚未使用 Apple Developer ID 公证。如果系统仍提示应用损坏，请先确认文件来自本仓库 Release 且 SHA-256 正确，再执行：

```bash
xattr -dr com.apple.quarantine /Applications/MyProxy.app
```

## 桌面模式说明

- `规则`：GFW 列表命中域名走代理，国内与其他流量默认直连。
- `全局`：经过系统代理的 TCP 流量统一走所选节点。
- `直连`：关闭 MyProxy 系统代理并恢复原设置。

桌面首版使用操作系统 HTTP、HTTPS 和 SOCKS 代理，不是 TUN/VPN。浏览器和遵循系统代理的应用可以使用；主动忽略系统代理的应用不会被接管。UDP 也不保证被普通系统代理应用转发。

## 数据位置

节点与选择状态只保存在本机：

- Windows：`%APPDATA%\MyProxy\desktop-state.json`
- macOS：`~/Library/Application Support/MyProxy/desktop-state.json`

文件包含用户导入的连接参数。不要公开、上传或附加到 Issue。

## 异常恢复

应用在改动系统代理前会保存原设置。正常断开和退出会恢复；如果进程异常终止，下次启动会先尝试恢复。若电脑在应用崩溃后无法联网，可在系统网络设置中手动关闭代理，再重新打开 MyProxy。
