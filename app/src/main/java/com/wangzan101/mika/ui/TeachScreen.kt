package com.wangzan101.mika.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wangzan101.mika.R
import com.wangzan101.mika.core.knowledge.FrontMatter
import com.wangzan101.mika.core.skills.PersonalSkill
import com.wangzan101.mika.core.teach.TeachPhase
import com.wangzan101.mika.core.teach.TeachSession
import com.wangzan101.mika.teach.TeachNotice
import com.wangzan101.mika.teach.TeachRuntime
import com.wangzan101.mika.teach.TeachUi
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.MikaCard
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.OrbitNote
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.components.OrbitFieldShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaSize

/** 总结页要显示的东西，和 runtime 分开，便于单独预览与测试。 */
internal data class TeachSummaryView(
    val display: String,
    val description: String,
    val body: String,
    val folded: List<String>,
    val basic: Boolean,
)

@Composable
fun TeachScreen(runtime: TeachRuntime, onBack: () -> Unit) {
    val ui by runtime.ui.collectAsState()
    val session = ui.session
    Column(Modifier.fillMaxSize().background(MikaTheme.colors.bg)) {
        TopBar(stringResource(R.string.teach_title), MikaIcons.Back, onBack, stringResource(R.string.back))
        Notice(ui.notice)
        val draft = ui.draft
        when (session?.phase) {
            null, TeachPhase.Idle, TeachPhase.Ready -> Intro(ui.hint.orEmpty(), runtime)
            TeachPhase.Recording, TeachPhase.Paused -> Live(runtime, session.phase, session.steps.size)
            TeachPhase.Interrupted -> Interrupted(runtime)
            TeachPhase.Drafting -> Summarizing(failed = false, notice = ui.notice, onRetry = runtime::retry, onDiscard = runtime::discard)
            TeachPhase.Reviewing -> if (draft != null) {
                TeachSummaryPage(
                    view = summaryView(draft, ui),
                    onAccept = { runtime.accept() },
                    onRedo = { teachAgain(runtime, session.goal) },
                    onDiscard = runtime::discard,
                )
            } else {
                Summarizing(failed = true, notice = ui.notice, onRetry = runtime::retry, onDiscard = runtime::discard)
            }
            // 收下以后带着新目标再进来:问的是新目标,不该停在上一份的「已学会」
            TeachPhase.Saved -> if (showDone(session, ui.hint)) TeachDonePage(
                name = ui.acceptedName ?: metaValue(draft, "display") ?: session.goal,
                description = metaValue(draft, "description").orEmpty(),
                onBackToChat = onBack,
                onTeachAgain = { teachAgain(runtime, session.goal) },
            ) else Intro(ui.hint.orEmpty(), runtime)
        }
    }
}

/** 已经收下、且没有带着新目标进来时，才停在「已学会」页。 */
internal fun showDone(session: TeachSession?, hint: String?): Boolean =
    session?.phase == TeachPhase.Saved && (hint.isNullOrBlank() || hint == session.goal)

@Composable
private fun Intro(hint: String, runtime: TeachRuntime) {
    val context = LocalContext.current
    TeachIntroPage(hint) { goal ->
        val problem = runtime.start(goal)
        if (problem == null) (context as? Activity)?.moveTaskToBack(true)
    }
}

@Composable
private fun Notice(notice: TeachNotice?, modifier: Modifier = Modifier) {
    val text = when (notice) {
        TeachNotice.Busy -> stringResource(R.string.teach_busy)
        TeachNotice.NoAccessibility -> stringResource(R.string.teach_need_a11y)
        TeachNotice.NeedGoal -> stringResource(R.string.teach_need_goal)
        TeachNotice.SecretGoal -> stringResource(R.string.teach_secret_goal)
        TeachNotice.DraftFailed -> stringResource(R.string.teach_draft_failed)
        TeachNotice.NoSteps -> stringResource(R.string.teach_no_steps)
        TeachNotice.SecretNote -> stringResource(R.string.teach_sensitive_note)
        TeachNotice.NoVisibleStop -> stringResource(R.string.teach_no_visible)
        TeachNotice.WatchLost -> stringResource(R.string.teach_watch_lost)
        null -> return
    }
    val c = MikaTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.warnSoft, OrbitFieldShape).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S12),
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        Icon(MikaIcons.Exclaim, null, Modifier.padding(top = 1.dp).size(MikaSize.TileIcon), tint = c.warn)
        Text(text, Modifier.weight(1f), style = MikaType.Caption, color = c.ink)
    }
}

