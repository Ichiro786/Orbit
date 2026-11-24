package com.wangzan101.mika.ui.onboarding

import com.wangzan101.mika.ui.theme.MikaSpace
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wangzan101.mika.R
import com.wangzan101.mika.gemini.GeminiEngine
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.settings.ProviderPresets
import com.wangzan101.mika.settings.ProviderStore
import com.wangzan101.mika.ui.GeminiPlanSection
import com.wangzan101.mika.ui.ListState
import com.wangzan101.mika.ui.ModelSetupCopy
import com.wangzan101.mika.ui.ModelSetupScreen
import com.wangzan101.mika.ui.ModelSetupUi
import com.wangzan101.mika.ui.components.BarIconButton
import com.wangzan101.mika.ui.components.OrbitTextField
import com.wangzan101.mika.ui.rememberModelSetupViewModel
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaShapes
import com.wangzan101.mika.ui.theme.MikaSize
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType

/** The providers offered as chips, in this order, when the app knows them; everything else is under "More and custom". */
internal val KEY_PROVIDERS = listOf("Anthropic", "OpenAI", "Gemini", "OpenRouter", "DeepSeek", "xAI", "Mistral", "Groq")

/** [KEY_PROVIDERS] narrowed to presets that exist and take a key (pure, so it can be tested). */
internal fun keyProviders(known: List<String>): List<String> = KEY_PROVIDERS.filter { it in known }

/**
 * Pick a model: a plan sign-in (Gemini plan, ChatGPT plan) or an API key provider. The key is tested in place
 * with a clear result. Everything runs through the existing model setup view model; "More and custom" and
 * "Change" open the existing models page. This is the one step without Skip.
 */
@Composable
internal fun ModelStep(
    store: ProviderStore,
    header: @Composable (onBack: () -> Unit) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val vm = rememberModelSetupViewModel(store)
    val ui by vm.state.collectAsState()
    val gemini = remember { GeminiEngine.get(context) }
    val geminiOn by gemini.enabled.collectAsState()
    val geminiSignedIn by gemini.signedIn.collectAsState()
    var provider by rememberSaveable { mutableStateOf<String?>(null) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val connected = ui.canNext || (geminiOn && geminiSignedIn)
    val mainModel = ui.configFor(ModelRole.Main)?.model?.takeIf { it.isNotBlank() }
    val chips = remember { keyProviders(ProviderPresets.all.map { it.label }) }

    when {
        advanced -> ModelSetupScreen(store, onboarding = false, onBack = { advanced = false }, onDone = { advanced = false })
        provider != null -> {
            val leave = { vm.closeForm(); provider = null }
            BackHandler(onBack = leave)
            KeyPage(
                provider!!, ui, connected, mainModel, header = { header(leave) },
                onKey = vm::setApiKey, onTest = vm::testAndSave, onSaveUntested = vm::saveWithoutTest,
                onChangeModel = { advanced = true }, onContinue = onContinue,
            )
        }
        else -> {
            BackHandler(onBack = onBack)
            val c = MikaTheme.colors
            FlowPage(
                header = { header(onBack) },
                bottom = { FlowButton(stringResource(R.string.ob_continue), onContinue, Modifier.testTag("setup-next"), enabled = connected) },
            ) {
                FlowTitleBlock(stringResource(R.string.ob_model_title), stringResource(R.string.ob_model_lead))
                if (connected) {
                    ResultPanel(ok = true, stringResource(R.string.ob_model_ok), stringResource(R.string.ob_model_ready_body), Modifier.padding(top = MikaSpace.S24))
                    if (mainModel != null) MainModelRow(mainModel) { advanced = true }
                }
                SectionLabel(stringResource(R.string.ob_model_plans))
                // The Gemini plan's own section: setup, sign-in and its states are exactly as on the models page.
                GeminiPlanSection()
                GlassCard(Modifier.padding(top = MikaSpace.S12)) {
                    val signIn = ui.chatGptSignIn
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = signIn != ListState.Running, role = Role.Button, onClick = vm::signInWithChatGpt)
                            .heightIn(min = (MikaSpace.S32 * 2)).padding(start = MikaSpace.S16, end = MikaSpace.S16, top = MikaSpace.S12, bottom = MikaSpace.S12).testTag("onboarding-chatgpt"),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(R.string.ob_model_chatgpt), style = MikaType.BodyStrong, color = c.ink)
                            val line = when {
                                ui.chatGptAccount?.signedIn == true -> stringResource(R.string.ob_model_ok)
                                signIn == ListState.Running -> stringResource(R.string.chatgpt_waiting)
                                signIn is ListState.Failed -> signIn.message
                                else -> stringResource(R.string.ob_model_chatgpt_body)
                            }
                            Text(line, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MikaType.Callout,
                                color = if (signIn is ListState.Failed) c.bad else c.ink2)
                        }
                        Icon(MikaIcons.Chevron, null, Modifier.size(MikaSize.TileIcon), tint = c.ink3)
                    }
                }
                SectionLabel(stringResource(R.string.ob_model_keys))
                ProviderChips(chips, onPick = { label -> vm.openForm(); vm.selectPreset(label); provider = label }, onMore = { advanced = true })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = MikaSpace.S4, top = MikaSpace.S24, bottom = MikaSpace.S12).semantics { heading() },
        style = MikaType.Overline, color = MikaTheme.colors.ink3,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderChips(labels: List<String>, onPick: (String) -> Unit, onMore: () -> Unit) {
    val c = MikaTheme.colors
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8), verticalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
        labels.forEach { label ->
            Box(
                Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill).background(c.fill).border(0.75.dp, c.glassEdge, MikaShapes.Pill)
                    .clickable(role = Role.Button) { onPick(label) }.padding(horizontal = MikaSpace.S16, vertical = MikaSpace.S8).testTag("preset-$label"),
                contentAlignment = Alignment.Center,
            ) { Text(label, style = MikaType.BodyStrong, color = c.ink) }
        }
        Row(
            Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill).border(1.dp, c.ink.copy(alpha = 0.18f), MikaShapes.Pill)
                .clickable(role = Role.Button, onClick = onMore).padding(start = MikaSpace.S16, end = MikaSpace.S12, top = MikaSpace.S8, bottom = MikaSpace.S8).testTag("onboarding-more-models"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S4),
        ) {
            Text(stringResource(R.string.ob_model_more), style = MikaType.BodyStrong, color = c.ink2)
            Icon(MikaIcons.Chevron, null, Modifier.size(MikaSize.SmallIcon), tint = c.ink2)
        }
    }
}

