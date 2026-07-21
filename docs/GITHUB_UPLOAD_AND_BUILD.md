# 上传 GitHub 并构建 APK

本项目可发布到 GitHub 公开仓库。不要通过网页直接拖入整个文件夹，使用 Git 提交可以让 `.gitignore` 自动排除本地 keystore、APK、构建目录和无关网页文件。

## 1. 创建仓库

1. 登录 GitHub，右上角选择 `New repository`。
2. 输入仓库名，例如 `myproxy-android`。
3. 开发期间可选择 `Private`；完成敏感信息审计、许可证和第三方声明后再切换为 `Public`。
4. 不勾选 README、`.gitignore` 或 License，创建空仓库。
5. 保留 GitHub 显示的仓库地址，例如 `https://github.com/YOUR_GITHUB_USERNAME/myproxy-android.git`。

## 2. 初始化并上传本地项目

在 PowerShell 中进入本项目根目录，然后执行：

```powershell
git init
git branch -M main
git add .
git status
```

在 `git status` 中确认以下内容没有进入暂存区：

- `local.properties`
- `.gradle/` 和所有 `build/`
- `keystore.properties`
- `*.jks`、`*.keystore`
- `*.apk`、`*.aab`
- 根目录的 `index.html`、`app.js`、`styles.css` 和 `assets/`

确认无误后提交并推送：

```powershell
git commit -m "finalize github actions cloud apk build"
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/myproxy-android.git
git push -u origin main
```

如果 GitHub 要求登录，按提示使用浏览器授权或 Personal Access Token，不要把 Token 写入项目文件。

## 3. 运行 Debug 云构建

1. 打开仓库的 `Actions` 页面。
2. 左侧选择 `Android Debug Build`。
3. 点击 `Run workflow`，分支选择 `main`，再次点击绿色按钮。
4. 等待 `Run Android lint`、`Build Debug APK` 和 `Upload Debug APK` 全部变绿。
5. 打开该次运行，在页面底部 `Artifacts` 下载 `myproxy-debug-apk`。
6. 解压 ZIP 后得到 Debug APK，可先安装到手机验证。

以后每次 push 到 `main` 也会自动触发 Debug 构建。

## 4. 配置 Release Secrets

进入仓库 `Settings -> Secrets and variables -> Actions -> New repository secret`，依次添加：

- `KEYSTORE_BASE64`
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

在本机 PowerShell 中把 JKS 转为 Base64 文本：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\release.jks")) | Set-Content -NoNewline "keystore-base64.txt"
```

把 `keystore-base64.txt` 的完整内容保存为 `KEYSTORE_BASE64`。其余三个 Secret 填写创建 JKS 时使用的值。添加完成后删除 `keystore-base64.txt`，不要提交 JKS 或该文本文件。

## 5. 运行 Release 云构建

手动构建：

1. 打开 `Actions -> MyProxy Multiplatform Release`。
2. 点击 `Run workflow`，选择 `main` 后运行。
3. 构建成功后可分别下载 Android、Windows x64、macOS Intel 和 macOS Apple Silicon Artifacts。

通过版本标签构建：

```powershell
git tag -a v1.1.0 -m "v1.1.0"
git push origin v1.1.0
```

推送符合 `v*` 的标签后会自动触发多平台 Release workflow。Android 与三个桌面任务全部成功后会创建对应 GitHub Release，并附加每个平台文件及 SHA-256。当前应用 `versionName` 已与 `v1.1.0` 对齐。

## 6. 安装和验收

1. 解压 Artifact ZIP，将 Release APK 传到手机。
2. 如系统拦截，进入提示对应的浏览器或文件管理器设置，临时允许“安装未知应用”。
3. 安装后关闭该未知来源权限。
4. 首次连接依次确认通知权限和 VPN 权限。
5. 导入自有服务器节点，验证连接、出口 IP、通知、后台、锁屏和断开。
6. Debug 与 Release applicationId 不同，可以同时安装；以后升级 Release 必须继续使用同一 keystore。

## 7. 常见问题

- Actions 页面没有 workflow：确认 `.github/workflows/*.yml` 已推送到默认分支 `main`。
- `KEYSTORE_BASE64` 缺失或解码失败：重新生成 Base64，确认 Secret 内容完整且没有多余说明文字。
- Release 签名变量缺失：检查四个 Secret 名称的大小写完全一致。
- 找不到 `libv2ray.aar`：确认 `app/libs/libv2ray.aar` 已被 Git 提交；该文件小于 GitHub 单文件 100 MB 限制。
- `geoip.dat` 或 `geosite.dat` 缺失：确认二者位于 `app/src/main/assets/`。
- R8 失败：查看 `minifyReleaseWithR8` 日志，并对照 `app/proguard-rules.pro` 中真实 AAR/JNI 类名。
- APK 安装提示签名不一致：卸载旧的不同签名版本，或改用原先的同一 keystore 重新构建。
