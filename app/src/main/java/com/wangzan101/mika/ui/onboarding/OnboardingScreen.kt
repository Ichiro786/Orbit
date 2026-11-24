package com.wangzan101.mika.ui.onboarding

import com.wangzan101.mika.ui.theme.rememberMikaPulse
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaRadius
import com.wangzan101.mika.ui.theme.MikaSpace
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.R
import com.wangzan101.mika.core.shell.BuiltinShellStatus
import com.wangzan101.mika.settings.AccessibilityState
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.Prerequisites
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.settings.SystemStatus
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.ui.Fix
import com.wangzan101.mika.ui.PhoneSetupChrome
import com.wangzan101.mika.ui.PhoneSetupScreen
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.identity.MikaMark
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.launch

/** Shapes and type the flow uses on top of the app's tokens: a slightly rounder card, and its page title. */
internal val FlowCard = MikaShapes.Card

/**
 * The first-run flow: welcome, what Orbit can do, privacy, a model, phone control, permissions, optional extras,
 * done. Calm, one thing per page.
 * - A progress bar and a back button on every step; Skip on every optional one.
 * - The step is saved as the user moves, so a killed app reopens where it was ([replay] never touches that).
 * - [replay] is the same flow opened from Settings: nothing is marked, leaving goes back to Settings.
 * Every action goes to the handlers that already exist: models through the model setup view model, phone control
 * through the pairing guide, permissions through [onFix].
 */
@Composable
fun OnboardingScreen(
    store: ProviderStore,
    status: SystemStatus,
    onFix: (Fix) -> Unit,
    progress: OnboardingProgress,
    replay: Boolean,
    /** Where a replay picks up after a detour (linking WhatsApp); the first run reads its saved step instead. */
    startAt: FlowStep? = null,
    onStep: (FlowStep) -> Unit = {},
    whatsappLinked: Boolean,
    onLinkWhatsapp: () -> Unit,
    onOpenSource: () -> Unit,
    /** Back on the first page: out of the flow. */
    onLeave: () -> Unit,
    onFinish: () -> Unit,
) {
    val reduceMotion = MikaTheme.reduceMotion
    val steps = remember(status.sdkInt) { OnboardingFlow.steps(phoneAvailable = status.sdkInt >= Prerequisites.MIN_SDK) }
    var step by rememberSaveable { mutableStateOf(OnboardingFlow.resumeAt(if (replay) startAt else progress.step(), steps)) }
    var forward by remember { mutableStateOf(true) }
    fun go(to: FlowStep?, ahead: Boolean) {
        if (to == null) return
        forward = ahead
        step = to
        if (!replay) progress.saveStep(to)
        onStep(to)
    }
    val next: () -> Unit = { go(OnboardingFlow.next(step, steps), ahead = true) }
    val back: () -> Unit = { OnboardingFlow.previous(step, steps)?.let { go(it, ahead = false) } ?: onLeave() }
    val phoneReady = status.builtinShell == BuiltinShellStatus.Running

    PageBackground {
        AnimatedContent(
            step, Modifier.fillMaxSize(),
            transitionSpec = {
                if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
                else {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally(MikaMotion.specs(reduceMotion).offset()) { dir * it / 3 } + MikaMotion.specs(reduceMotion).fadeEnter()) togetherWith
                        (slideOutHorizontally(MikaMotion.specs(reduceMotion).offset()) { -dir * it / 4 } + MikaMotion.specs(reduceMotion).fadeExit())
                }
            },
            label = "onboarding-step",
        ) { shown ->
            val header: @Composable (onBack: () -> Unit, skipLabel: String?, onSkip: () -> Unit) -> Unit = { onBack, skipLabel, onSkip ->
                FlowHeader(OnboardingFlow.position(shown, steps), steps.size, onBack.takeIf { shown != FlowStep.Welcome }, skipLabel, onSkip)
            }
            val skip = stringResource(R.string.ob_skip).takeIf { OnboardingFlow.optional(shown) }
            when (shown) {
                FlowStep.Welcome -> {
                    BackHandler(onBack = back)
                    WelcomeStep(reduceMotion, next)
                }
                FlowStep.CanDo -> {
                    BackHandler(onBack = back)
                    CanDoStep(reduceMotion, header = { header(back, skip, next) }, onDone = next)
                }
                FlowStep.Privacy -> {
                    BackHandler(onBack = back)
                    PrivacyStep(header = { header(back, skip, next) }, onOpenSource, next)
                }
                FlowStep.Model -> ModelStep(store, header = { onBack -> header(onBack, null, next) }, onBack = back, onContinue = next)
                FlowStep.Phone -> PhoneSetupScreen(
                    status, repair = false, onFix = onFix, onboarding = true,
                    chrome = PhoneSetupChrome(
                        header = { onBack, onLater -> header(onBack, stringResource(R.string.ob_later).takeIf { !phoneReady }, onLater) },
                        onBack = back,
                    ),
                    onClose = next,
                )
                FlowStep.Permissions -> {
                    BackHandler(onBack = back)
                    PermissionsStep(status, onFix, header = { header(back, skip, next) }, next)
                }
                FlowStep.Personal -> {
                    BackHandler(onBack = back)
                    PersonalStep(progress, whatsappLinked, onLinkWhatsapp, header = { header(back, skip, next) }, next)
                }
                FlowStep.Done -> {
                    BackHandler(onBack = back)
                    DoneStep(
                        store, progress.name(), phoneReady, whatsappLinked, reduceMotion,
                        header = { header(back, null, next) }, onFinish = onFinish,
                    )
                }
            }
        }
    }
}

