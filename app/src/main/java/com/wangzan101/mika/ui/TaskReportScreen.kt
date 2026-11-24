package com.wangzan101.mika.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.wangzan101.mika.R
import com.wangzan101.mika.ui.components.*
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaRadius
import com.wangzan101.mika.ui.theme.MikaShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun TaskReportEntries(onReport: () -> Unit, onReplay: () -> Unit) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth().padding(top = MikaSpace.S16), verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
        Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).background(c.surface)
            .clickable(onClick = onReport).padding(MikaSpace.S12), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Icon(MikaIcons.Doc, null, Modifier.size(MikaSpace.S24), tint = c.accent)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.report_title), style = MikaType.BodyStrong, color = c.ink)
                Text(stringResource(R.string.report_entry_hint), style = MikaType.Hint, color = c.ink2)
            }
            Icon(MikaIcons.Chevron, null, Modifier.size(MikaSize.SmallIcon), tint = c.ink3)
        }
        Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).clickable(onClick = onReplay)
            .heightIn(min = MikaSize.TouchTarget).padding(horizontal = MikaSpace.S12, vertical = MikaSpace.S8), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
            Icon(MikaIcons.Route, null, Modifier.size(MikaSize.TileIcon), tint = c.accent)
            Text(stringResource(R.string.playback_title), Modifier.weight(1f), style = MikaType.Caption, color = c.accent)
            Icon(MikaIcons.Chevron, null, Modifier.size(MikaSize.SmallIcon), tint = c.ink3)
        }
    }
}

@Composable
internal fun rememberReport(vm: TaskReportViewModel): Pair<Boolean, TaskReport?> {
    var loaded by remember(vm) { mutableStateOf(false) }
    var report by remember(vm) { mutableStateOf<TaskReport?>(null) }
    LaunchedEffect(vm) { report = withContext(Dispatchers.IO) { vm.load() }; loaded = true }
    return loaded to report
}

@Composable
fun TaskReportScreen(vm: TaskReportViewModel, onBack: () -> Unit, onReplay: (Int?) -> Unit, onTimeline: () -> Unit) {
    BackHandler(onBack = onBack)
    val (loaded, report) = rememberReport(vm)
    val c = MikaTheme.colors
    PageBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.report_title), MikaIcons.Back, onBack, stringResource(R.string.back),
                right = { ReportExportMenu(vm.runId, report) })
            val r = report
            if (r == null) {
                if (loaded) EmptyNote(TimelineUi.EXPIRED_TEXT, Modifier.padding(top = MikaSpace.S8 * 9))
            } else LazyColumn(Modifier.fillMaxSize().testTag("report-scroll"),
                contentPadding = PaddingValues(MikaSpace.S16, MikaSpace.S8, MikaSpace.S16, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MikaSpace.S20),
                verticalArrangement = Arrangement.spacedBy(MikaSpace.S16)) {
                item("document") {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(MikaRadius.Bubble)).background(c.surface).padding(MikaSpace.S20),
                        verticalArrangement = Arrangement.spacedBy(MikaSpace.S20)) {
                        Text(r.header.goal, style = MikaType.Title.copy(fontWeight = FontWeight.Bold), color = c.ink)
                        Row(Modifier.fillMaxWidth().clip(MikaShapes.Tile).background(c.accentSoft).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(MikaSpace.S4).fillMaxHeight().background(c.accent))
                            Text(r.header.outcome, Modifier.weight(1f).padding(MikaSpace.S12), style = MikaType.Label, color = c.accent)
                            r.header.duration?.let { Text(it, Modifier.padding(end = MikaSpace.S12), style = MikaType.Hint, color = c.ink2) }
                        }
                        if (r.header.criteria.isNotEmpty()) Text(r.header.criteria.joinToString(" · "), style = MikaType.Caption, color = c.ink2)
                        if (r.answer.isBlank()) Text(stringResource(R.string.report_no_answer), style = MikaType.Body, color = c.ink2)
                        else MarkdownText(TaskReportExport.displayMarkdown(r.answer, r.header.goal), MikaType.Body.copy(color = c.ink, lineHeight = MikaType.Reading.lineHeight), Modifier.fillMaxWidth(), reportStyle = true)
                    }
                }
                if (r.pages.isNotEmpty()) {
                    item("screens-heading") { Text(stringResource(R.string.report_screens), style = MikaType.BodyStrong, color = c.ink) }
                    itemsIndexed(r.pages, key = { _, p -> "page-${p.screenshotId}" }) { _, page ->
                        Row(Modifier.fillMaxWidth().clip(MikaShapes.Card).background(c.surface)
                            .clickable { onReplay(page.screenshotId) }.padding(MikaSpace.S12), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                            StaticPlaybackImage(vm, page.screenshotId, Modifier.size(MikaSize.TouchTarget, MikaSpace.S8 * 9))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
                                Text(page.title, style = MikaType.BodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(R.string.report_view_step, page.stepIndex + 1), style = MikaType.Hint, color = c.ink2)
                            }
                            Icon(MikaIcons.Chevron, null, Modifier.size(MikaSize.SmallIcon), tint = c.ink3)
                        }
                    }
                }
                item("replay") { MikaButton(stringResource(R.string.playback_title), { onReplay(null) }, Modifier.fillMaxWidth(), kind = ButtonKind.Primary) }
                item("timeline") { MikaButton(stringResource(R.string.chat_view_process), onTimeline, Modifier.fillMaxWidth(), kind = ButtonKind.Tonal, size = ButtonSize.Small) }
                item("privacy") { Text(stringResource(R.string.report_local_note), style = MikaType.Hint, color = c.ink2) }
            }
        }
    }
}