/** Scrolling content with the actions pinned below it, clear of the keyboard and navigation bar. */
@Composable
private fun TeachFrame(
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Column(
            modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = MikaSpace.S24, end = MikaSpace.S24, top = MikaSpace.S12, bottom = MikaSpace.S16),
            verticalArrangement = Arrangement.spacedBy(MikaSpace.S16),
            content = content,
        )
        Column(
            Modifier.fillMaxWidth().padding(start = MikaSpace.S24, end = MikaSpace.S24, top = MikaSpace.S8, bottom = MikaSpace.S16),
            verticalArrangement = Arrangement.spacedBy(MikaSpace.S12),
            content = actions,
        )
    }
}

@Composable
private fun TeachHeading(title: String, body: String? = null) {
    Column {
        Text(title, style = MikaType.Title.copy(fontSize = 24.sp, lineHeight = 30.sp), color = MikaTheme.colors.ink, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, Modifier.padding(top = MikaSpace.S8), style = MikaType.Body, color = MikaTheme.colors.ink2)
    }
}

@Composable
private fun WideButton(text: String, onClick: () -> Unit, kind: ButtonKind = ButtonKind.Primary, enabled: Boolean = true, modifier: Modifier = Modifier) {
    MikaButton(text, onClick, modifier.fillMaxWidth(), kind = kind, size = ButtonSize.Large, enabled = enabled)
}

@Composable
internal fun TeachIntroPage(initial: String, onStart: (String) -> Unit) {
    var goal by remember(initial) { mutableStateOf(initial) }
    var detailed by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("teach-form"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.teach_intro_title), style = MikaType.Title, color = MikaTheme.colors.ink)
        Text(stringResource(R.string.teach_intro_body), style = MikaType.Body, color = MikaTheme.colors.ink2)
        OrbitTextField(
            goal, { goal = it },
            stringResource(R.string.teach_goal_label),
            Modifier.testTag("teach-goal"),
            placeholder = stringResource(R.string.teach_goal_hint),
            singleLine = false,
        )
        MikaCard(Modifier.clickable { detailed = !detailed }.testTag("teach-disclosure")) {
            Text(
                stringResource(if (detailed) R.string.teach_disclosure else R.string.teach_disclosure_short),
                Modifier.padding(12.dp),
                style = MikaType.Caption,
                color = MikaTheme.colors.ink2,
            )
        }
        MikaButton(
            stringResource(R.string.teach_start), { onStart(goal) },
            kind = ButtonKind.Primary, size = ButtonSize.Medium,
            modifier = Modifier.testTag("teach-start"),
        )
    }
}

@Composable
private fun Live(runtime: TeachRuntime, phase: TeachPhase, steps: Int) {
    val context = LocalContext.current
    TeachFrame(actions = {
        if (phase == TeachPhase.Recording) WideButton(stringResource(R.string.teach_pause), runtime::pause, kind = ButtonKind.Tonal)
        else WideButton(stringResource(R.string.teach_resume), {
            if (runtime.resume() == null) (context as? Activity)?.moveTaskToBack(true)
        })
        WideButton(stringResource(R.string.teach_end), runtime::end)
        WideButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Text)
    }) {
        TeachHeading(
            stringResource(if (phase == TeachPhase.Recording) R.string.teach_learning else R.string.teach_paused),
            stringResource(R.string.teach_notification),
        )
        StepCount(steps)
    }
}

/** The number of steps captured so far, as a large figure in a card. */
@Composable
private fun StepCount(steps: Int) {
    val c = MikaTheme.colors
    MikaCard {
        Row(Modifier.fillMaxWidth().padding(MikaSpace.S16), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Box(Modifier.size(10.dp).background(c.accent, CircleShape))
            Text(steps.toString(), style = MikaType.LargeTitle, color = c.ink)
        }
    }
}

@Composable
private fun Interrupted(runtime: TeachRuntime) {
    val context = LocalContext.current
    TeachFrame(actions = {
        WideButton(stringResource(R.string.teach_resume), {
            if (runtime.resume() == null) (context as? Activity)?.moveTaskToBack(true)
        })
        WideButton(stringResource(R.string.teach_end), runtime::end, kind = ButtonKind.Tonal)
        WideButton(stringResource(R.string.teach_discard), runtime::discard, kind = ButtonKind.Text)
    }) {
        Text(stringResource(R.string.teach_interrupted), style = MikaType.Body, color = MikaTheme.colors.ink)
    }
}

