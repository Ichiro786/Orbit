package com.wangzan101.mika.task

import com.wangzan101.mika.core.run.ChannelPeer
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.whatsapp.WhatsappChannel

/**
 * Unlock for scheduled tasks, WhatsApp tasks the user allowed, or tasks started on this phone while the
 * local unlock switch is on; the permission is read again at unlock time. Other origins (MCP, subtasks) never get it.
 */
internal fun taskUnlockAuthorization(
    origin: RunOrigin,
    peer: ChannelPeer?,
    localUnlockEnabled: () -> Boolean = { false },
    canUnlockChannel: (ChannelPeer?) -> Boolean,
): (() -> Boolean)? = when {
    origin == RunOrigin.Trigger -> ({ true })
    origin == RunOrigin.Channel && peer?.channelId == WhatsappChannel.ID && canUnlockChannel(peer) ->
        ({ canUnlockChannel(peer) })
    origin == RunOrigin.Ui && localUnlockEnabled() -> ({ localUnlockEnabled() })
    else -> null
}
