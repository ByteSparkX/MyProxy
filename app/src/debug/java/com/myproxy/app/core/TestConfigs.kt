package com.myproxy.app.core

object TestConfigs {
    // 此配置仅用于阶段二测试，阶段四会由动态配置生成器替换；release 不编译该临时入口。
    const val LOCAL_SOCKS_TO_FREEDOM: String = """
        {
          "log": {
            "loglevel": "info"
          },
          "inbounds": [
            {
              "tag": "local-socks",
              "listen": "127.0.0.1",
              "port": 10808,
              "protocol": "socks",
              "settings": {
                "udp": true
              }
            }
          ],
          "outbounds": [
            {
              "tag": "direct",
              "protocol": "freedom"
            }
          ]
        }
    """
}