// ---------- Frame ----------

/** Back (when there is somewhere to go back to), a segmented progress bar, and Skip or Later on optional steps. */
@Composable
internal fun FlowHeader(position: Int, total: Int, onBack: (() -> Unit)?, trailing: String?, onTrailing: () -> Unit) {
    val c = MikaTheme.colors
    val label = stringResource(R.string.ob_step_of, position, total)
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = MikaSpace.S12, end = MikaSpace.S12, top = MikaSpace.S8),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        if (onBack != null) Box(
            Modifier.size(MikaSize.TouchTarget).clip(CircleShape).background(c.fill).border(0.75.dp, c.glassEdge, CircleShape)
                .clickable(onClickLabel = stringResource(R.string.back), role = Role.Button, onClick = onBack).testTag("onboarding-back"),
            contentAlignment = Alignment.Center,
        ) { Icon(MikaIcons.Back, stringResource(R.string.back), Modifier.size(MikaSize.Icon), tint = c.ink) }
        else Spacer(Modifier.size(MikaSize.TouchTarget))
        Row(
            Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 1f..total.toFloat(), total - 2)
            }.testTag("onboarding-progress"),
            horizontalArrangement = Arrangement.spacedBy(MikaSpace.S4),
        ) {
            repeat(total) { i ->
                Box(Modifier.weight(1f).height(MikaSpace.S4).background(if (i < position) c.ink else c.ink.copy(alpha = 0.18f), CircleShape))
            }
        }
        if (trailing != null) Box(
            Modifier.heightIn(min = MikaSize.TouchTarget).widthIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
                .clickable(role = Role.Button, onClick = onTrailing).padding(horizontal = MikaSpace.S8).testTag("onboarding-skip"),
            contentAlignment = Alignment.Center,
        ) { Text(trailing, style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.ink2, maxLines = 1) }
        else Spacer(Modifier.size(MikaSize.TouchTarget))
    }
}

/**
 * The flow's main button: the page's ink on its background (off-white on black in the dark theme), so it reads
 * at full contrast whatever accent the wallpaper gives and never competes with the coral mark.
 */
@Composable
internal fun FlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = MikaTheme.colors
    Box(
        modifier.fillMaxWidth().heightIn(min = MikaSize.Button).clip(MikaShapes.Button)
            .background(if (enabled) c.ink else c.ink.copy(alpha = 0.12f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = MikaSpace.S24, vertical = MikaSpace.S12),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MikaType.BodyStrong, textAlign = TextAlign.Center,
            color = if (enabled) c.bg else c.ink3)
    }
}

/** A quiet "do this later" under a step: a text button and one line on what still works. */
@Composable
internal fun FlowLater(label: String, note: String, onClick: () -> Unit, tag: String) {
    val c = MikaTheme.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill).clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = MikaSpace.S24).testTag(tag),
            contentAlignment = Alignment.Center,
        ) { Text(label, style = MikaType.BodyStrong, color = c.ink) }
        Text(note, style = MikaType.Caption, color = c.ink3, textAlign = TextAlign.Center)
    }
}

