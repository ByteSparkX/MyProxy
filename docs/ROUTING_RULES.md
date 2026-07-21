# 路由模式与规则来源

## 三种模式

- `规则`：`geosite:gfw` 命中的受限域名走代理，`geosite:cn`、`geoip:cn`、私有地址及其他未命中流量直连。
- `全局`：除项目原有的 BitTorrent 阻断规则外，TCP/UDP 流量都走所选代理节点。
- `直连`：除项目原有的 BitTorrent 阻断规则外，TCP/UDP 流量都走本地网络，不需要选择节点。

规则模式采用黑名单分流，目标是让国内和未受限的国外网站直连，仅将规则库确认受限的域名送入 VPN 代理。新出现或未被规则收录的域名可能暂时直连，此时可临时切换为全局模式。

## GitHub 规则来源

项目使用 [Loyalsoldier/v2ray-rules-dat](https://github.com/Loyalsoldier/v2ray-rules-dat) 发布的 Xray/V2Ray 增强规则文件。上游仓库使用 GPL-3.0 许可证，并由 GitHub Actions 定期生成规则。

当前锁定版本：

```text
release: 202607202253
published: 2026-07-20T22:53:47Z
geoip.dat SHA-256: AF332AB88EB4BB15E3CD10F03F5542E90655EE4BD5BF0E23949CFBD1E46BC20F
geosite.dat SHA-256: 32EF2379DF257042DBF9D3E6779B42F1533FFECE918A009470F4851FF2277923
```

下载地址：

```text
https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/202607202253/geoip.dat
https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/202607202253/geosite.dat
```

文件位于：

```text
app/src/main/assets/geoip.dat
app/src/main/assets/geosite.dat
```

更新时必须同时下载对应的 `*.sha256sum`，校验通过后再替换 assets，并运行 Debug workflow 的单元测试、lint 和 APK 构建。