@Composable
internal fun Summarizing(failed: Boolean, notice: TeachNotice?, onRetry: () -> Unit, onDiscard: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (failed) {
            Text(
                stringResource(if (notice == TeachNotice.NoSteps) R.string.teach_no_steps_again else R.string.teach_draft_failed),
                style = MikaType.Title, color = MikaTheme.colors.ink,
            )
            MikaButton(
                stringResource(R.string.teach_retry), onRetry,
                kind = ButtonKind.Primary, size = ButtonSize.Medium,
                modifier = Modifier.testTag("teach-retry"),
            )
        } else {
            Text(stringResource(R.string.teach_summarizing), style = MikaType.Title, color = MikaTheme.colors.ink)
            Text(stringResource(R.string.teach_summary_head), style = MikaType.Body, color = MikaTheme.colors.ink2)
        }
        MikaButton(
            stringResource(R.string.teach_discard), onDiscard, kind = ButtonKind.Tonal, size = ButtonSize.Medium,
            modifier = Modifier.testTag("teach-discard"),
        )
    }
}

@Composable
internal fun TeachSummaryPage(
    view: TeachSummaryView,
    onAccept: () -> Unit,
    onRedo: () -> Unit,
    onDiscard: () -> Unit,
) {
    var foldedOpen by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 8.dp).testTag("teach-summary"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.teach_summary_ready), style = MikaType.Label, color = MikaTheme.colors.accent)
            Text(view.display, style = MikaType.Title, color = MikaTheme.colors.ink)
            if (view.description.isNotBlank()) {
                Text(view.description, style = MikaType.Caption, color = MikaTheme.colors.ink2)
            }
            Text(stringResource(R.string.teach_summary_head), style = MikaType.Hint, color = MikaTheme.colors.ink3)
            if (view.folded.isNotEmpty()) {
                MikaCard(Modifier.clickable { foldedOpen = !foldedOpen }.testTag("teach-folded")) {
                    Text(
                        stringResource(R.string.teach_folded_n, view.folded.size),
                        Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MikaType.Label, color = MikaTheme.colors.ink,
                    )
                    if (foldedOpen) {
                        view.folded.forEach {
                            Text(
                                "· $it",
                                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
                                style = MikaType.Hint, color = MikaTheme.colors.ink3,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            if (view.basic) {
                Text(stringResource(R.string.teach_basic_note), style = MikaType.Caption, color = MikaTheme.colors.warn)
            }
            SkillReadingView(view.body)
            Spacer(Modifier.height(8.dp))
        }
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MikaButton(
                stringResource(R.string.teach_accept), onAccept,
                kind = ButtonKind.Primary, size = ButtonSize.Medium,
                modifier = Modifier.testTag("teach-accept"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    stringResource(R.string.teach_redo),
                    Modifier.clickable(onClick = onRedo).padding(8.dp).testTag("teach-redo"),
                    style = MikaType.Label, color = MikaTheme.colors.ink3,
                )
                Text(
                    stringResource(R.string.teach_discard),
                    Modifier.clickable {
                        if (confirmDiscard) onDiscard() else confirmDiscard = true
                    }.padding(8.dp).testTag("teach-discard"),
                    style = MikaType.Label,
                    color = if (confirmDiscard) MikaTheme.colors.warn else MikaTheme.colors.ink3,
                )
            }
        }
    }
}

@Composable
internal fun TeachDonePage(
    name: String,
    description: String,
    onBackToChat: () -> Unit,
    onTeachAgain: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(20.dp).testTag("teach-done"),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.teach_accepted_title), style = MikaType.Label, color = MikaTheme.colors.accent)
        Text(
            stringResource(R.string.teach_accepted_body, name),
            style = MikaType.Title, color = MikaTheme.colors.ink, textAlign = TextAlign.Center,
        )
        if (description.isNotBlank()) {
            Text(description, style = MikaType.Body, color = MikaTheme.colors.ink2, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(8.dp))
        MikaButton(
            stringResource(R.string.teach_back_chat), onBackToChat,
            kind = ButtonKind.Primary, size = ButtonSize.Medium,
            modifier = Modifier.testTag("teach-back-chat"),
        )
        MikaButton(
            stringResource(R.string.teach_teach_again), onTeachAgain,
            kind = ButtonKind.Tonal, size = ButtonSize.Medium,
            modifier = Modifier.testTag("teach-again"),
        )
    }
}

private fun teachAgain(runtime: TeachRuntime, goal: String) {
    runtime.discard()
    runtime.prefill(goal)
}

private fun summaryView(draft: PersonalSkill, ui: TeachUi) = TeachSummaryView(
    display = metaValue(draft, "display") ?: draft.name,
    description = metaValue(draft, "description").orEmpty(),
    body = skillPreviewBody(draft.markdown),
    folded = ui.folded,
    basic = ui.basic,
)

private fun metaValue(draft: PersonalSkill?, field: String): String? =
    draft?.let { (FrontMatter.parse(it.markdown).first[field] as? String)?.takeIf { v -> v.isNotBlank() } }
