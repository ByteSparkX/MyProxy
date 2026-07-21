package com.myproxy.desktop.data

import com.myproxy.desktop.model.ProtocolType
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ShareLinkParserTest {
    @Test
    fun parsesVlessRealityLink() {
        val node = ShareLinkParser.parse(
            "vless://11111111-1111-4111-8111-111111111111@example.com:443" +
                "?type=grpc&security=reality&sni=example.com&pbk=fakePublicKey&sid=01#Demo",
        )

        assertNotNull(node)
        assertEquals(ProtocolType.VLESS, node.protocol)
        assertEquals("Demo", node.remark)
        assertEquals("grpc", node.network)
        assertEquals("fakePublicKey", node.extra["publicKey"])
    }

    @Test
    fun parsesVmessBase64Json() {
        val json = """{"v":"2","ps":"Demo","add":"example.com","port":"443","id":"11111111-1111-4111-8111-111111111111","net":"ws","path":"/demo","tls":"tls"}"""
        val encoded = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val node = ShareLinkParser.parse("vmess://$encoded")

        assertNotNull(node)
        assertEquals(ProtocolType.VMESS, node.protocol)
        assertEquals("/demo", node.path)
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(ShareLinkParser.parse("not-a-node"))
        assertNull(ShareLinkParser.parse("vless://missing-fields"))
    }
}
