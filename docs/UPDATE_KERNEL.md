# Update Xray Kernel AAR

The project integrates Xray-core through a local Android AAR:

```text
app/libs/libv2ray.aar
```

The AAR is part of the repository build strategy. Do not download an
unknown binary from an untrusted source, and do not record real nodes,
subscription URLs, UUIDs, passwords, or server information in this file.

## Replace `libv2ray.aar`

1. Keep a local backup before replacing the file:

```powershell
Copy-Item app/libs/libv2ray.aar app/libs/libv2ray.aar.bak
```

2. Copy the new AAR into the fixed path:

```powershell
Copy-Item C:\path\to\libv2ray.aar app/libs/libv2ray.aar -Force
```

3. Inspect the AAR:

```powershell
jar tf app/libs/libv2ray.aar
```

Confirm:

- `classes.jar` exists.
- `jni/arm64-v8a/libgojni.so` exists.
- `jni/armeabi-v7a/libgojni.so` exists if 32-bit support is kept.
- Unexpected ABIs such as `x86` or `x86_64` are not packaged unless Gradle ABI
  filters are intentionally updated.

## Trigger Debug Workflow

After replacing the AAR and committing it to the repository:

1. Open GitHub -> repository -> Actions.
2. Select `Android Debug Build`.
3. Click `Run workflow`.

Alternatively, push to `main` to trigger the Debug workflow automatically.

Artifact:

```text
myproxy-debug-apk
```

## Trigger Release Workflow

After Debug validation passes:

1. Open GitHub -> repository -> Actions.
2. Select `Android Release Build`.
3. Click `Run workflow`.

Or push a version tag:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Artifact:

```text
myproxy-release-apk
```

Release signing requires the GitHub Secrets documented in
`docs/GITHUB_ACTIONS_BUILD.md`.

## API Changes to Check

The current AAR exports these real packages/classes:

- `go.**`
- `libv2ray.Libv2ray`
- `libv2ray.CoreController`
- `libv2ray.CoreCallbackHandler`
- `libv2ray.ProcessFinder`

After replacing the AAR, inspect `classes.jar`:

```powershell
$tmp="$env:TEMP\libv2ray-check"
Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $tmp | Out-Null
Copy-Item app/libs/libv2ray.aar "$tmp\libv2ray.zip"
Expand-Archive "$tmp\libv2ray.zip" $tmp -Force
jar tf "$tmp\classes.jar"
```

If API names or signatures change, update:

- `app/src/main/java/com/myproxy/app/core/XrayCore.kt`
- `app/src/main/java/com/myproxy/app/core/XrayCoreCallback.kt`
- `app/src/main/java/com/myproxy/app/core/XrayProcessFinderStub.kt`
- `app/src/main/java/com/myproxy/app/core/AssetResourceManager.kt`
- `app/src/main/java/com/myproxy/app/core/Libv2rayApiNotes.kt`
- `app/proguard-rules.pro`

## ABI Changes to Check

Gradle ABI filters are in:

```text
app/build.gradle.kts
```

Current ABI filter:

```kotlin
abiFilters += listOf("arm64-v8a", "armeabi-v7a")
```

Also check tun2socks native libraries:

```text
app/src/main/jniLibs/arm64-v8a/libhev-socks5-tunnel.so
app/src/main/jniLibs/armeabi-v7a/libhev-socks5-tunnel.so
```

If the AAR adds or removes ABIs, update both the Gradle ABI filters and the
matching tun2socks libraries. Do not ship an ABI for one native dependency while
the other dependency lacks the same ABI.

## Regression Checklist

- Debug cloud build succeeds.
- Release cloud build succeeds.
- Xray resource initialization works.
- VLESS, VMess, Trojan, and Shadowsocks configs still generate.
- TCP, WebSocket, gRPC, TLS, and Reality combinations are tested.
- UDP is tested.
- VPN permission and TUN creation work.
- tun2socks bridges traffic to `127.0.0.1:10808`.
- R8 does not remove AAR callbacks, JNI classes, Room classes, or serialization
  classes.
- Logs do not expose real nodes or credentials.
