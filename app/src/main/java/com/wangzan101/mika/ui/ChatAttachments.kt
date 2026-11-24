package com.wangzan101.mika.ui

import com.wangzan101.mika.R
import com.wangzan101.mika.i18n.tr
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import com.wangzan101.mika.ui.components.SmokedGlassPanel
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.core.chat.GeneratedPhoto
import com.wangzan101.mika.ui.components.cardSurface
import com.wangzan101.mika.ui.theme.MikaColors
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import androidx.compose.ui.graphics.vector.ImageVector
import com.wangzan101.mika.ui.theme.MikaRadius
import com.wangzan101.mika.ui.theme.MikaShapes
import kotlinx.coroutines.launch
import com.wangzan101.mika.ui.theme.MikaMotion
import com.wangzan101.mika.ui.theme.MikaSpace
import com.wangzan101.mika.ui.theme.MikaSize

/** 对话页附件的说法。 */
object AttachCopy {
    val ADD: String get() = tr(R.string.attach_add)
    val CLOSE: String get() = tr(R.string.attach_close)
    val CAMERA: String get() = tr(R.string.attach_camera)
    val ALBUM: String get() = tr(R.string.orbit_attach_media)
    val FILES: String get() = tr(R.string.attach_files)
    val NO_CAMERA: String get() = tr(R.string.attach_no_camera)
    val GONE: String get() = tr(R.string.attach_gone)
    val IMPORTING: String get() = tr(R.string.attach_importing)

    fun remove(name: String) = tr(R.string.attach_remove, name)
    fun overLimit(n: Int) = tr(R.string.attach_over_limit, AttachTray.LIMIT, n)
}

// ---------- 菜单 ----------

/** The three system pickers behind the composer's plus menu. */
@androidx.compose.runtime.Stable
internal class AttachActions(val camera: () -> Unit, val media: () -> Unit, val files: () -> Unit)

/** One more row under the pickers in the plus menu (device folders, saved tasks, checklists, workflows). */
@androidx.compose.runtime.Immutable
internal data class AttachExtra(val icon: ImageVector, val label: String, val tag: String, val onClick: () -> Unit)

/**
 * Registers the system pickers without requesting broad media-library access.
 * Keep this composed while a picker is open (the menu itself closes on tap), or the result is lost.
 */
@Composable
internal fun rememberAttachActions(onPicked: (List<String>) -> Unit): AttachActions {
    val context = LocalContext.current
    val picked by androidx.compose.runtime.rememberUpdatedState(onPicked)
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri ?: return@rememberLauncherForActivityResult
        cameraUri = null
        if (ok) picked(listOf(uri)) else AttachFiles.discardCamera(context, Uri.parse(uri))
    }
    fun startCamera() {
        val uri = runCatching { AttachFiles.newCameraUri(context) }.getOrElse {
            Toast.makeText(context, AttachCopy.NO_CAMERA, Toast.LENGTH_SHORT).show()
            return
        }
        cameraUri = uri.toString()
        try {
            camera.launch(uri)
        } catch (e: ActivityNotFoundException) {
            cameraUri = null
            AttachFiles.discardCamera(context, uri)
            Toast.makeText(context, AttachCopy.NO_CAMERA, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            cameraUri = null
            AttachFiles.discardCamera(context, uri)
            Toast.makeText(context, AttachCopy.NO_CAMERA, Toast.LENGTH_SHORT).show()
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera()
    }
    val album = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(AttachTray.LIMIT)) { uris ->
        if (uris.isNotEmpty()) picked(uris.map { it.toString() })
    }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) picked(uris.map { it.toString() })
    }
    fun openPicker(action: () -> Unit) {
        try { action() } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.orbit_picker_unavailable, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.orbit_picker_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    return remember(camera, album, files, cameraPermission) {
        AttachActions(
            camera = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            media = { openPicker { album.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) } },
            files = { openPicker { files.launch(arrayOf("*/*")) } },
        )
    }
}

/**
 * The plus button's menu: a small smoked-glass column that floats just above the composer,
 * anchored under the plus (like X's attach menu). Tapping outside, Back, or any choice closes it.
 * [bottom] is the distance from the bottom of this overlay to the top of the composer.
 */
