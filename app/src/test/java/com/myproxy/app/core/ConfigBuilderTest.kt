package com.myproxy.app.core

import com.myproxy.app.model.ProtocolType
import com.myproxy.app.model.ProxyNode
import com.myproxy.app.model.RoutingMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBuilderTest {
    @Test
    fun ruleModeProxiesOnlyGfwMatchesAndDefaultsToDirect() {
        val config = parseConfig(ConfigBuilder.build(testNode, routingMode = RoutingMode.RULE))
        val routing = config.getValue("routing").jsonObject
        val rules = routing.getValue("rules").jsonArray

        assertEquals("IPIfNonMatch", routing.getValue("domainStrategy").jsonPrimitive.content)
        assertTrue(rules.hasDomainRule("geosite:gfw", "proxy"))
        assertTrue(rules.hasDomainRule("geosite:cn", "direct"))
        assertTrue(rules.hasIpRule("geoip:cn", "direct"))
        assertEquals("direct", rules.last().jsonObject.getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun globalModeUsesProxyCatchAll() {
        val config = parseConfig(ConfigBuilder.build(testNode, routingMode = RoutingMode.GLOBAL))
        val rules = config.getValue("routing").jsonObject.getValue("rules").jsonArray

        assertEquals("proxy", rules.last().jsonObject.getValue("outboundTag").jsonPrimitive.content)
    }

    @Test
    fun directModeDoesNotContainProxyOutbound() {
        val config = parseConfig(ConfigBuilder.buildDirect())
        val outbounds = config.getValue("outbounds").jsonArray
        val rules = config.getValue("routing").jsonObject.getValue("rules").jsonArray
        val sniffing = config.getValue("inbounds").jsonArray
            .first().jsonObject.getValue("sniffing").jsonObject

        assertFalse(outbounds.any { it.jsonObject["tag"]?.jsonPrimitive?.content == "proxy" })
        assertEquals("direct", rules.last().jsonObject.getValue("outboundTag").jsonPrimitive.content)
        assertTrue(sniffing.getValue("enabled").jsonPrimitive.content.toBoolean())
        assertTrue(sniffing.getValue("routeOnly").jsonPrimitive.content.toBoolean())
    }

    private fun parseConfig(value: String): JsonObject {
        return Json.parseToJsonElement(value).jsonObject
    }

    private fun JsonArray.hasDomainRule(domain: String, outboundTag: String): Boolean {
        return any { element ->
            val rule = element.jsonObject
            rule["domain"]?.jsonArray?.any { it.jsonPrimitive.content == domain } == true &&
                rule["outboundTag"]?.jsonPrimitive?.content == outboundTag
        }
    }

    private fun JsonArray.hasIpRule(ip: String, outboundTag: String): Boolean {
        return any { element ->
            val rule = element.jsonObject
            rule["ip"]?.jsonArray?.any { it.jsonPrimitive.content == ip } == true &&
                rule["outboundTag"]?.jsonPrimitive?.content == outboundTag
        }
    }

    private val testNode = ProxyNode(
        remark = "unit-test",
        protocol = ProtocolType.SHADOWSOCKS,
        address = "example.invalid",
        port = 443,
        password = "unit-test-placeholder",
        method = "aes-128-gcm",
    )
}