/** A step's page: header, scrolling content with the flow's side margins, and the bottom bar above the gesture area. */
@Composable
internal fun FlowPage(
    header: @Composable () -> Unit,
    bottom: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        header()
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = MikaSpace.S24, end = MikaSpace.S24, top = MikaSpace.S24, bottom = MikaSpace.S8),
            content = content,
        )
        Column(Modifier.fillMaxWidth().padding(start = MikaSpace.S24, end = MikaSpace.S24, top = MikaSpace.S12, bottom = MikaSpace.S16), content = bottom)
    }
}

@Composable
internal fun FlowTitleBlock(title: String, lead: String?) {
    val c = MikaTheme.colors
    Text(title, Modifier.semantics { heading() }, style = MikaType.LargeTitle, color = c.ink)
    if (lead != null) Text(lead, Modifier.padding(top = MikaSpace.S12), style = MikaType.Body, color = c.ink2)
}

/** A frosted card: a soft fill and a hairline, in the flow's rounder shape. */
@Composable
internal fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = MikaTheme.colors
    Column(modifier.fillMaxWidth().clip(FlowCard).background(c.fill).border(0.75.dp, c.glassEdge, FlowCard), content = content)
}

@Composable
private fun IconSquare(icon: ImageVector, tint: Color, fill: Color) {
    Box(Modifier.size(MikaSize.IconButton).background(fill, RoundedCornerShape(MikaRadius.Md)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(MikaSize.Icon), tint = tint)
    }
}

/** A small state pill: a dot or check and a word, in the state's colour and never colour alone. */
@Composable
internal fun StatePill(text: String, color: Color, soft: Color, pulsing: Boolean = false, check: Boolean = false, modifier: Modifier = Modifier) {
    val pulse = if (pulsing) rememberMikaPulse(label = "pill-dot") else null
    Row(
        modifier.heightIn(min = MikaSpace.S32).background(soft, MikaShapes.Pill).padding(start = MikaSpace.S12, end = MikaSpace.S12, top = MikaSpace.S4, bottom = MikaSpace.S4),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8),
    ) {
        if (check) Icon(MikaIcons.Check, null, Modifier.size(MikaSize.SmallIcon), tint = color)
        else Box(Modifier.size(MikaSpace.S8).graphicsLayer { alpha = pulse?.value?.let { 0.35f + 0.65f * it } ?: 1f }.background(color, CircleShape))
        Text(text, style = MikaType.LabelStrong, color = color)
    }
}

// ---------- 1 Welcome ----------

/**
 * The orb rises and settles on a spring, then the title, the line and the button fade up in turn; afterwards only
 * a slow breathing glow. With animations off everything is simply there.
 */
@Composable
private fun WelcomeStep(reduceMotion: Boolean, onStart: () -> Unit) {
    val c = MikaTheme.colors
    val orb = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val text = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val cta = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        launch { orb.animateTo(1f, MikaMotion.specs(reduceMotion).spatialSlow()) }
        launch { text.animateTo(1f, MikaMotion.specs(reduceMotion).timed(MikaMotion.Duration.MEDIUM, delayMs = MikaMotion.Duration.MICRO)) }
        launch { cta.animateTo(1f, MikaMotion.specs(reduceMotion).timed(MikaMotion.Duration.MEDIUM, delayMs = MikaMotion.Duration.SHORT)) }
    }
    val breathe = rememberMikaPulse(label = "welcome-glow")
    val glow = c.accent
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MikaSpace.S32),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(MikaSize.IntroWelcomeHalo).drawBehind {
                    val a = (0.75f + 0.25f * breathe.value) * orb.value.coerceIn(0f, 1f)
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f * a), glow.copy(alpha = 0.10f * a), Color.Transparent)))
                },
                contentAlignment = Alignment.Center,
            ) {
                MikaMark(
                    Modifier.size(MikaSize.IntroMark).graphicsLayer {
                        val p = orb.value
                        scaleX = 0.55f + 0.45f * p; scaleY = 0.55f + 0.45f * p
                        translationY = (1f - p) * MikaSize.TouchTarget.toPx()
                        alpha = p.coerceIn(0f, 1f)
                    }.clearAndSetSemantics {},
                )
            }
            Column(
                Modifier.graphicsLayer { alpha = text.value; translationY = (1f - text.value) * MikaSpace.S12.toPx() },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.welcome_title), Modifier.padding(top = MikaSpace.S4).semantics { heading() },
                    style = MikaType.LargeTitle, color = c.ink, textAlign = TextAlign.Center)
                Text(stringResource(R.string.ob_welcome_lead), Modifier.padding(top = MikaSpace.S12),
                    style = MikaType.Title, color = c.ink2, textAlign = TextAlign.Center)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = MikaSpace.S24, end = MikaSpace.S24, bottom = MikaSpace.S16)
                .graphicsLayer { alpha = cta.value; translationY = (1f - cta.value) * MikaSpace.S12.toPx() },
            verticalArrangement = Arrangement.spacedBy(MikaSpace.S16),
        ) {
            Text(stringResource(R.string.ob_welcome_note), Modifier.fillMaxWidth(), style = MikaType.Caption, color = c.ink3, textAlign = TextAlign.Center)
            FlowButton(stringResource(R.string.welcome_start), onStart, Modifier.testTag("welcome-start"))
        }
    }
}

