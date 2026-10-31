package com.stockfileexplorer.app.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.view.KeyEvent
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.ViewerActivity
import com.stockfileexplorer.app.data.Archives
import com.stockfileexplorer.app.data.FileLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(initialFiles: List<File>, startIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<File>().apply { addAll(initialFiles) } }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (files.size - 1).coerceAtLeast(0))) { files.size }
    var zoomed by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val current = files.getOrNull(pager.currentPage)
    // A file opened from another app is a temporary copy in our cache: viewing and sharing only, so no
    // action here can look like it changes or deletes the original.
    val incomingDir = remember { File(context.cacheDir, "incoming").absolutePath + "/" }
    val viewOnly = current != null && current.absolutePath.startsWith(incomingDir)
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Column {
                    if (files.size > 1) Text(stringResource(R.string.viewer_count, pager.currentPage + 1, files.size), style = MaterialTheme.typography.labelMedium)
                    Text(current?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (viewOnly) Text(stringResource(R.string.incoming_view_only), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onBack) { Text(stringResource(R.string.viewer_back)) } })
    }, bottomBar = {
        if (current != null) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp)) {
            ViewerAction(Icons.Filled.Share, stringResource(R.string.share), Modifier.weight(1f)) { shareFiles(context, listOf(current)) }
            if (!viewOnly) ViewerAction(Icons.Filled.Delete, stringResource(R.string.delete), Modifier.weight(1f)) { confirmDelete = true }
            ViewerAction(Icons.Filled.Info, stringResource(R.string.viewer_info), Modifier.weight(1f)) { showInfo = true }
            Box(Modifier.weight(1f)) {
                ViewerAction(Icons.Filled.MoreVert, stringResource(R.string.viewer_more), Modifier.fillMaxWidth()) { moreMenu = true }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.open_with)) }, onClick = {
                        moreMenu = false
                        openFileWithChooser(context, current, context.getString(R.string.open_with), context.getString(R.string.no_app_to_open))
                    })
                }
            }
        }
    }) { padding ->
        HorizontalPager(pager, Modifier.fillMaxSize().padding(padding), userScrollEnabled = !zoomed, key = { files.getOrNull(it)?.path ?: it }) { index ->
            val file = files.getOrNull(index)
            if (file != null) Column(Modifier.fillMaxSize()) {
                when (ViewerActivity.kind(file)) {
                    "image" -> PictureViewer(file, onZoomed = { if (index == pager.currentPage) zoomed = it })
                    "pdf" -> PdfViewer(file)
                    // Only the settled page owns a player, so swiping never leaves two videos playing.
                    "video" -> if (index == pager.settledPage) VideoViewer(file)
                    "archive" -> ArchiveViewer(file)
                    "text" -> TextViewer(file)
                }
            }
        }
    }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    if (showInfo && current != null) PropertiesDialog(
        name = current.name, path = current.absolutePath, isDirectory = false,
        sizeBytes = current.length(), modified = current.lastModified(), favorite = false,
        onDismiss = { showInfo = false })
    if (confirmDelete && current != null) ConfirmDialog(
        title = stringResource(R.string.delete_confirm_title, 1),
        body = stringResource(R.string.delete_confirm_body),
        confirmLabel = stringResource(R.string.delete),
        onConfirm = {
            confirmDelete = false
            val target = current
            scope.launch {
                val ok = withContext(Dispatchers.IO) { com.stockfileexplorer.app.data.Trash.moveToTrash(target) }
                if (ok) {
                    com.stockfileexplorer.app.data.FileIndex.removed(listOf(target))
                    com.stockfileexplorer.app.data.FileIndex.invalidate()
                    files.remove(target)
                    android.widget.Toast.makeText(context, R.string.deleted_ok, android.widget.Toast.LENGTH_SHORT).show()
                    if (files.isEmpty()) onBack()
                } else android.widget.Toast.makeText(context, R.string.delete_failed, android.widget.Toast.LENGTH_SHORT).show()
            }
        },
        onDismiss = { confirmDelete = false })
}

