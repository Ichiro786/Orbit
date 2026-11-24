package com.wangzan101.mika.core.whatsapp

import com.wangzan101.mika.core.capability.PromptEnv
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ArgError
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.EvidenceLog
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolConfig
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolIndex
import com.wangzan101.mika.core.visual.Shots
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** whatsapp_send_owner: offered only with a port, over-long text refused, the port's outcome reported as it is. */
class WhatsappOwnerToolTest {
    private class FakePort(var result: WhatsappOwnerResult) : WhatsappOwnerPort {
        val sent = mutableListOf<String>()
        override suspend fun send(text: String): WhatsappOwnerResult {
            sent += text
            return result
        }
    }

    private fun ctx(capability: WhatsappOwnerCapability): ToolContext {
        val ctx = ToolContext(
            ToolConfig(false, null, false, null, false, false, null, null, null, null, null),
            EvidenceLog(), ToolIndex(capability.tools()), null, Shots(),
        )
        capability.onTaskStart(ctx)
        return ctx
    }

    private fun args(tool: AgentTool, text: String) = Args(tool.def, buildJsonObject { put("text", text) })

    @Test fun noPortOffersNothing() {
        val capability = WhatsappOwnerCapability(null)
        assertTrue(capability.tools().isEmpty())
        assertTrue(capability.sections(PromptEnv(offered = setOf("whatsapp_send_owner"), inlineScreen = false)).isEmpty())
    }

    @Test fun toolAndSectionOfferedWithPort() {
        val capability = WhatsappOwnerCapability(FakePort(WhatsappOwnerResult(WhatsappOwnerResult.Status.Delivered)))
        val tool = capability.tools().single()
        assertEquals("whatsapp_send_owner", tool.name)
        assertEquals(Risk.Write, tool.risk)
        assertTrue(tool.offScreen)
        assertTrue(tool.logged)
        assertTrue(tool.available(ctx(capability)))
        assertFalse(capability.sections(PromptEnv(offered = setOf("whatsapp_send_owner"), inlineScreen = false)).isEmpty())
        // No section when the tool is not offered in this task
        assertTrue(capability.sections(PromptEnv(offered = emptySet(), inlineScreen = false)).isEmpty())
    }

    @Test fun overlongTextRejected() {
        val port = FakePort(WhatsappOwnerResult(WhatsappOwnerResult.Status.Delivered))
        val capability = WhatsappOwnerCapability(port)
        val tool = capability.tools().single()
        val ctx = ctx(capability)
        assertThrows(ArgError::class.java) {
            runBlocking { tool.execute(args(tool, "x".repeat(4001)), ctx) }
        }
        assertTrue(port.sent.isEmpty())
    }

    @Test fun unavailableReportedHonestly() = runTest {
        val port = FakePort(WhatsappOwnerResult(WhatsappOwnerResult.Status.Unavailable, "The link changed"))
        val capability = WhatsappOwnerCapability(port)
        val result = capability.tools().single().execute(args(capability.tools().single(), "Result text"), ctx(capability)).content
        assertEquals(false, result.getValue("ok").jsonPrimitive.content.toBoolean())
        assertEquals("unavailable", result.getValue("status").jsonPrimitive.content)
        assertEquals("The link changed", result.getValue("text").jsonPrimitive.content)
        assertEquals(listOf("Result text"), port.sent)
    }

    @Test fun deliveredMapsToOk() = runTest {
        val port = FakePort(WhatsappOwnerResult(WhatsappOwnerResult.Status.Delivered))
        val capability = WhatsappOwnerCapability(port)
        val result = capability.tools().single().execute(args(capability.tools().single(), "Result text"), ctx(capability)).content
        assertEquals(true, result.getValue("ok").jsonPrimitive.content.toBoolean())
        assertEquals("delivered", result.getValue("status").jsonPrimitive.content)
    }
}
