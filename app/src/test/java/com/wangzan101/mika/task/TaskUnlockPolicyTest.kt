package com.wangzan101.mika.task

import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.whatsapp.WhatsappChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskUnlockPolicyTest {
    private val peer = ChannelPeer(WhatsappChannel.ID, "owner", "thread")

    @Test fun scheduledTasksKeepUnlockWithoutResolvingWhatsapp() {
        val authorized = taskUnlockAuthorization(RunOrigin.Trigger, null) {
            error("A scheduled task must not initialize WhatsApp")
        }

        assertTrue(requireNotNull(authorized).invoke())
    }

    @Test fun otherTaskOriginsCannotUseWhatsappPermission() {
        for (origin in listOf(RunOrigin.Ui, RunOrigin.Mcp, RunOrigin.Child)) {
            assertNull(taskUnlockAuthorization(origin, peer) {
                error("$origin must not read WhatsApp permission")
            })
        }
    }

    @Test fun missingOrOtherChannelPeersNeverResolveWhatsapp() {
        for (candidate in listOf(null, peer.copy(channelId = "local"), peer.copy(channelId = "mcp"))) {
            assertNull(taskUnlockAuthorization(RunOrigin.Channel, candidate) {
                error("A non-WhatsApp peer must not read WhatsApp permission")
            })
        }
    }

    @Test fun grantingPermissionLaterDoesNotChangeAManualWaitIntoAnUnlockAttempt() {
        var allowed = false
        var checks = 0
        val canUnlock: (ChannelPeer?) -> Boolean = { checks++; allowed }
        val waitingTask = taskUnlockAuthorization(RunOrigin.Channel, peer, canUnlockChannel = canUnlock)
        assertNull(waitingTask)
        assertEquals(1, checks)

        allowed = true
        assertNull(waitingTask)
        assertEquals(1, checks)
        val nextTask = taskUnlockAuthorization(RunOrigin.Channel, peer, canUnlockChannel = canUnlock)
        assertTrue(requireNotNull(nextTask).invoke())
    }

    /** Tasks started on this phone follow the local switch: the check re-reads it on every call instead of caching the value at start. */
    @Test fun uiTasksFollowTheLocalUnlockSwitchInRealTime() {
        var on = true
        var checks = 0
        val authorized = requireNotNull(taskUnlockAuthorization(RunOrigin.Ui, null, localUnlockEnabled = {
            checks++
            on
        }) {
            error("Ui tasks must not read channel permission")
        })
        assertTrue(authorized())
        on = false
        assertFalse(authorized())
        on = true
        assertTrue(authorized())
        assertEquals(4, checks)
    }

    /** MCP tasks never get unlock authorization, whatever the local switch says. */
    @Test fun mcpTasksStayUnauthorizedWhateverTheLocalSwitchSays() {
        for (on in listOf(false, true)) {
            assertNull(taskUnlockAuthorization(RunOrigin.Mcp, null, localUnlockEnabled = { on }) {
                error("Mcp tasks must not read channel permission")
            })
        }
    }

    @Test fun authorizedWhatsappTaskRechecksItsPeerAfterPermissionIsRevoked() {
        var allowed = true
        val checkedPeers = mutableListOf<ChannelPeer?>()
        val authorized = requireNotNull(taskUnlockAuthorization(RunOrigin.Channel, peer) {
            checkedPeers += it
            allowed
        })
        assertEquals(listOf(peer), checkedPeers)
        assertTrue(authorized())

        allowed = false
        assertFalse(authorized())
        assertEquals(listOf(peer, peer, peer), checkedPeers)
    }
}