@Composable
private fun ViewerAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
private fun ViewerError(detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.viewer_cannot_open), style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ZoomImage(bitmap: Bitmap, description: String, rotation: Float = 0f, modifier: Modifier, onZoomed: (Boolean) -> Unit = {}) {
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var x by remember(bitmap) { mutableFloatStateOf(0f) }
    var y by remember(bitmap) { mutableFloatStateOf(0f) }
    Box(modifier.clipToBounds().background(Color(0xFFF3F1EA)), contentAlignment = Alignment.Center) {
        Image(bitmap.asImageBitmap(), description, modifier = Modifier.fillMaxSize()
            .pointerInput(bitmap) {
                // One finger on an unzoomed picture is left alone so the pager can swipe; pinch or a zoomed pan is ours.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || zoom > 1f) {
                            val scale = event.calculateZoom()
                            val pan = event.calculatePan()
                            zoom = (zoom * scale).coerceIn(1f, 6f)
                            x = if (zoom == 1f) 0f else (x + pan.x).coerceIn(-size.width * (zoom - 1), size.width * (zoom - 1))
                            y = if (zoom == 1f) 0f else (y + pan.y).coerceIn(-size.height * (zoom - 1), size.height * (zoom - 1))
                            onZoomed(zoom > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = x; translationY = y; rotationZ = rotation })
    }
}

@Composable
private fun PictureViewer(file: File, onZoomed: (Boolean) -> Unit = {}) {
    var bitmap by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.path) { mutableStateOf<String?>(null) }
    var rotation by remember { mutableFloatStateOf(0f) }
    val decodeError = stringResource(R.string.viewer_image_error)
    LaunchedEffect(file.path) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val scale = min(1.0, 2048.0 / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
            }
        } catch (e: Exception) { error = decodeError }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { rotation = (rotation + 90) % 360 }) { Text(stringResource(R.string.viewer_rotate)) }
        Text(stringResource(R.string.viewer_pinch), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
    }
    if (error != null) ViewerError(error!!) else if (bitmap == null) CircularProgressIndicator(Modifier.padding(24.dp))
    else ZoomImage(bitmap!!, stringResource(R.string.viewer_picture), rotation, Modifier.fillMaxSize(), onZoomed)
}

