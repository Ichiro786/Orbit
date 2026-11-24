package com.wangzan101.mika.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.toSize
import com.wangzan101.mika.R
import com.wangzan101.mika.chat.Project
import com.wangzan101.mika.chat.ProjectDeleteImpact
import com.wangzan101.mika.i18n.trPlural
import com.wangzan101.mika.ui.components.DirectionalIcon
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import com.wangzan101.mika.ui.components.floatShadow
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/**
 * Projects in the sidebar (upstream item 27): a named group of chats that share one sandbox workspace.
 * They sit after Spaces and look like channels: a folder tile, the name, and the project's chats nested under it.
 */
data class DrawerProjectGroup(val project: Project, val rows: List<DrawerConversation>)

/**
 * Each project with its chats, newest first. Archived, deleted, side chats and channels are left out; a chat whose
 * project no longer exists is not listed under any project (it stays a plain chat). Empty projects are kept.
 */
internal fun drawerProjectGroups(
    all: List<com.wangzan101.mika.chat.ConversationSummary>,
    projects: List<Project>,
    currentId: String?,
    canSwitch: Boolean,
    runningIds: Set<String>,
    deleted: Set<String>,
    now: Long,
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): List<DrawerProjectGroup> {
    val byProject = all.filter { it.id !in deleted && it.projectId != null && it.sideChat == null && it.channel == null && it.archivedAt == null }
        .sortedByDescending { it.updatedAt }.groupBy { it.projectId!! }
    return projects.map { project ->
        DrawerProjectGroup(project, byProject[project.id].orEmpty().map { summary ->
            drawerEntry(summary, currentId, canSwitch, summary.id in runningIds,
                if (summary.updatedAt != 0L) conversationTime(summary.updatedAt, now, zone) else "")
        })
    }
}

/** One project row: tap folds its chats away or back, the plus starts a chat in it, long-press opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ProjectEntry(
    group: DrawerProjectGroup,
    collapsed: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onActions: (Project, Rect) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val state = stringResource(if (collapsed) R.string.drawer_project_collapsed else R.string.drawer_project_expanded)
    Row(
        Modifier.padding(horizontal = MikaSpace.S12).fillMaxWidth()
            .onGloballyPositioned { bounds = Rect(it.positionInWindow(), it.size.toSize()) }
            .clip(NavShape)
            .combinedClickable(
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.drawer_project_actions),
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onActions(group.project, bounds) },
                onClick = onToggle,
            )
            .semantics { stateDescription = state }
            .heightIn(min = MikaSize.RowMin).padding(start = MikaSpace.S12, end = MikaSpace.S4, top = MikaSpace.S4, bottom = MikaSpace.S4)
            .testTag("project-header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        ProjectRowContent(group)
        DirectionalIcon(
            MikaIcons.Chevron, null,
            Modifier.size(MikaSize.SmallIcon).rotate(if (collapsed) 0f else 90f), tint = MikaTheme.colors.ink3,
        )
        Box(
            Modifier.size(MikaSize.TouchTarget).clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.drawer_project_new_chat), onClick = onNewChat)
                .testTag("project-new-chat"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(MikaIcons.Plus, stringResource(R.string.drawer_project_new_chat), Modifier.size(MikaSize.SmallIcon),
                tint = if (enabled) MikaTheme.colors.ink2 else MikaTheme.colors.ink3)
        }
    }
}

@Composable
private fun RowScope.ProjectRowContent(group: DrawerProjectGroup) {
    val c = MikaTheme.colors
    IconTile(MikaIcons.Folder, tileColor(group.project.id), tileColor(group.project.id).copy(alpha = 0.14f))
    Column(Modifier.weight(1f)) {
        Text(group.project.name, style = MikaType.Body.copy(fontWeight = FontWeight.Medium), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            if (group.rows.isEmpty()) stringResource(R.string.drawer_project_empty) else stringResource(R.string.drawer_project_sub),
            style = MikaType.Hint, color = c.ink3, maxLines = 1,
        )
    }
}

/** Long-press on a project: a new chat in it, rename, its workspace in the terminal, delete. */
@Composable
internal fun ProjectMenu(
    group: DrawerProjectGroup,
    bounds: Rect,
    progress: () -> Float,
    onNewChat: () -> Unit,
    onRename: () -> Unit,
    onTerminal: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MikaTheme.colors
    LongPressMenu(
        bounds, progress, pane = stringResource(R.string.drawer_project_actions), tag = "drawer-project-menu",
        entries = listOf(
            MenuEntry(stringResource(R.string.drawer_project_new_chat), MikaIcons.New, "project-menu-new-chat", onClick = onNewChat),
            MenuEntry(stringResource(R.string.drawer_project_rename_title), MikaIcons.Edit, "project-menu-rename", onClick = onRename),
            MenuEntry(stringResource(R.string.terminal_open), MikaIcons.Terminal, "project-menu-terminal", onClick = onTerminal),
            MenuEntry(stringResource(R.string.confirm_delete), MikaIcons.Trash, "project-menu-delete", c.bad, onDelete),
        ),
        onDismiss = onDismiss,
    ) {
        Row(
            Modifier.floatShadow(c, NavShape).background(c.surface, NavShape).clip(NavShape)
                .padding(horizontal = MikaSpace.S12, vertical = MikaSpace.S8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
        ) { ProjectRowContent(group) }
    }
}

/** Naming a new project, or renaming one. */
@Composable
internal fun ProjectNameDialog(
    title: String,
    initial: String,
    confirmTag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(initial) { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = value.text.isNotBlank()
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            KeyboardDismissArea {
                OutlinedTextField(
                    value, { value = it },
                    Modifier.fillMaxWidth().focusRequester(focus).keepsKeyboard().testTag("project-name-field"),
                    placeholder = { Text(stringResource(R.string.drawer_project_name_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (ok) onSave(value.text.trim()) }),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.text.trim()) }, Modifier.testTag(confirmTag), enabled = ok) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Deleting a project says plainly that its chats and shared workspace files go too. */
@Composable
internal fun DeleteProjectDialog(
    project: Project,
    impact: ProjectDeleteImpact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val body = listOfNotNull(
        stringResource(R.string.drawer_project_delete_body, project.name),
        if (impact.runningTasks > 0) trPlural(R.plurals.drawer_delete_many_running, impact.runningTasks, impact.runningTasks) else null,
        if (impact.schedules > 0) trPlural(R.plurals.drawer_delete_many_schedules, impact.schedules, impact.schedules) else null,
    ).joinToString("\n")
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_project_delete_title)) },
        text = { Text(body, Modifier.testTag("delete-project-body")) },
        confirmButton = {
            TextButton(onClick = onConfirm, Modifier.testTag("confirm-delete-project")) {
                Text(stringResource(R.string.confirm_delete), color = MikaTheme.colors.bad)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
