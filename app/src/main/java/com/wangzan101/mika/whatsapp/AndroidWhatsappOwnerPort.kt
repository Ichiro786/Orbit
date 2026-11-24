package com.wangzan101.mika.whatsapp

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.core.run.Channel
import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.Delivery
import com.wangzan101.mika.core.run.DeliveryState
import com.wangzan101.mika.core.run.Outbox
import com.wangzan101.mika.core.whatsapp.WhatsappOwnerPort
import com.wangzan101.mika.core.whatsapp.WhatsappOwnerResult
import java.security.MessageDigest

/**
 * Pushes a task's result to the user's own WhatsApp chat. The linked account and chat are captured when the task
 * starts and checked again before sending: unlinked, paused or relinked mid-run means nothing is sent. Delivery goes
 * through the [Outbox]: the same text in the same run is sent once, and an unconfirmed send is never retried.
 */
class AndroidWhatsappOwnerPort internal constructor(
    private val store: WhatsappStore,
    private val channel: Channel,
    private val outbox: Outbox,
    private val runKey: String,
) : WhatsappOwnerPort {
    constructor(app: MikaApp, runKey: String) : this(app.whatsapp.store, app.whatsapp.channel, app.outbox, runKey)

    private val expectedPeer: ChannelPeer? = store.ownerPeer()

    override suspend fun send(text: String): WhatsappOwnerResult {
        val peer = expectedPeer
            ?: return WhatsappOwnerResult(WhatsappOwnerResult.Status.Unavailable,
                "WhatsApp is not linked or is paused in Orbit; nothing was sent.")
        if (store.ownerPeer() != peer) {
            return WhatsappOwnerResult(WhatsappOwnerResult.Status.Unavailable,
                "The WhatsApp link changed or was paused during this task; nothing was sent.")
        }
        val id = "owner-send:$runKey:${sha256(peer.threadId + text)}"
        val record = outbox.deliver(channel, peer, Delivery.Result(id, peer.threadId, text))
        return when (record.state) {
            DeliveryState.Delivered -> WhatsappOwnerResult(WhatsappOwnerResult.Status.Delivered)
            DeliveryState.Uncertain -> WhatsappOwnerResult(WhatsappOwnerResult.Status.Uncertain,
                "The message was handed to WhatsApp but delivery was not confirmed; it was not resent.")
            else -> WhatsappOwnerResult(WhatsappOwnerResult.Status.Failed,
                "WhatsApp did not accept the message (it may be offline or signed out).")
        }
    }

    private companion object {
        fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)
    }
}