@Composable
internal fun AttachMenuOverlay(
    open: Boolean,
    onDismiss: () -> Unit,
    actions: AttachActions,
    bottom: Dp,
    modifier: Modifier = Modifier,
    onTeach: (() -> Unit)? = null,
    backdrop: com.wangzan101.mika.ui.components.GlassBackdrop? = null,
    reduceMotion: Boolean = false,
    extras: List<AttachExtra> = emptyList(),
) {
    androidx.activity.compose.BackHandler(enabled = open, onBack = onDismiss)
    Box(modifier.fillMaxSize()) {
        if (open) {
            Box(
                Modifier.fillMaxSize().testTag("attach-scrim")
                    // Tap anywhere else to close; Back and the plus (now ×) close it for TalkBack users.
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = MikaSpace.S12, bottom = bottom),
            enter = MikaMotion.specs(reduceMotion).enter(MenuOrigin),
            exit = MikaMotion.specs(reduceMotion).exit(MenuOrigin),
        ) {
            AttachPanel(
                onCamera = { onDismiss(); actions.camera() },
                onAlbum = { onDismiss(); actions.media() },
                onFiles = { onDismiss(); actions.files() },
                onTeach = onTeach?.let { teach -> { onDismiss(); teach() } },
                backdrop = backdrop,
                extras = extras.map { it.copy(onClick = { onDismiss(); it.onClick() }) },
            )
        }
    }
}