// ---------- 2 What Orbit can do ----------

@Composable
private fun CanDoStep(reduceMotion: Boolean, header: @Composable () -> Unit, onDone: () -> Unit) {
    val c = MikaTheme.colors
    val pager = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val pageLabel = stringResource(R.string.ob_card_of, pager.currentPage + 1, 3)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        header()
        HorizontalPager(
            pager, Modifier.weight(1f).fillMaxWidth().padding(top = MikaSpace.S24).testTag("onboarding-cards"),
            contentPadding = PaddingValues(start = MikaSpace.S24, end = MikaSpace.S32), pageSpacing = MikaSpace.S12,
        ) { page ->
            GlassCard(Modifier.fillMaxSize().clip(RoundedCornerShape(MikaRadius.Xxl))) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = MikaSpace.S16, end = MikaSpace.S16, top = MikaSpace.S24)) {
                    when (page) {
                        0 -> MessageScene()
                        1 -> SummaryScene()
                        else -> BackgroundScene()
                    }
                }
                Column(Modifier.padding(start = MikaSpace.S24, end = MikaSpace.S24, top = MikaSpace.S16, bottom = MikaSpace.S24)) {
                    val (title, body) = when (page) {
                        0 -> R.string.ob_can1_title to R.string.ob_can1_body
                        1 -> R.string.ob_can2_title to R.string.ob_can2_body
                        else -> R.string.ob_can3_title to R.string.ob_can3_body
                    }
                    Text(stringResource(title), Modifier.semantics { heading() }, style = MikaType.Headline, color = c.ink)
                    Text(stringResource(body), Modifier.padding(top = MikaSpace.S8), style = MikaType.Body, color = c.ink2)
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = MikaSpace.S24, end = MikaSpace.S24, top = MikaSpace.S16, bottom = MikaSpace.S16), verticalArrangement = Arrangement.spacedBy(MikaSpace.S16)) {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = pageLabel },
                horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8, Alignment.CenterHorizontally),
            ) {
                repeat(3) { i ->
                    Box(Modifier.height(MikaSpace.S8).width(if (i == pager.currentPage) MikaSpace.S24 else MikaSpace.S8).background(if (i == pager.currentPage) c.ink else c.ink.copy(alpha = 0.28f), CircleShape))
                }
            }
            val last = pager.currentPage == 2
            FlowButton(stringResource(if (last) R.string.ob_continue else R.string.next), {
                if (last) onDone() else scope.launch {
                    if (reduceMotion) pager.scrollToPage(pager.currentPage + 1) else pager.animateScrollToPage(pager.currentPage + 1)
                }
            }, Modifier.testTag("onboarding-next"))
        }
    }
}

@Composable
private fun UserLine(text: String) {
    val tone = MikaTheme.userBubble
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(text, Modifier.widthIn(max = MikaSize.IntroExampleBubble).background(tone.bg, MikaShapes.UserBubble).padding(horizontal = MikaSpace.S12, vertical = MikaSpace.S12),
            style = MikaType.Body, color = tone.fg)
    }
}

@Composable
private fun DoneLineSmall(text: String) {
    val c = MikaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
        Icon(MikaIcons.Check, null, Modifier.size(MikaSize.SmallIcon), tint = c.ok)
        Text(text, style = MikaType.Caption, color = c.ink2)
    }
}

