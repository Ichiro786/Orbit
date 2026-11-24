package com.wangzan101.mika.core.whatsapp

import com.wangzan101.mika.core.capability.Capability
import com.wangzan101.mika.core.capability.CapabilityKey
import com.wangzan101.mika.core.capability.CapabilityStatus
import com.wangzan101.mika.core.capability.PromptEnv
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import com.wangzan101.mika.core.tool.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Where a task's result goes when the user asked for it on their WhatsApp: the user's own "Message yourself"
 * chat. Checking the link, de-duplicating and the actual send live on the app side; core only knows this port.
 */
fun interface WhatsappOwnerPort {
    suspend fun send(text: String): WhatsappOwnerResult
}

/**
 * How one push ended. [Uncertain] means it was handed to WhatsApp without a confirmation; like a failure it is told
 * to the model as it is and never resent automatically. [message] is the plain reason, passed to the model unchanged.
 */
data class WhatsappOwnerResult(val status: Status, val message: String? = null) {
    enum class Status { Delivered, Uncertain, Failed, Unavailable }
}

/** One language's gate: a send verb and the owner's WhatsApp both appear, with no negation before the verb. */
private class GatePattern(val verb: Regex, val target: Regex, val negation: Regex)

/** The explicit-intent patterns per language; another language is one more entry here. */
private val GATE_PATTERNS = listOf(
    GatePattern(
        verb = Regex("\\b(?:send|deliver|forward|push|post)\\b", RegexOption.IGNORE_CASE),
        target = Regex("\\bmy whatsapp\\b", RegexOption.IGNORE_CASE),
        negation = Regex("\\b(?:don't|dont|do not|never|no need to)\\b", RegexOption.IGNORE_CASE),
    ),
    GatePattern(
        verb = Regex("发送|发到|发给|推送|推给|推到|转发|传给|发"),
        target = Regex("我的?\\s*whatsapp", RegexOption.IGNORE_CASE),
        negation = Regex("不|别|无需"),
    ),
)

/**
 * Whether the user's opening request ([goal]) explicitly asks for the result on their WhatsApp. The verb and the
 * target are matched anywhere in the request (real requests are often long); a negation only counts before the verb.
 * When in doubt it says no: this gate alone decides whether the tool is offered at all.
 */
fun explicitWhatsappOwnerRequest(goal: String): Boolean = GATE_PATTERNS.any { p ->
    val verb = p.verb.find(goal) ?: return@any false
    if (!p.target.containsMatchIn(goal)) return@any false
    p.negation.find(goal)?.let { it.range.first < verb.range.first } != true
}

/**
 * Pushing the result to the owner's own WhatsApp chat: whatsapp_send_owner is offered only when the user asked for it
 * in this task and the app has a linked, enabled WhatsApp to deliver to. With no port there is no tool and no section.
 */
internal class WhatsappOwnerCapability(private val port: WhatsappOwnerPort?) : Capability {
    override val id: String = WHATSAPP_OWNER

    override fun status(): CapabilityStatus =
        if (port != null) CapabilityStatus.Available else CapabilityStatus.Unavailable("owner push channel not linked")

    override fun tools(): List<AgentTool> = if (port == null) emptyList() else whatsappOwnerAgentTools

    override fun sections(env: PromptEnv): List<String> =
        if (port != null && env.isOpen(id) && SEND_OWNER in env.offered) listOf(SECTION) else emptyList()

    override fun onTaskStart(ctx: ToolContext) {
        port?.let { ctx[KEY] = it }
    }

    companion object {
        const val WHATSAPP_OWNER = "whatsapp_owner"
        const val SEND_OWNER = "whatsapp_send_owner"
        /** Longest text one push may carry. */
        const val TEXT_MAX = 4000
        val KEY = CapabilityKey<WhatsappOwnerPort>(WHATSAPP_OWNER)

        private const val SECTION = "WhatsApp owner push: the user explicitly asked for this task's result to be sent to their own WhatsApp chat. " +
            "Call whatsapp_send_owner once, when the task is done, with only the final result and any honest uncertainty. " +
            "A delivered status means WhatsApp accepted the text, not that the user has read it. " +
            "If delivery fails or is uncertain, report exactly that and never resend on your own. " +
            "Never use the WhatsApp app on the screen or whatsapp_send as a substitute for this tool."
    }
}

/** All tools of this capability; stateless, the port reaches the tool context at task start via [WhatsappOwnerCapability.KEY]. */
internal val whatsappOwnerAgentTools: List<AgentTool> = listOf(WhatsappSendOwner())

private class WhatsappSendOwner : AgentTool {
    override val capability = WhatsappOwnerCapability.WHATSAPP_OWNER
    override val verb = Verb.WhatsappSendOwner
    override val risk = Risk.Write
    override val offScreen = true
    override val logged = true
    override val def = ToolDef(
        WhatsappOwnerCapability.SEND_OWNER,
        Prompts.tool(WhatsappOwnerCapability.SEND_OWNER),
        mapOf("text" to str("Final result text to deliver to the user's own WhatsApp chat.")),
        listOf("text"),
    )

    /** Not assembled without a linked channel; checked again here. */
    override fun available(ctx: ToolContext) = ctx[WhatsappOwnerCapability.KEY] != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val port = ctx[WhatsappOwnerCapability.KEY]
            ?: return ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", "whatsapp_owner_unavailable")
                put("status", WhatsappOwnerResult.Status.Unavailable.name.lowercase())
            })
        val text = a.str("text", min = 1, max = WhatsappOwnerCapability.TEXT_MAX)!!
        val result = port.send(text)
        return ToolExecution(buildJsonObject {
            put("ok", result.status == WhatsappOwnerResult.Status.Delivered)
            put("status", result.status.name.lowercase())
            result.message?.let { put("text", it) }
        })
    }
}