/** One provider's key: paste, test in place, and see either "Connected" with the main model or what went wrong. */
@Composable
private fun KeyPage(
    provider: String, ui: ModelSetupUi, connected: Boolean, mainModel: String?,
    header: @Composable () -> Unit,
    onKey: (String) -> Unit, onTest: () -> Unit, onSaveUntested: () -> Unit, onChangeModel: () -> Unit, onContinue: () -> Unit,
) {
    val c = MikaTheme.colors
    val form = ui.form
    val testing = form.test == ListState.Running
    val failed = form.test as? ListState.Failed
    // Saved and a main model chosen: the form has done its job.
    val saved = connected && !testing && failed == null && !ui.formOpen
    var showKey by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var confirmUntested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(failed) { if (failed == null) showDetails = false }
    FlowPage(
        header,
        bottom = { FlowButton(stringResource(R.string.ob_continue), onContinue, Modifier.testTag("setup-next"), enabled = saved) },
    ) {
        FlowTitleBlock(stringResource(R.string.ob_model_connect, provider), stringResource(R.string.ob_model_key_lead))
        OrbitTextField(
            form.apiKey, onKey, stringResource(R.string.ob_model_key_label),
            Modifier.fillMaxWidth().padding(top = MikaSpace.S24).testTag("setup-api-key"),
            placeholder = ModelSetupCopy.KEY_HINT, keyboardType = KeyboardType.Password, secret = !showKey,
            trailing = {
                BarIconButton(
                    if (showKey) MikaIcons.EyeOff else MikaIcons.Eye,
                    stringResource(if (showKey) R.string.ob_model_hide_key else R.string.ob_model_show_key), { showKey = !showKey },
                )
            },
        )
        when {
            saved -> {
                ResultPanel(ok = true, stringResource(R.string.ob_model_ok), ui.probeNote ?: stringResource(R.string.ob_model_ok_body), Modifier.padding(top = MikaSpace.S16))
                if (mainModel != null) MainModelRow(mainModel, onChangeModel)
            }
            failed != null -> {
                ResultPanel(ok = false, stringResource(R.string.ob_model_fail), failed.message, Modifier.padding(top = MikaSpace.S16)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8), verticalAlignment = Alignment.CenterVertically) {
                        SmallButton(stringResource(R.string.ob_model_retry), primary = true, tag = "setup-test", enabled = form.canTest, onClick = onTest)
                        if (failed.details != null) Box(
                            Modifier.heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill).clickable(role = Role.Button) { showDetails = !showDetails }
                                .padding(horizontal = MikaSpace.S12).testTag("setup-test-details-toggle"),
                            contentAlignment = Alignment.Center,
                        ) { Text(stringResource(if (showDetails) R.string.model_details_hide else R.string.model_details_show), style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.ink) }
                    }
                    if (showDetails) failed.details?.let {
                        Text(it, Modifier.testTag("setup-test-details"), style = MikaType.Hint, fontFamily = FontFamily.Monospace, color = c.ink2)
                    }
                }
                Box(
                    Modifier.padding(top = MikaSpace.S8).heightIn(min = MikaSize.TouchTarget).clip(MikaShapes.Pill).clickable(role = Role.Button) { confirmUntested = true }
                        .padding(horizontal = MikaSpace.S4).testTag("setup-save-untested"),
                    contentAlignment = Alignment.CenterStart,
                ) { Text(ModelSetupCopy.SAVE_UNTESTED, style = MikaType.Body, fontWeight = FontWeight.Medium, color = c.ink2) }
            }
            else -> Box(Modifier.padding(top = MikaSpace.S16)) {
                FlowButton(
                    if (testing) ModelSetupCopy.TESTING else stringResource(R.string.ob_model_test), onTest,
                    Modifier.testTag("setup-test"), enabled = form.canTest,
                )
            }
        }
    }
    if (confirmUntested) AlertDialog(
        onDismissRequest = { confirmUntested = false },
        title = { Text(ModelSetupCopy.SAVE_UNTESTED_TITLE) },
        text = { Text(ModelSetupCopy.SAVE_UNTESTED_BODY) },
        confirmButton = { TextButton({ confirmUntested = false; onSaveUntested() }) { Text(ModelSetupCopy.SAVE_UNTESTED_CONFIRM) } },
        dismissButton = { TextButton({ confirmUntested = false }) { Text(ModelSetupCopy.CANCEL) } },
    )
}

