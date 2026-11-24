package com.wangzan101.mika.whatsapp

import com.wangzan101.mika.core.run.Channel
import com.wangzan101.mika.core.run.ChannelInputSink
import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.Delivery
import com.wangzan101.mika.core.run.Outbox
import com.wangzan101.mika.core.run.SendOutcome
import com.wangzan101.mika.core.whatsapp.WhatsappOwnerResult
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The owner result push: the link captured at task start is compared before sending, and the Outbox sends a text once. */
class WhatsappOwnerSenderTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Counts calls only, so a test can assert nothing was sent after a relink or pause. */
    private class CountingChannel(var outcome: SendOutcome = SendOutcome.Delivered) : Channel {
        override val id = WhatsappChannel.ID
        override val confirms = false
        var sends = 0
            private set

        override fun start(sink: ChannelInputSink) = AutoCloseable {}
        override suspend fun send(peer: ChannelPeer, delivery: Delivery): SendOutcome {
            sends++
            return outcome
        }
    }

    private fun linkedStore(dir: File) = WhatsappStore(dir).apply {
        linked("15550001111@s.whatsapp.net")
        setEnabled(true)
    }

    @Test fun deliversToTheOwnChatWhileTheLinkStaysTheSame() = runTest {
        val store = linkedStore(tmp.newFolder())
        val channel = CountingChannel()
        val outbox = Outbox()
        val port = AndroidWhatsappOwnerPort(store, channel, outbox, "run-1")

        val result = port.send("Task result")

        assertEquals(WhatsappOwnerResult.Status.Delivered, result.status)
        assertEquals(1, channel.sends)
        val record = outbox.records().single()
        assertTrue(record.delivery.id.startsWith("owner-send:run-1:"))
        assertEquals(store.ownerPeer(), record.peer)
        assertEquals("15550001111@s.whatsapp.net", record.peer.peerId)
    }

    @Test fun relinkingAnotherAccountAfterTheSnapshotSendsNothing() = runTest {
        val store = linkedStore(tmp.newFolder())
        val channel = CountingChannel()
        val port = AndroidWhatsappOwnerPort(store, channel, Outbox(), "run-1")
        store.linked("15559998888@s.whatsapp.net")

        val result = port.send("Task result")

        assertEquals(WhatsappOwnerResult.Status.Unavailable, result.status)
        assertEquals(0, channel.sends)
    }

    @Test fun pausingWhatsappAfterTheSnapshotSendsNothing() = runTest {
        val store = linkedStore(tmp.newFolder())
        val channel = CountingChannel()
        val port = AndroidWhatsappOwnerPort(store, channel, Outbox(), "run-1")
        store.setEnabled(false)

        val result = port.send("Task result")

        assertEquals(WhatsappOwnerResult.Status.Unavailable, result.status)
        assertEquals(0, channel.sends)
    }

    @Test fun theSameTextTwiceIsDeliveredOnce() = runTest {
        val store = linkedStore(tmp.newFolder())
        val channel = CountingChannel()
        val port = AndroidWhatsappOwnerPort(store, channel, Outbox(), "run-1")

        val first = port.send("Task result")
        val second = port.send("Task result")

        assertEquals(WhatsappOwnerResult.Status.Delivered, first.status)
        assertEquals(first.status, second.status)
        assertEquals(1, channel.sends)
    }

    @Test fun anUncertainDeliveryIsNotResent() = runTest {
        val store = linkedStore(tmp.newFolder())
        val channel = CountingChannel(SendOutcome.Uncertain)
        val port = AndroidWhatsappOwnerPort(store, channel, Outbox(), "run-1")

        val first = port.send("Task result")
        val second = port.send("Task result")

        assertEquals(WhatsappOwnerResult.Status.Uncertain, first.status)
        assertEquals(WhatsappOwnerResult.Status.Uncertain, second.status)
        assertEquals(1, channel.sends)
    }

    @Test fun withoutALinkedWhatsappNothingIsSent() = runTest {
        val store = WhatsappStore(tmp.newFolder())
        val channel = CountingChannel()
        val port = AndroidWhatsappOwnerPort(store, channel, Outbox(), "run-1")

        val result = port.send("Task result")

        assertEquals(WhatsappOwnerResult.Status.Unavailable, result.status)
        assertEquals(0, channel.sends)
    }
}