/** Example: asking Orbit to message someone, the steps it takes, and the question it asks before sending. */
@Composable
private fun MessageScene() {
    val c = MikaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
        UserLine(stringResource(R.string.ob_can1_ask))
        Column(Modifier.padding(horizontal = 2.dp, vertical = MikaSpace.S4), verticalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
            DoneLineSmall(stringResource(R.string.ob_can1_step1))
            DoneLineSmall(stringResource(R.string.ob_can1_step2))
            DoneLineSmall(stringResource(R.string.ob_can1_step3))
        }
        Column(Modifier.fillMaxWidth().background(c.answer, MikaShapes.AnswerBubble).padding(MikaSpace.S12), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Text(stringResource(R.string.ob_can1_confirm), style = MikaType.CalloutStrong, color = c.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                Box(Modifier.width(2.dp).height(MikaSize.IconButton).background(c.ink.copy(alpha = 0.2f)))
                Text(stringResource(R.string.ob_can1_message), style = MikaType.Callout, color = c.ink2)
            }
            // A picture of the approval, not working buttons: TalkBack reads the texts, nothing here is a control.
            Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
                Text(stringResource(R.string.chat_send), Modifier.background(c.ink, MikaShapes.Pill).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S8),
                    style = MikaType.CalloutStrong, color = c.bg)
                Text(stringResource(R.string.ob_edit), Modifier.background(c.fill, MikaShapes.Pill).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S8),
                    style = MikaType.CalloutStrong, color = c.ink)
            }
        }
    }
}

/** Example: a summary of unread chats, one line per chat. */
@Composable
private fun SummaryScene() {
    val c = MikaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
        UserLine(stringResource(R.string.ob_can2_ask))
        Column(Modifier.fillMaxWidth(0.92f).background(c.answer, MikaShapes.AnswerBubble).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S12), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Text(stringResource(R.string.ob_can2_lead), style = MikaType.Callout, color = c.ink)
            listOf(
                R.string.ob_can2_n1 to R.string.ob_can2_t1, R.string.ob_can2_n2 to R.string.ob_can2_t2, R.string.ob_can2_n3 to R.string.ob_can2_t3,
            ).forEach { (name, line) ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(name), style = MikaType.CalloutStrong, color = c.ink)
                    Text(stringResource(line), style = MikaType.Callout, color = c.ink2)
                }
            }
        }
        Box(Modifier.padding(start = MikaSpace.S4)) { DoneLineSmall(stringResource(R.string.ob_can2_meta)) }
    }
}

/** Example: one task running with real progress, one scheduled, one done. */
@Composable
private fun BackgroundScene() {
    val c = MikaTheme.colors
    @Composable fun label(text: String) = Text(text.uppercase(), Modifier.padding(start = 2.dp), style = MikaType.Overline, color = c.ink3)
    Column(verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
        label(stringResource(R.string.personal_status_running))
        Column(Modifier.fillMaxWidth().background(c.answer, MikaShapes.Card).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S12), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Text(stringResource(R.string.ob_can3_task), style = MikaType.BodyStrong, color = c.ink)
            Text(stringResource(R.string.ob_can3_progress), style = MikaType.Caption, color = c.ink2)
            Box(Modifier.fillMaxWidth().height(MikaSpace.S4).background(c.ink.copy(alpha = 0.12f), CircleShape)) {
                Box(Modifier.fillMaxWidth(0.56f).height(MikaSpace.S4).background(c.accent, CircleShape))
            }
        }
        Box(Modifier.padding(top = MikaSpace.S8)) { label(stringResource(R.string.tasks_up_next)) }
        Row(Modifier.fillMaxWidth().background(c.answer, MikaShapes.Card).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S12), horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Icon(MikaIcons.Clock, null, Modifier.padding(top = 1.dp).size(MikaSpace.S24), tint = c.ink2)
            Column(verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
                Text(stringResource(R.string.ob_can3_next), style = MikaType.BodyStrong, color = c.ink)
                Text(stringResource(R.string.ob_can3_when), style = MikaType.Caption, color = c.ink2)
            }
        }
        Box(Modifier.padding(start = 2.dp, top = MikaSpace.S4)) { DoneLineSmall(stringResource(R.string.ob_can3_done)) }
    }
}

// ---------- 3 Privacy ----------