@Composable
private fun PdfViewer(file: File) {
    var page by remember(file.path) { mutableIntStateOf(0) }
    var pages by remember(file.path) { mutableIntStateOf(0) }
    var bitmap by remember(file.path) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file.path) { mutableStateOf<String?>(null) }
    val pdfError = stringResource(R.string.viewer_pdf_error)
    LaunchedEffect(file.path, page) {
        bitmap = null
        error = null
        try {
            val rendered = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val total = renderer.pageCount
                        require(total > 0)
                        val bmp = renderer.openPage(page.coerceIn(0, total - 1)).use { p ->
                            val factor = min(2.0, 2048.0 / maxOf(p.width, p.height))
                            Bitmap.createBitmap((p.width * factor).toInt().coerceAtLeast(1), (p.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(android.graphics.Color.WHITE)
                                p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        total to bmp
                    }
                }
            }
            pages = rendered.first
            bitmap = rendered.second
        } catch (e: Exception) { error = pdfError }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { page-- }, enabled = page > 0 && bitmap != null) { Text(stringResource(R.string.viewer_previous)) }
        Text(if (pages == 0) "…" else "${page + 1} / $pages", style = MaterialTheme.typography.labelLarge)
        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { page++ }, enabled = page + 1 < pages && bitmap != null) { Text(stringResource(R.string.viewer_next)) }
    }
    if (error != null) ViewerError(error!!) else if (bitmap == null) CircularProgressIndicator(Modifier.padding(24.dp))
    else ZoomImage(bitmap!!, stringResource(R.string.viewer_pdf_page, page + 1), modifier = Modifier.fillMaxSize())
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoViewer(file: File) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val playFocus = remember(file.path) { FocusRequester() }
    val videoFocus = remember(file.path) { FocusRequester() }
    var surfaceFocused by remember(file.path) { mutableStateOf(false) }
    var playFocused by remember(file.path) { mutableStateOf(false) }
    var error by remember(file.path) { mutableStateOf(false) }
    var rendered by remember(file.path) { mutableStateOf(false) }
    var playing by remember(file.path) { mutableStateOf(false) }
    val player = remember(file.path) {
        ExoPlayer.Builder(context).build().apply {
            // Local URI only. There is no INTERNET permission and no streaming UI.
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = true }
            override fun onRenderedFirstFrame() { rendered = true }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player.pause()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    var position by remember(file.path) { mutableFloatStateOf(0f) }
    var duration by remember(file.path) { mutableFloatStateOf(0f) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.toFloat()
            duration = player.duration.coerceAtLeast(0).toFloat()
            delay(250)
        }
    }
    LaunchedEffect(file.path, error) { if (!error) videoFocus.requestFocus() }
    if (error) ViewerError(stringResource(R.string.viewer_video_error))
    else Column(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
        // Only this video subtree owns shortcuts. Header/actions/dialogs keep their own keys.
        val key = event.nativeKeyEvent
        val seek = when (key.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -10_000L
            KeyEvent.KEYCODE_DPAD_RIGHT -> 10_000L
            else -> null
        }
        if (surfaceFocused && key.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) playFocus.requestFocus()
            true
        } else if (playFocused && key.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) videoFocus.requestFocus()
            true
        } else if (seek != null) {
            if (key.action == KeyEvent.ACTION_DOWN && duration > 0) {
                // Seeking exactly to duration can enter ENDED without rendering a new
                // paused frame. Stay within the final decodable interval instead.
                val lastFramePosition = (duration.toLong() - 250L).coerceAtLeast(0L)
                val target = (player.currentPosition + seek).coerceIn(0L, lastFramePosition)
                player.seekTo(target)
            }
            true // consume both halves; hold repeats seek but never changes the pager page
        } else if (surfaceFocused && key.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (key.action == KeyEvent.ACTION_DOWN && key.repeatCount == 0) {
                if (player.playWhenReady) player.pause() else player.play()
            }
            true
        } else false
    }) {
        Text(stringResource(if (!rendered) R.string.viewer_video_loading else if (playing) R.string.viewer_video_playing else R.string.viewer_video_paused),
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (player.playWhenReady) player.pause() else player.play() },
                modifier = Modifier.testTag("video_play_pause").focusRequester(playFocus).focusProperties { down = videoFocus }.onFocusChanged { playFocused = it.isFocused }
                    .border(if (playFocused) 3.dp else 0.dp, if (playFocused) MaterialTheme.colorScheme.onSurface else Color.Transparent, RoundedCornerShape(24.dp))) {
                Text(stringResource(if (playing) R.string.viewer_pause else R.string.viewer_play))
            }
            Spacer(Modifier.width(12.dp))
            Text("${(position / 1000).toInt()} / ${(duration / 1000).toInt()} s", Modifier.testTag("video_position"), style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = position.coerceIn(0f, duration.coerceAtLeast(1f)),
            onValueChange = { player.seekTo(it.toLong()); position = it },
            valueRange = 0f..duration.coerceAtLeast(1f), enabled = duration > 0,
            modifier = Modifier.testTag("video_seek").fillMaxWidth().padding(horizontal = 16.dp))
        val focusLabel = stringResource(R.string.viewer_remote_hint)
        Box(Modifier.fillMaxWidth().weight(1f)
            .testTag("video_remote_surface")
            .focusRequester(videoFocus).focusProperties { up = playFocus }.onFocusChanged { surfaceFocused = it.isFocused }
            .border(if (surfaceFocused) 3.dp else 0.dp, if (surfaceFocused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .semantics { contentDescription = focusLabel }.focusable()) {
        AndroidView(factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                isFocusable = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                useController = false
                controllerAutoShow = true
                controllerShowTimeoutMs = 0
                controllerHideOnTouch = false
                showController()
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
        }, modifier = Modifier.fillMaxSize().padding(3.dp))
        if (surfaceFocused) Text(focusLabel, color = Color.White,
            modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.75f)).padding(8.dp),
            style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ArchiveViewer(file: File) {
    var entries by remember(file.path) { mutableStateOf<List<Archives.Entry>?>(null) }
    var error by remember(file.path) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var extracted by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.viewer_archive_error)
    LaunchedEffect(file.path) {
        try { entries = withContext(Dispatchers.IO) { Archives.list(file) } }
        catch (e: Exception) { error = failed + "\n" + (e.message ?: "") }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(stringResource(R.string.viewer_extract_policy), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        var pickFolder by remember { mutableStateOf(false) }
        fun runExtract(destination: File?) {
            busy = true
            error = null
            scope.launch {
                try {
                    val jobContext = coroutineContext
                    extracted = withContext(Dispatchers.IO) {
                        Archives.extract(file, { jobContext.ensureActive() }, { name -> current = name }, destination)
                    }
                    com.stockfileexplorer.app.data.ScanStore.invalidate()
                } catch (e: Exception) { error = failed + "\n" + (e.message ?: "") }
                finally { busy = false }
            }
        }
        val canExtract = entries != null && !busy && extracted == null
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = canExtract, onClick = { runExtract(null) }) { Text(stringResource(if (busy) R.string.viewer_extracting else R.string.viewer_extract_here)) }
            OutlinedButton(enabled = canExtract, onClick = { pickFolder = true }) { Text(stringResource(R.string.viewer_extract_choose)) }
        }
        if (pickFolder) FolderPickerDialog(
            title = stringResource(R.string.viewer_extract_choose),
            confirmLabel = stringResource(R.string.viewer_extract_here_short),
            rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath,
            blockedPaths = emptySet(),
            onConfirm = { pickFolder = false; runExtract(it) },
            onDismiss = { pickFolder = false })
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(current, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        extracted?.let { folder ->
            Text(stringResource(R.string.viewer_extracted, folder.absolutePath), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            val context = LocalContext.current
            TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = {
                context.startActivity(android.content.Intent(context, com.stockfileexplorer.app.MainActivity::class.java)
                    .putExtra("browse_path", folder.absolutePath)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            }) { Text(stringResource(R.string.viewer_open_folder)) }
        }
        error?.let { ViewerError(it) }
        if (entries == null && error == null) CircularProgressIndicator(Modifier.padding(24.dp))
        entries?.let { list ->
            Text(stringResource(R.string.viewer_entries, list.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(list) { e ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(e.name, style = MaterialTheme.typography.bodyMedium)
                        if (!e.directory) Text(FileLogic.formatSize(e.size.coerceAtLeast(0)), style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}


/** Plain-text viewer. Reads at most [TEXT_VIEW_LIMIT_BYTES] and says so when the file is longer. */
private const val TEXT_VIEW_LIMIT_BYTES = 2L * 1024 * 1024

@Composable
private fun TextViewer(file: File) {
    var text by remember(file.path) { mutableStateOf<String?>(null) }
    var truncated by remember(file.path) { mutableStateOf(false) }
    var failed by remember(file.path) { mutableStateOf(false) }
    LaunchedEffect(file.path) {
        withContext(Dispatchers.IO) {
            try {
                val total = file.length()
                val bytes = file.inputStream().use { input ->
                    val buffer = ByteArray(minOf(total, TEXT_VIEW_LIMIT_BYTES).toInt())
                    var read = 0
                    while (read < buffer.size) {
                        val n = input.read(buffer, read, buffer.size - read)
                        if (n < 0) break
                        read += n
                    }
                    buffer.copyOf(read)
                }
                truncated = total > TEXT_VIEW_LIMIT_BYTES
                text = String(bytes, Charsets.UTF_8)
            } catch (e: Exception) {
                failed = true
            }
        }
    }
    when {
        failed -> ViewerError(stringResource(R.string.viewer_text_error))
        text == null -> Text(stringResource(R.string.viewer_video_loading), Modifier.padding(20.dp))
        else -> androidx.compose.foundation.text.selection.SelectionContainer {
            Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(12.dp)) {
                if (truncated) Text(stringResource(R.string.viewer_text_truncated), style = MaterialTheme.typography.labelMedium)
                Text(text.orEmpty(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        }
    }
}
