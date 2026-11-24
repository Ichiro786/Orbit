package com.wangzan101.mika.ui

import com.wangzan101.mika.chat.ConversationSummary
import com.wangzan101.mika.chat.Project
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/** Projects in Orbit's sidebar: each project lists its own chats, newest first; other chats stay out. */
class DrawerProjectsTest {
    private val zone = ZoneOffset.UTC

    @Test fun groupsChatsUnderTheirProjectNewestFirst() {
        val research = Project(id = "p1", name = "Research")
        val empty = Project(id = "p2", name = "Empty")
        val all = listOf(
            ConversationSummary("older", "Older", 10L, projectId = research.id),
            ConversationSummary("newer", "Newer", 30L, projectId = research.id),
            ConversationSummary("solo", "Not in a project", 20L),
            ConversationSummary("orphan", "Project deleted", 15L, projectId = "missing"),
            ConversationSummary("archived", "Archived", 40L, archivedAt = 41L, projectId = research.id),
            ConversationSummary("gone", "Deleted", 50L, projectId = research.id),
        )
        val groups = drawerProjectGroups(all, listOf(research, empty), currentId = "older", canSwitch = true,
            runningIds = setOf("newer"), deleted = setOf("gone"), now = 100L, zone = zone)

        assertEquals(listOf("Research", "Empty"), groups.map { it.project.name })
        assertEquals(listOf("newer", "older"), groups[0].rows.map { it.id })
        assertEquals(listOf(true, false), groups[0].rows.map { it.running })
        assertEquals(listOf(false, true), groups[0].rows.map { it.current })
        assertEquals(emptyList<String?>(), groups[1].rows.map { it.id })
    }

    @Test fun whenOnlyTheCurrentChatMaySwitchOtherRowsAreDisabled() {
        val p = Project(id = "p", name = "P")
        val all = listOf(
            ConversationSummary("a", "A", 2L, projectId = p.id),
            ConversationSummary("b", "B", 1L, projectId = p.id),
        )
        val rows = drawerProjectGroups(all, listOf(p), currentId = "b", canSwitch = false, runningIds = emptySet(),
            deleted = emptySet(), now = 3L, zone = zone).single().rows
        assertEquals(listOf(false, true), rows.map { it.enabled })
    }
}