@Composable
private fun PrivacyStep(header: @Composable () -> Unit, onOpenSource: () -> Unit, onContinue: () -> Unit) {
    val c = MikaTheme.colors
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_continue), onContinue, Modifier.testTag("onboarding-next")) }) {
        FlowTitleBlock(stringResource(R.string.ob_privacy_title), stringResource(R.string.ob_privacy_lead))
        GlassCard(Modifier.padding(top = MikaSpace.S24)) {
            PrivacyRow(MikaIcons.Code, c.ink, c.fill, R.string.ob_privacy_open, R.string.ob_privacy_open_body)
            Box(Modifier.padding(start = (MikaSize.IconButton * 2)).fillMaxWidth().height(0.75.dp).background(c.hair))
            PrivacyRow(MikaIcons.Phone, c.ok, c.okSoft, R.string.ob_privacy_local, R.string.ob_privacy_local_body)
            Box(Modifier.padding(start = (MikaSize.IconButton * 2)).fillMaxWidth().height(0.75.dp).background(c.hair))
            PrivacyRow(MikaIcons.External, c.warn, c.warnSoft, R.string.ob_privacy_sent, R.string.ob_privacy_sent_body)
        }
        Row(
            Modifier.padding(top = MikaSpace.S8).heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
                .clickable(role = Role.Button, onClick = onOpenSource).padding(horizontal = MikaSpace.S4).testTag("onboarding-source"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8),
        ) {
            Text(stringResource(R.string.ob_privacy_source), style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.accent)
            Icon(MikaIcons.External, null, Modifier.size(MikaSize.SmallIcon), tint = c.accent)
        }
    }
}

@Composable
private fun PrivacyRow(icon: ImageVector, tint: Color, fill: Color, title: Int, body: Int) {
    val c = MikaTheme.colors
    Row(Modifier.fillMaxWidth().padding(MikaSpace.S24), horizontalArrangement = Arrangement.spacedBy(MikaSpace.S16)) {
        IconSquare(icon, tint, fill)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
            Text(stringResource(title), Modifier.semantics { heading() }, style = MikaType.BodyStrong, color = c.ink)
            Text(stringResource(body), style = MikaType.Body, color = c.ink2)
        }
    }
}

// ---------- 5 Phone control (the frame around the existing pairing guide) ----------

/** Title, one plain paragraph and the live state above the pairing guide's step list; a settled check once connected. */
@Composable
internal fun PhoneStepIntro(connected: Boolean) {
    val c = MikaTheme.colors
    Column(Modifier.padding(start = MikaSpace.S8, end = MikaSpace.S8, top = MikaSpace.S24, bottom = MikaSpace.S16)) {
        if (connected) {
            Box(Modifier.padding(bottom = MikaSpace.S12).size((MikaSpace.S32 * 2)).background(c.ok, CircleShape), contentAlignment = Alignment.Center) {
                Icon(MikaIcons.Check, null, Modifier.size(MikaSpace.S32), tint = c.bg)
            }
            FlowTitleBlock(stringResource(R.string.ob_phone_on_title), stringResource(R.string.ob_phone_on_body))
            StatePill(stringResource(R.string.phone_connected), c.ok, c.okSoft, check = true, modifier = Modifier.padding(top = MikaSpace.S16).testTag("onboarding-phone-state"))
        } else {
            FlowTitleBlock(stringResource(R.string.ob_phone_title), stringResource(R.string.ob_phone_lead))
            StatePill(stringResource(R.string.ob_phone_not_connected), c.warn, c.warnSoft, pulsing = true, modifier = Modifier.padding(top = MikaSpace.S16).testTag("onboarding-phone-state"))
        }
    }
}

// ---------- 6 Permissions ----------

/**
 * Nothing is requested just by arriving here. Each row says in one line why Orbit wants it and shows whether it is
 * already allowed; the buttons hand over to the system's own prompt or settings page.
 */