/** The inline result: green with a check, or red with an exclamation mark; always a word, never colour alone. */
@Composable
internal fun ResultPanel(ok: Boolean, title: String, body: String, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    val c = MikaTheme.colors
    val tone = if (ok) c.ok else c.bad
    Column(
        modifier.fillMaxWidth().clip(FlowCard).background(if (ok) c.okSoft else c.badSoft).border(0.75.dp, tone.copy(alpha = 0.3f), FlowCard)
            .padding(MikaSpace.S16).semantics { liveRegion = LiveRegionMode.Polite }.testTag(if (ok) "setup-test-ok" else "setup-test-failed"),
        verticalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12)) {
            Box(Modifier.size(MikaSpace.S32).background(tone, CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (ok) MikaIcons.Check else MikaIcons.Exclaim, null, Modifier.size(MikaSize.TileIcon), tint = c.bg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MikaSpace.S4)) {
                Text(title, style = MikaType.BodyStrong, color = c.ink)
                Text(body, style = MikaType.Body, color = c.ink)
            }
        }
        actions?.invoke()
    }
}

@Composable
private fun MainModelRow(model: String, onChange: () -> Unit) {
    val c = MikaTheme.colors
    GlassCard(Modifier.padding(top = MikaSpace.S12)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = stringResource(R.string.ob_model_change), role = Role.Button, onClick = onChange)
                .heightIn(min = (MikaSpace.S32 * 2)).padding(start = MikaSpace.S16, end = MikaSpace.S12, top = MikaSpace.S12, bottom = MikaSpace.S12).testTag("setup-pick-main"),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.ob_model_main), style = MikaType.Caption, color = c.ink2)
                Text(model, style = MikaType.BodyStrong, color = c.ink)
            }
            Text(stringResource(R.string.ob_model_change), style = MikaType.Callout, color = c.ink3)
            Icon(MikaIcons.Chevron, null, Modifier.size(MikaSize.TileIcon), tint = c.ink3)
        }
    }
}
