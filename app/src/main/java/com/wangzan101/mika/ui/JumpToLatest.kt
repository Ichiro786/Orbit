package com.wangzan101.mika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.FrostedDisc
import com.wangzan101.mika.ui.components.GlassBackdrop
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.pressScale

/** The jump-to-latest button: a 40dp frosted circle with a down arrow inside a 48dp touch target. */
@Composable
internal fun JumpToLatest(unread: Int, backdrop: GlassBackdrop?, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val c = MikaTheme.colors
    val label = if (unread > 0) stringResource(R.string.chat_jump_latest_new, unread)
        else stringResource(R.string.chat_jump_latest)
    val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        Modifier.size(MikaSize.TouchTarget).pressScale(press)
            .clip(CircleShape)
            .clickable(press, androidx.compose.foundation.LocalIndication.current, role = Role.Button, onClickLabel = label, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            })
            .semantics { contentDescription = label }
            .testTag("chat-jump-latest"),
        contentAlignment = Alignment.Center,
    ) {
        FrostedDisc(Modifier.size(MikaSize.IconButton), backdrop) {
            Icon(MikaIcons.Down, null, Modifier.align(Alignment.Center).size(MikaSize.Icon), tint = c.ink)
        }
        if (unread > 0) {
            Text(
                unreadBadge(unread),
                Modifier.align(Alignment.TopEnd).background(c.accent, MikaShapes.Pill)
                    .padding(horizontal = MikaSpace.S4).testTag("jump-to-latest-count").clearAndSetSemantics {},
                style = MikaType.Micro, color = c.onAccent,
            )
        }
    }
}