@Composable
private fun PermissionsStep(status: SystemStatus, onFix: (Fix) -> Unit, header: @Composable () -> Unit, onContinue: () -> Unit) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    var exact by remember { mutableStateOf(TriggerRuntime.canScheduleExactly(context)) }
    LifecycleResumeEffect(Unit) {
        exact = TriggerRuntime.canScheduleExactly(context)
        onPauseOrDispose {}
    }
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_continue), onContinue, Modifier.testTag("onboarding-next")) }) {
        FlowTitleBlock(stringResource(R.string.ob_perm_title), stringResource(R.string.ob_perm_lead))
        Column(Modifier.padding(top = MikaSpace.S24), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            PermissionRow(MikaIcons.Bell, R.string.ob_perm_notif, R.string.ob_perm_notif_body, "perm-notifications") {
                if (status.notificationsAllowed) StatePill(stringResource(R.string.ob_perm_allowed), c.ok, c.okSoft, check = true)
                else SmallButton(stringResource(R.string.ob_perm_allow), primary = true) { onFix(Fix.AllowNotifications) }
            }
            PermissionRow(MikaIcons.Layers, R.string.ob_perm_capsule, R.string.ob_perm_capsule_body, "perm-capsule") {
                // Drawn by the accessibility service: there is no separate system permission to ask for.
                if (status.accessibility == AccessibilityState.On) StatePill(stringResource(R.string.ob_perm_capsule_ready), c.ok, c.okSoft, check = true)
                else StatePill(stringResource(R.string.ob_perm_capsule_needs), c.ink2, c.fill)
            }
            PermissionRow(MikaIcons.Alarm, R.string.ob_perm_alarm, R.string.ob_perm_alarm_body, "perm-alarms") {
                if (exact) StatePill(stringResource(R.string.ob_perm_allowed), c.ok, c.okSoft, check = true)
                else SmallButton(stringResource(R.string.ob_perm_allow_now), primary = false) {
                    runCatching { context.startActivity(TriggerRuntime.exactAlarmSettings(context)) }
                }
            }
        }
        Text(stringResource(R.string.ob_perm_note), Modifier.padding(start = MikaSpace.S4, end = MikaSpace.S4, top = MikaSpace.S16), style = MikaType.Caption, color = c.ink3)
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: Int, body: Int, tag: String, trailing: @Composable () -> Unit) {
    val c = MikaTheme.colors
    GlassCard(Modifier.testTag(tag)) {
        Column(Modifier.padding(MikaSpace.S16), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                IconSquare(icon, c.ink, c.fill)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
                    Text(stringResource(title), Modifier.semantics { heading() }, style = MikaType.BodyStrong, color = c.ink)
                    Text(stringResource(body), style = MikaType.Callout, color = c.ink2)
                }
            }
            // On its own line so a long label or a large font never squeezes the explanation.
            Box(Modifier.padding(start = MikaSize.TopBar)) { trailing() }
        }
    }
}

@Composable
internal fun SmallButton(text: String, primary: Boolean, tag: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = MikaTheme.colors
    Box(
        Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill)
            .then(if (primary) Modifier.background(if (enabled) c.ink else c.ink.copy(alpha = 0.12f)) else Modifier.border(1.dp, c.ink.copy(alpha = 0.22f), MikaShapes.Pill))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = MikaSpace.S24, vertical = MikaSpace.S8)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MikaType.BodyStrong,
            color = if (!primary) c.ink else if (enabled) c.bg else c.ink3)
    }
}

// ---------- 7 Optional extras ----------

@Composable
private fun PersonalStep(
    progress: OnboardingProgress, whatsappLinked: Boolean, onLinkWhatsapp: () -> Unit,
    header: @Composable () -> Unit, onContinue: () -> Unit,
) {
    val c = MikaTheme.colors
    var name by rememberSaveable { mutableStateOf(progress.name()) }
    val save = { progress.setName(name); onContinue() }
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_continue), save, Modifier.testTag("onboarding-next")) }) {
        FlowTitleBlock(stringResource(R.string.ob_personal_title), stringResource(R.string.ob_personal_lead))
        GlassCard(Modifier.padding(top = MikaSpace.S24)) {
            Column(Modifier.padding(MikaSpace.S16), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                OrbitTextField(
                    name, { name = it.take(OnboardingFlow.NAME_MAX) }, stringResource(R.string.ob_name_label),
                    Modifier.fillMaxWidth().testTag("onboarding-name"), placeholder = stringResource(R.string.ob_name_placeholder),
                )
                Text(stringResource(R.string.ob_name_hint), style = MikaType.Caption, color = c.ink3)
            }
        }
        GlassCard(Modifier.padding(top = MikaSpace.S12)) {
            Column(Modifier.padding(MikaSpace.S16), verticalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
                    IconSquare(MikaIcons.Message, c.ok, c.okSoft)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
                        Text(stringResource(R.string.ob_wa_title), Modifier.semantics { heading() }, style = MikaType.BodyStrong, color = c.ink)
                        Text(stringResource(R.string.ob_wa_body), style = MikaType.Body, color = c.ink2)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(c.warnSoft, RoundedCornerShape(MikaRadius.Lg)).padding(horizontal = MikaSpace.S12, vertical = MikaSpace.S12),
                    horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
                ) {
                    Icon(MikaIcons.Exclaim, null, Modifier.padding(top = 2.dp).size(MikaSize.SmallIcon), tint = c.warn)
                    Text(stringResource(R.string.ob_wa_risk), Modifier.weight(1f), style = MikaType.Caption, color = c.ink)
                }
                if (whatsappLinked) StatePill(stringResource(R.string.ob_wa_linked), c.ok, c.okSoft, check = true)
                else SmallButton(stringResource(R.string.ob_wa_title), primary = false, tag = "onboarding-whatsapp") { progress.setName(name); onLinkWhatsapp() }
            }
        }
    }
}