/** Compact vertical list of attach choices on smoked glass; picked files land in the composer tray. */
@Composable
internal fun AttachPanel(
    onCamera: () -> Unit,
    onAlbum: () -> Unit,
    onFiles: () -> Unit,
    modifier: Modifier = Modifier,
    onTeach: (() -> Unit)? = null,
    backdrop: com.wangzan101.mika.ui.components.GlassBackdrop? = null,
    extras: List<AttachExtra> = emptyList(),
) {
    SmokedGlassPanel(modifier.widthIn(min = 196.dp, max = 260.dp).width(IntrinsicSize.Max).testTag("attach-panel"), backdrop, PanelShape) {
        MenuRow(MikaIcons.Photo, AttachCopy.ALBUM, onAlbum)
        MenuRow(MikaIcons.PhotoCamera, AttachCopy.CAMERA, onCamera)
        MenuRow(MikaIcons.Paperclip, AttachCopy.FILES, onFiles)
        if (onTeach != null) MenuRow(MikaIcons.Spark, stringResource(R.string.teach_menu), onTeach, Modifier.testTag("teach-mika"))
        extras.forEach { MenuRow(it.icon, it.label, it.onClick, Modifier.testTag(it.tag)) }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = MikaTheme.colors
    Row(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick)
            .padding(start = MikaSpace.S12, end = MikaSpace.S24, top = MikaSpace.S4, bottom = MikaSpace.S4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S16),
    ) {
        Box(Modifier.size(MikaSize.IconButton).background(c.ink.copy(alpha = if (c.isDark) 0.09f else 0.06f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = c.ink)
        }
        Text(label, style = MenuText, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val MenuOrigin = TransformOrigin(0.08f, 1f)

// ---------- 待发 ----------

/** 输入框上方一排待发的附件:照片是方块缩略图,文件是小卡片(类型、名字、大小),点一下预览或打开,右上角 × 去掉。 */
@Composable
internal fun PendingTray(
    items: List<PendingAttachment>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: ((PendingAttachment) -> Unit)? = null,
) {
    val c = MikaTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(
        modifier.fillMaxWidth().testTag("attach-tray").horizontalScroll(rememberScrollState()).padding(start = MikaSpace.S4, end = MikaSpace.S12, top = MikaSpace.S8, bottom = MikaSpace.S8),
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        for (a in items) {
            val conversationId = a.workspaceConversationId
            val path = a.workspacePath
            val workspaceImage = if (conversationId != null && path != null) AttachImage.Sent(conversationId, path) else null
            val openModifier = Modifier.clickable(
                role = if (a.photo) Role.Image else Role.Button,
                onClickLabel = stringResource(if (a.photo) R.string.attach_view_large else R.string.chat_open),
            ) {
                if (onOpen != null) {
                    onOpen(a)
                } else {
                    scope.launch {
                        val opened = if (workspaceImage != null) {
                            AttachFiles.openSent(context, workspaceImage.conversationId, workspaceImage.path)
                        } else {
                            AttachFiles.openPending(context, a.uri, a.name)
                        }
                        if (!opened) Toast.makeText(context, AttachCopy.GONE, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            Box {
                if (a.photo) {
                    val thumb = rememberAttachImage(workspaceImage ?: AttachImage.Picked(a.uri), TRAY_PX)
                    Box(Modifier.testTag("tray-photo").size(58.dp).clip(ChipShape).background(c.fill).then(openModifier)) {
                        thumb?.let { Image(it, a.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    }
                } else {
                    Row(
                        Modifier.testTag("tray-file").height(58.dp).widthIn(max = 200.dp).cardSurface(c, ChipShape)
                            .clip(ChipShape).then(openModifier).padding(horizontal = MikaSpace.S12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S8),
                    ) {
                        TypeBadge(a.name, Modifier.size(28.dp, 34.dp), small = true)
                        Column(Modifier.widthIn(max = 130.dp)) {
                            Text(a.name, style = ChipName, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(ChatViewModel.sizeLabel(a.size), style = ChipMeta, color = c.ink3, maxLines = 1)
                        }
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(20.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(role = Role.Button, onClickLabel = AttachCopy.remove(a.name)) { onRemove(a.uri) }
                        .semantics { contentDescription = AttachCopy.remove(a.name) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(MikaIcons.Close, null, Modifier.size(12.dp), tint = Color.White)
                }
            }
        }
    }
}

// ---------- 发出去的 ----------

/**
 * 用户消息里带的附件:照片排成格子(见 [PhotoGrid];1 张保持比例,2 张并排,3 张起三列),文件是卡片(类型、名字、大小);
 * 点照片或卡片交给 [onOpen](在 App 里的查看页打开)。最宽 [maxWidth]。
 */
@Composable
internal fun SentAttachments(parsed: AttachmentMessage.Parsed, conversationId: String?, maxWidth: Dp, onOpen: (AttachmentMessage.Item) -> Unit) {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(MikaSpace.S8)) {
        if (parsed.photos.isNotEmpty()) SentPhotos(parsed.photos, conversationId, maxWidth, onOpen)
        for (f in parsed.files) SentFileCard(f, maxWidth) { onOpen(f) }
    }
}

/** 回答里模型产出的 `/workspace/...` 引用:照片排格子、文件画卡片,和回答一起左对齐。 */
@Composable
internal fun OutputRefs(answer: String, conversationId: String?, onOpen: (AttachmentMessage.Item) -> Unit) {
    val parsed = remember(answer) { outputRefsParsed(answer) }
    if (!parsed.hasAttachments) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (parsed.photos.isNotEmpty()) SentPhotos(parsed.photos, conversationId, OutputRefMaxWidth, onOpen)
        for (f in parsed.files) SentFileCard(f, OutputRefMaxWidth) { onOpen(f) }
    }
}

private val OutputRefMaxWidth = 320.dp

@Composable
private fun SentPhotos(photos: List<AttachmentMessage.Item>, conversationId: String?, maxWidth: Dp, onOpen: (AttachmentMessage.Item) -> Unit) {
    val c = MikaTheme.colors
    val cols = PhotoGrid.columns(photos.size)
    if (cols == 1) {
        val p = photos.single()
        val thumb = conversationId?.let { rememberAttachImage(AttachImage.Sent(it, p.path), SINGLE_PX) }
        val ratio = thumb?.let { (it.width.toFloat() / it.height.coerceAtLeast(1)).coerceIn(0.56f, 1.8f) } ?: 0.75f
        Box(
            Modifier.testTag("sent-photo").width(minOf(SingleWidth, maxWidth)).aspectRatio(ratio).clip(GridPhotoShape).background(c.fill)
                .clickable(role = Role.Image, onClickLabel = stringResource(R.string.attach_view_large)) { onOpen(p) },
        ) {
            thumb?.let { Image(it, p.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
        }
        return
    }
    val gap = 4.dp
    val cell = minOf(if (cols == 2) 118.dp else 96.dp, (maxWidth - gap * (cols - 1)) / cols)
    val px = with(LocalDensity.current) { cell.roundToPx() }.coerceAtLeast(64)
    Column(Modifier.clip(GridPhotoShape), verticalArrangement = Arrangement.spacedBy(gap)) {
        photos.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { p ->
                    val thumb = conversationId?.let { rememberAttachImage(AttachImage.Sent(it, p.path), px) }
                    Box(
                        Modifier.testTag("sent-photo").size(cell).background(c.fill)
                            .clickable(role = Role.Image, onClickLabel = stringResource(R.string.attach_view_large)) { onOpen(p) },
                    ) {
                        thumb?.let { Image(it, p.name, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
    }
}

/**
 * Mika 生成的照片:和用户附件一样的格子(1 张一大张,2 张并排,3 张起三列),点开在 App 的查看页看。
 * 路径来自工具结果,不解析回答文字;conversationId 为 null(旧记录没有对话)时不解码。
 */
@Composable
internal fun GeneratedPhotos(photos: List<GeneratedPhoto>, conversationId: String?, maxWidth: Dp) {
    if (conversationId == null || photos.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MikaTheme.colors
    val cols = PhotoGrid.columns(photos.size)
    val gap = 4.dp
    val cell = minOf(if (cols == 1) SingleWidth else if (cols == 2) 118.dp else 96.dp, maxWidth)
    val px = with(LocalDensity.current) { cell.roundToPx() }.coerceAtLeast(64)
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
        photos.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { p ->
                    val thumb = rememberAttachImage(AttachImage.Sent(conversationId, p.path), px)
                    Box(
                        Modifier.testTag("generated-photo").size(cell).clip(GridPhotoShape).background(c.fill)
                            .clickable(role = Role.Image, onClickLabel = stringResource(R.string.attach_view_large)) {
                                scope.launch {
                                    if (!AttachFiles.openSent(context, conversationId, p.path)) {
                                        Toast.makeText(context, AttachCopy.GONE, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                    ) {
                        thumb?.let { Image(it, p.path.substringAfterLast('/'), Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SentFileCard(f: AttachmentMessage.Item, maxWidth: Dp, onClick: () -> Unit) {    val c = MikaTheme.colors
    Row(
        Modifier.testTag("sent-file").widthIn(min = minOf(220.dp, maxWidth), max = maxWidth).cardSurface(c, FileCardShape).clip(FileCardShape)
            .clickable(onClickLabel = stringResource(R.string.chat_open), role = Role.Button, onClick = onClick)
            .padding(horizontal = MikaSpace.S12, vertical = MikaSpace.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MikaSpace.S12),
    ) {
        TypeBadge(f.name, Modifier.size(38.dp, 44.dp), small = false)
        Column(Modifier.weight(1f, fill = false)) {
            Text(f.name, style = FileCardName, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(f.size, style = ChipMeta, color = c.ink3, maxLines = 1)
        }
    }
}

/** 文件类型角标:按扩展名配色,上面写扩展名。 */
@Composable
private fun TypeBadge(name: String, modifier: Modifier, small: Boolean) {
    val shape = RoundedCornerShape(if (small) MikaRadius.Xs else MikaRadius.Xs)
    Box(modifier.background(badgeColor(FileBadge.kind(name), MikaTheme.colors), shape), contentAlignment = Alignment.Center) {
        Text(FileBadge.text(name), style = if (small) BadgeSmall else BadgeLarge, color = Color.White, maxLines = 1)
    }
}

private fun badgeColor(kind: FileBadge.Kind, c: MikaColors): Color = when (kind) {
    FileBadge.Kind.Pdf -> c.bad
    FileBadge.Kind.Sheet -> c.ok
    FileBadge.Kind.Doc -> c.accent
    FileBadge.Kind.Slides -> c.warnSolid
    FileBadge.Kind.Archive, FileBadge.Kind.Other -> NeutralBadge
}

/** 缩略图解到这么大(像素):面板格子、待发的小方块、单张大图。 */
private const val TRAY_PX = 180
private const val SINGLE_PX = 640

private val SingleWidth = 200.dp
private val NeutralBadge = Color(0xFF8E8E93)
private val PanelShape = RoundedCornerShape(MikaRadius.Xxl)
private val ChipShape = RoundedCornerShape(MikaRadius.Md)
private val GridPhotoShape = RoundedCornerShape(MikaRadius.Lg)
private val FileCardShape = RoundedCornerShape(MikaRadius.Lg)
private val MenuText = MikaType.Button
private val BadgeSmall = MikaType.Micro
private val BadgeLarge = MikaType.Micro
private val ChipName = MikaType.HintStrong
private val ChipMeta = MikaType.Micro.copy(fontWeight = FontWeight.Normal)
private val FileCardName = MikaType.Callout.copy(fontWeight = FontWeight.Medium)
