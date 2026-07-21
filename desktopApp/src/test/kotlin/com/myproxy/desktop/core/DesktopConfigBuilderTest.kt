package com.myproxy.desktop.core

import com.myproxy.desktop.model.ProtocolType
import com.myproxy.desktop.model.ProxyNode
import com.myproxy.desktop.model.RoutingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DesktopConfigBuilderTest {
    @Test
    fun buildsRuleConfigWithDesktopInbounds() {
        val node = ProxyNode(
            remark = "Demo",
            protocol = ProtocolType.VLESS,
            address = "example.com",
            port = 443,
            uuid = "11111111-1111-4111-8111-111111111111",
            network = "ws",
            security = "tls",
            sni = "example.com",
            path = "/demo",
        )

        val root = Json.parseToJsonElement(DesktopConfigBuilder.build(node, RoutingMode.RULE)).jsonObject
        val inbounds = root.getValue("inbounds").jsonArray
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray

        assertEquals(2, inbounds.size)
        assertEquals("socks", inbounds[0].jsonObject.getValue("protocol").jsonPrimitive.content)
        assertEquals("http", inbounds[1].jsonObject.getValue("protocol").jsonPrimitive.content)
        assertEquals("proxy", rules[2].jsonObject.getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun rejectsProxyModeWithoutNode() {
        assertFailsWith<IllegalArgumentException> {
            DesktopConfigBuilder.build(null, RoutingMode.GLOBAL)
        }
    }

    @Test
    fun buildsDirectModeWithoutCredentials() {
        val root = Json.parseToJsonElement(
            DesktopConfigBuilder.build(null, RoutingMode.DIRECT),
        ).jsonObject
        val outbound = root.getValue("outbounds").jsonArray.first().jsonObject
        assertEquals("freedom", outbound.getValue("protocol").jsonPrimitive.content)
    }
}