// ---------- 8 Done ----------

@Composable
private fun DoneStep(
    store: ProviderStore, name: String, phoneReady: Boolean, whatsappLinked: Boolean, reduceMotion: Boolean,
    header: @Composable () -> Unit, onFinish: () -> Unit,
) {
    val c = MikaTheme.colors
    val roles by store.roles.collectAsState()
    val context = LocalContext.current
    val geminiOn by remember { com.wangzan101.mika.gemini.GeminiEngine.get(context) }.enabled.collectAsState()
    // A provider's main model, or the Gemini plan when that is what runs tasks.
    val model = remember(roles) { store.mainModelName() } ?: stringResource(R.string.ob_model_gemini).takeIf { geminiOn }
    val settle = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val ring = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(reduceMotion) {
        launch { settle.animateTo(1f, MikaMotion.specs(reduceMotion).bouncy()) }
        launch { ring.animateTo(1f, MikaMotion.specs(reduceMotion).timed(MikaMotion.Duration.LONG)) }
    }
    val glow = c.accent
    FlowPage(header, bottom = { FlowButton(stringResource(R.string.ob_done_start), onFinish, Modifier.testTag("onboarding-finish")) }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(MikaSize.IntroHalo).drawBehind {
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.30f), glow.copy(alpha = 0.10f), Color.Transparent)))
                    // One ring goes out and fades: the short celebration. Still, it is not drawn at all.
                    val p = ring.value
                    if (p > 0f && p < 1f) drawCircle(
                        glow.copy(alpha = 0.7f * (1f - p)), radius = size.minDimension / 2 * (0.5f + 0.5f * p),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                MikaMark(Modifier.size(MikaSize.IntroDoneMark).graphicsLayer {
                    val p = settle.value
                    scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p; alpha = p.coerceIn(0f, 1f)
                }.clearAndSetSemantics {})
            }
            Text(
                if (name.isBlank()) stringResource(R.string.ob_done_title) else stringResource(R.string.ob_done_title_named, name),
                Modifier.semantics { heading() }, style = MikaType.LargeTitle,
                color = c.ink, textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.ob_done_lead), Modifier.padding(top = MikaSpace.S12), style = MikaType.Body, color = c.ink2, textAlign = TextAlign.Center)
        }
        GlassCard(Modifier.padding(top = MikaSpace.S24)) {
            SummaryRow(stringResource(R.string.ob_done_model), model ?: stringResource(R.string.ob_phone_later), ok = model != null)
            Box(Modifier.padding(horizontal = MikaSpace.S16).fillMaxWidth().height(0.75.dp).background(c.hair))
            SummaryRow(stringResource(R.string.ob_done_phone), stringResource(if (phoneReady) R.string.phone_connected else R.string.ob_phone_later), ok = phoneReady)
            Box(Modifier.padding(horizontal = MikaSpace.S16).fillMaxWidth().height(0.75.dp).background(c.hair))
            SummaryRow(stringResource(R.string.whatsapp_title), stringResource(if (whatsappLinked) R.string.ob_wa_linked else R.string.ob_done_not_linked), ok = whatsappLinked)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, ok: Boolean) {
    val c = MikaTheme.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = MikaSize.Button).padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S12).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        Text(label, Modifier.weight(1f), style = MikaType.Body, color = c.ink2)
        Text(value, Modifier.weight(1f, fill = false), style = MikaType.BodyStrong,
            color = if (ok) c.ink else c.ink3, textAlign = TextAlign.End)
        if (ok) Icon(MikaIcons.Check, null, Modifier.size(MikaSize.SmallIcon), tint = c.ok)
    }
}

/** The main model's name as the store has it, or null when none is chosen yet. */
internal fun ProviderStore.mainModelName(): String? = roles.value.resolve(ModelRole.Main)?.let { id ->
    configs.value.firstOrNull { it.id == id }?.model?.takeIf { it.isNotBlank() }
}
