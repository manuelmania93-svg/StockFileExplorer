package com.stockfileexplorer.app.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stockfileexplorer.app.FileActionCallbacks
import com.stockfileexplorer.app.FileActionMenu
import com.stockfileexplorer.app.FileActionPreferences
import com.stockfileexplorer.app.FileOperations
import com.stockfileexplorer.app.MenuCustomizationDialog
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileIndex
import com.stockfileexplorer.app.data.FileItem
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.FileRepository
import com.stockfileexplorer.app.data.ScanStore
import com.stockfileexplorer.app.data.FileCategory
import com.stockfileexplorer.app.data.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val thumbnailCache = android.util.LruCache<String, ImageBitmap>(400)
// Few decode threads so scrolling a long list never starves the UI or the index scan.
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private val thumbnailDispatcher = Dispatchers.IO.limitedParallelism(3)

private fun loadVideoThumbnail(file: File, cacheKey: String): ImageBitmap? {
    thumbnailCache.get(cacheKey)?.let { return it }
    return try {
        val bmp = android.media.ThumbnailUtils.createVideoThumbnail(file, android.util.Size(160, 160), null)
        val result = bmp.asImageBitmap()
        thumbnailCache.put(cacheKey, result)
        result
    } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }
}

private fun loadThumbnail(file: File, reqSize: Int, cacheKey: String): ImageBitmap? {
    thumbnailCache.get(cacheKey)?.let { return it }
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= reqSize && opts.outHeight / (sample * 2) >= reqSize) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null
        val result = bmp.asImageBitmap()
        thumbnailCache.put(cacheKey, result)
        result
    } catch (e: Exception) {
        null
    }
}

private fun mimeTypeFor(file: File): String {
    val ext = MimeTypeMap.getFileExtensionFromUrl(file.name)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
    return mime ?: "*/*"
}

private fun uriFor(context: android.content.Context, file: File) =
    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

fun openFile(context: android.content.Context, file: File, noAppMessage: String, siblings: List<File> = emptyList()) {
    if (com.stockfileexplorer.app.ViewerActivity.open(context, file, siblings)) return
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
    }
}

fun openFileWithChooser(context: android.content.Context, file: File, chooserTitle: String, noAppMessage: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), mimeTypeFor(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, noAppMessage, Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(context: android.content.Context, files: List<File>) {
    try {
        val uris = ArrayList(files.map { uriFor(context, it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeFor(files.first())
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, null))
    } catch (e: Exception) {
        // no share target available
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    startPath: String,
    onNavigateUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootPath = remember { Environment.getExternalStorageDirectory().absolutePath }

    var currentPath by remember { mutableStateOf(startPath) }
    var rawItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val sortContext = androidx.compose.ui.platform.LocalContext.current
    var sortMode by remember { mutableStateOf(SortMode.entries.firstOrNull { it.name == com.stockfileexplorer.app.FileActionPreferences.browserSort(sortContext) } ?: SortMode.NAME) }
    var showHidden by remember { mutableStateOf(false) }
    var gridView by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var selection by remember { mutableStateOf(setOf<String>()) }
    // Floating Rename/Share quick actions card (replaces the bar's old "More" menu):
    // opened by long-press, Select-all or the file menu's Select; leaves with the selection.
    var quickActionsOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<FileItem?>(null) }
    var folderStats by remember { mutableStateOf<Map<String, FileIndex.DirectoryStats>>(emptyMap()) }
    val indexStatus by FileIndex.state.collectAsState()
    var propertiesTarget by remember { mutableStateOf<FileItem?>(null) }
    var transferSources by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var transferMove by remember { mutableStateOf(false) }
    var showMenuCustomization by remember { mutableStateOf(false) }
    var favoritePaths by remember { mutableStateOf(FileActionPreferences.favoritePaths(context)) }

    val noAppMessage = stringResource(R.string.no_app_to_open)
    val openWithTitle = stringResource(R.string.open_with)

    LaunchedEffect(currentPath, sortMode, showHidden, refreshTick) {
        loading = true
        selection = emptySet()
        rawItems = withContext(Dispatchers.IO) {
            FileRepository.listDir(File(currentPath), showHidden, sortMode)
        }
        loading = false
    }

    // Query once when the visible folders or refresh boundary changes, not on every
    // indexed batch. In-progress results stay visibly provisional until completion.
    LaunchedEffect(rawItems, indexStatus.refreshing) {
        val directoryPaths = rawItems.filter { it.isDirectory }.map { it.file.absolutePath }
        if (directoryPaths.isEmpty()) {
            folderStats = emptyMap()
        } else {
            folderStats = FileIndex.directoryStats(directoryPaths)
        }
    }

    // Folders follow Size/Date too (by indexed total / modified time) instead of always by name.
    val items = remember(rawItems, folderStats, sortMode) {
        when (sortMode) {
            SortMode.SIZE, SortMode.DATE -> {
                val dirs = rawItems.filter { it.isDirectory }
                val files = rawItems.filter { !it.isDirectory }
                val sortedDirs = if (sortMode == SortMode.SIZE)
                    dirs.sortedWith(compareByDescending<FileItem> { folderStats[it.file.absolutePath]?.totalBytes ?: 0L }
                        .thenBy { it.name.lowercase() })
                else dirs.sortedByDescending { it.lastModified }
                sortedDirs + files
            }
            else -> rawItems
        }
    }

    fun navigateTo(path: String) {
        if (path != currentPath) currentPath = path
    }

    fun toggleSelection(path: String) {
        selection = if (selection.contains(path)) selection - path else selection + path
    }

    fun openQuickActions() {
        quickActionsOpen = true
    }

    fun closeQuickActions() {
        quickActionsOpen = false
    }

    val selecting = selection.isNotEmpty()
    BackHandler(selecting) { selection = emptySet() }
    // The floating card is a selection companion: it leaves when the selection clears.
    LaunchedEffect(selecting) {
        if (!selecting) closeQuickActions()
    }
    val quickActionsShare: () -> Unit = {
        closeQuickActions()
        shareFiles(context, selection.map { File(it) })
    }
    val quickActionsRename: () -> Unit = {
        closeQuickActions()
        items.firstOrNull { it.file.absolutePath == selection.first() }?.let { renameTarget = it }
    }
    // System Back mirrors the toolbar arrow: one folder up, and the browser closes at the
    // folder it was opened at - Back always returns to wherever the browser was opened
    // from (dashboard shortcut, or the storage analysis that opened a folder).
    BackHandler(enabled = !selecting) {
        if (currentPath == startPath) onNavigateUp()
        else File(currentPath).parent?.let { navigateTo(it) } ?: onNavigateUp()
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            TopAppBar(
                title = { Text(stringResource(R.string.selected_count, selection.size)) },
                navigationIcon = {
                    IconButton(onClick = { selection = emptySet() }) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selection = items.map { it.file.absolutePath }.toSet()
                            openQuickActions()
                        },
                        modifier = Modifier.testTag("selection_all"),
                    ) {
                        Icon(SfeIcons.CheckBox, contentDescription = stringResource(R.string.select_all))
                    }
                }
            )
        } else {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath == startPath) onNavigateUp()
                        else File(currentPath).parent?.let { navigateTo(it) } ?: onNavigateUp()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                },
                title = {
                    Breadcrumb(
                        currentPath = currentPath,
                        rootPath = rootPath,
                        onNavigate = { navigateTo(it) }
                    )
                },
                actions = {
                    IconButton(onClick = { gridView = !gridView }) {
                        Icon(
                            if (gridView) SfeIcons.ViewList else SfeIcons.GridView,
                            contentDescription = stringResource(
                                if (gridView) R.string.view_list else R.string.view_grid
                            )
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.save_location)) }, leadingIcon = { Icon(SfeIcons.Link, null) }, onClick = {
                                com.stockfileexplorer.app.FileActionPreferences.saveShortcut(context, currentPath)
                                Toast.makeText(context, R.string.location_saved, Toast.LENGTH_SHORT).show()
                                menuOpen = false
                            })
                            Text(
                                text = stringResource(R.string.sort_by),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            SortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                SortMode.NAME -> stringResource(R.string.sort_name)
                                                SortMode.DATE -> stringResource(R.string.sort_date)
                                                SortMode.SIZE -> stringResource(R.string.sort_size)
                                                SortMode.TYPE -> stringResource(R.string.sort_type)
                                            }
                                        )
                                    },
                                    onClick = { sortMode = mode; com.stockfileexplorer.app.FileActionPreferences.saveBrowserSort(sortContext, mode.name); menuOpen = false }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (showHidden) R.string.hide_hidden else R.string.show_hidden
                                        )
                                    )
                                },
                                onClick = { showHidden = !showHidden; menuOpen = false }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.new_folder)) },
                                leadingIcon = { Icon(SfeIcons.CreateNewFolder, null) },
                                onClick = { showNewFolder = true; menuOpen = false }
                            )
                        }
                    }
                }
            )
        }

        val restrictedKind = remember(currentPath) { com.stockfileexplorer.app.data.RestrictedAccess.kindOf(currentPath) }
        // Only when Android really hides the folder: a normal listing that works stays the normal browser.
        val restrictedHidden by produceState(false, currentPath, refreshTick) {
            value = restrictedKind != null && withContext(Dispatchers.IO) { File(currentPath).list().let { it == null || it.isEmpty() } }
        }
        // A listing that works does not prove writing works: the grant and write test stay reachable.
        var restrictedOpen by remember(currentPath) { mutableStateOf(false) }
        if (restrictedKind != null && !restrictedHidden) TextButton(onClick = { restrictedOpen = !restrictedOpen }, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.restricted_access_link))
        }
        when {
            restrictedKind != null && (restrictedHidden || restrictedOpen) -> RestrictedAccessPanel(currentPath, restrictedKind)
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.empty_folder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            gridView -> LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
            ) {
                items(items.size, key = { items[it].file.absolutePath }) { index ->
                    val item = items[index]
                    FileGridCell(
                        item = item,
                        favorite = item.file.absolutePath in favoritePaths,
                        selected = selection.contains(item.file.absolutePath),
                        selecting = selecting,
                        directoryStats = folderStats[item.file.absolutePath],
                        statsProvisional = indexStatus.refreshing || indexStatus.incomplete ||
                                knownIncompleteFolder(item.file.absolutePath, rootPath),
                        onClick = {
                            if (selecting) toggleSelection(item.file.absolutePath)
                            else if (item.isDirectory) navigateTo(item.file.absolutePath)
                            else openFile(context, item.file, noAppMessage, items.filter { !it.isDirectory }.map { it.file })
                        },
                        onLongClick = {
                            toggleSelection(item.file.absolutePath)
                            quickActionsOpen = selection.isNotEmpty()
                        },
                        onMenu = { menuTarget = item }
                    )
                }
            }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(items.size, key = { items[it].file.absolutePath }) { index ->
                    val item = items[index]
                    FileListRow(
                        item = item,
                        favorite = item.file.absolutePath in favoritePaths,
                        selected = selection.contains(item.file.absolutePath),
                        selecting = selecting,
                        directoryStats = folderStats[item.file.absolutePath],
                        statsProvisional = indexStatus.refreshing || indexStatus.incomplete ||
                                knownIncompleteFolder(item.file.absolutePath, rootPath),
                        onClick = {
                            if (selecting) toggleSelection(item.file.absolutePath)
                            else if (item.isDirectory) navigateTo(item.file.absolutePath)
                            else openFile(context, item.file, noAppMessage, items.filter { !it.isDirectory }.map { it.file })
                        },
                        onLongClick = {
                            toggleSelection(item.file.absolutePath)
                            quickActionsOpen = selection.isNotEmpty()
                        },
                        trailing = {
                            Box {
                                IconButton(onClick = { menuTarget = item }) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(R.string.file_actions_for, item.file.name)
                                    )
                                }

                            }
                        }
                    )
                }
            }
        }
        if (selecting) {
            SelectionActionBar(
                onCopy = {
                    transferSources = items.filter { it.file.absolutePath in selection }
                    transferMove = false
                },
                onMove = {
                    transferSources = items.filter { it.file.absolutePath in selection }
                    transferMove = true
                },
                onDelete = { showDeleteConfirm = true },
            )
        }
    }

    // The floating Rename/Share card sits on the right edge while the selection is active.
    if (selecting && quickActionsOpen) {
        SelectionQuickActionsPopup(
            renameEnabled = selection.size == 1,
            onShare = quickActionsShare,
            onRename = quickActionsRename,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
    }

    menuTarget?.let { item ->
        FileActionMenu(
            name = item.name,
            isDirectory = item.isDirectory,
            isFavorite = item.file.absolutePath in favoritePaths,
            onDismiss = { menuTarget = null },
            callbacks = FileActionCallbacks(
                onSaveLocation = {
                    val location = if (item.isDirectory) item.file.absolutePath else item.file.parent
                    location?.let { com.stockfileexplorer.app.FileActionPreferences.saveShortcut(context, it) }
                    Toast.makeText(context, R.string.location_saved, Toast.LENGTH_SHORT).show()
                },
                onContainingFolder = { item.file.parent?.let { navigateTo(it) } },
                onOpen = {
                    if (item.isDirectory) navigateTo(item.file.absolutePath)
                    else openFile(context, item.file, noAppMessage, items.filter { !it.isDirectory }.map { it.file })
                },
                onCopy = {
                    transferSources = listOf(item)
                    transferMove = false
                },
                onMove = {
                    transferSources = listOf(item)
                    transferMove = true
                },
                onRename = { renameTarget = item },
                onProperties = { propertiesTarget = item },
                onFavorite = {
                    val nowFavorite = FileActionPreferences.toggleFavorite(context, item.file.absolutePath)
                    favoritePaths = FileActionPreferences.favoritePaths(context)
                    Toast.makeText(
                        context,
                        if (nowFavorite) R.string.favorite_added else R.string.favorite_removed,
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onShare = { shareFiles(context, listOf(item.file)) },
                onOpenWith = { openFileWithChooser(context, item.file, openWithTitle, noAppMessage) },
                onDelete = {
                    selection = setOf(item.file.absolutePath)
                    showDeleteConfirm = true
                },
                onSelect = {
                    selection = setOf(item.file.absolutePath)
                    openQuickActions()
                },
                onCustomize = { showMenuCustomization = true },
            ),
        )
    }

    if (transferSources.isNotEmpty()) {
        FolderPickerDialog(
            title = stringResource(
                if (transferMove) R.string.choose_move_destination else R.string.choose_copy_destination
            ),
            confirmLabel = stringResource(if (transferMove) R.string.move_here else R.string.copy_here),
            rootPath = rootPath,
            blockedPaths = transferSources.filter { it.isDirectory }.map { it.file.absolutePath }.toSet(),
            onDismiss = { transferSources = emptyList() },
            onConfirm = { destination ->
                val sources = transferSources
                val move = transferMove
                transferSources = emptyList()
                scope.launch {
                    val succeeded = withContext(Dispatchers.IO) {
                        sources.filter { source ->
                            FileOperations.transfer(source.file, destination, move)
                        }
                    }
                    if (succeeded.isNotEmpty()) {
                        Toast.makeText(
                            context,
                            if (move) R.string.moved_ok else R.string.copied_ok,
                            Toast.LENGTH_SHORT,
                        ).show()
                        com.stockfileexplorer.app.data.FileIndex.invalidate()
                        refreshTick++
                    } else {
                        Toast.makeText(context, R.string.transfer_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    propertiesTarget?.let { item ->
        PropertiesDialog(
            name = item.name,
            path = item.file.absolutePath,
            isDirectory = item.isDirectory,
            sizeBytes = item.size,
            modified = item.lastModified,
            favorite = item.file.absolutePath in favoritePaths,
            onDismiss = { propertiesTarget = null },
        )
    }

    if (showMenuCustomization) {
        MenuCustomizationDialog(onDismiss = { showMenuCustomization = false })
    }

    if (showNewFolder) {
        TextInputDialog(
            title = stringResource(R.string.new_folder),
            initialValue = "",
            confirmLabel = stringResource(R.string.create),
            onConfirm = { name ->
                showNewFolder = false
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { File(currentPath, name).mkdirs() }
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.folder_created)
                        else context.getString(R.string.folder_create_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (ok) {
                        com.stockfileexplorer.app.data.FileIndex.invalidate()
                        refreshTick++
                    }
                }
            },
            onDismiss = { showNewFolder = false }
        )
    }

    renameTarget?.let { target ->
        TextInputDialog(
            title = stringResource(R.string.rename),
            initialValue = target.name,
            helperText = stringResource(R.string.rename_whole_name),
            validate = { com.stockfileexplorer.app.data.Rename.validName(it) },
            confirmLabel = stringResource(R.string.rename),
            onConfirm = { newName ->
                renameTarget = null
                selection = emptySet()
                scope.launch {
                    val ok = withContext(Dispatchers.IO) {
                        com.stockfileexplorer.app.data.Rename.move(target.file, newName)
                    }
                    if (ok) com.stockfileexplorer.app.data.FileIndex.renamed(target.file, File(target.file.parentFile, newName))
                    if (!ok) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.rename_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        com.stockfileexplorer.app.data.FileIndex.invalidate()
                    }
                    refreshTick++
                }
            },
            onDismiss = { renameTarget = null }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.delete_confirm_title, selection.size),
            body = stringResource(R.string.delete_confirm_body),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                showDeleteConfirm = false
                val toDelete = selection
                selection = emptySet()
                scope.launch {
                    val results = withContext(Dispatchers.IO) {
                        toDelete.map { File(it) }.map { it to com.stockfileexplorer.app.data.Trash.moveToTrash(it) }
                    }
                    com.stockfileexplorer.app.data.FileIndex.removed(results.filter { it.second }.map { it.first })
                    val allOk = results.all { it.second }
                    Toast.makeText(
                        context,
                        if (allOk) context.getString(R.string.deleted_ok)
                        else context.getString(R.string.delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    com.stockfileexplorer.app.data.FileIndex.invalidate()
                    refreshTick++
                }
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
fun Breadcrumb(currentPath: String, rootPath: String, onNavigate: (String) -> Unit) {
    val segments = remember(currentPath, rootPath) {
        val list = mutableListOf<Pair<String, String>>()
        var f: File? = File(currentPath)
        while (f != null) {
            val label = if (f.absolutePath == rootPath) "/" else f.name
            list.add(0, label to f.absolutePath)
            if (f.absolutePath == rootPath) break
            f = f.parentFile
        }
        list
    }
    LazyRow(verticalAlignment = Alignment.CenterVertically) {
        items(segments.size) { index ->
            val (label, path) = segments[index]
            val isLast = index == segments.size - 1
            TextButton(onClick = { if (!isLast) onNavigate(path) }) {
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isLast) androidx.compose.ui.text.font.FontWeight.Bold
                    else androidx.compose.ui.text.font.FontWeight.Normal
                )
            }
            if (!isLast) {
                Text(">", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileListRow(
    item: FileItem,
    favorite: Boolean = false,
    selected: Boolean,
    selecting: Boolean,
    directoryStats: FileIndex.DirectoryStats?,
    statsProvisional: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    val background = if (selected) MaterialTheme.colorScheme.secondaryContainer
    else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(4.dp))
        }
        FileIcon(item = item, size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (favorite) {
                    Icon(
                        imageVector = SfeIcons.Star,
                        contentDescription = stringResource(R.string.favorite),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            }
            Text(
                text = if (item.isDirectory)
                    folderMetadata(item, directoryStats, statsProvisional)
                else FileLogic.formatSize(item.size) + " • " + FileLogic.formatDate(item.lastModified),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (item.isDirectory) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        trailing?.invoke()
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCell(
    item: FileItem,
    favorite: Boolean = false,
    selected: Boolean,
    selecting: Boolean,
    directoryStats: FileIndex.DirectoryStats?,
    statsProvisional: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                FileIcon(item = item, size = 56)
                if (favorite) {
                    Icon(
                        imageVector = SfeIcons.Star,
                        contentDescription = stringResource(R.string.favorite),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopStart).size(18.dp),
                    )
                }
                if (selecting) {
                    Icon(
                        imageVector = if (selected) SfeIcons.CheckBox else SfeIcons.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.file_actions_for, item.file.name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clickable { onMenu() }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.isDirectory)
                    folderMetadata(item, directoryStats, statsProvisional)
                else FileLogic.formatSize(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun knownIncompleteFolder(path: String, rootPath: String): Boolean {
    val androidRoot = "$rootPath/Android"
    return path == androidRoot ||
            path.startsWith("$androidRoot/data/") || path == "$androidRoot/data" ||
            path.startsWith("$androidRoot/obb/") || path == "$androidRoot/obb"
}

@Composable
private fun folderMetadata(
    item: FileItem,
    stats: FileIndex.DirectoryStats?,
    provisional: Boolean
): String {
    val date = FileLogic.formatDate(item.lastModified)
    return when {
        stats == null -> stringResource(R.string.folder_stats_pending, date)
        provisional -> stringResource(
            R.string.folder_stats_partial,
            stats.fileCount,
            FileLogic.formatSize(stats.totalBytes),
            date
        )
        else -> stringResource(
            R.string.folder_stats,
            stats.fileCount,
            FileLogic.formatSize(stats.totalBytes),
            date
        )
    }
}

@Composable
fun FileIcon(item: FileItem, size: Int) {
    if (item.isDirectory) {
        FolderGlyph(size.dp)
    } else if (FileLogic.isImage(item.name) || item.category == FileCategory.VIDEOS) {
        val isVideo = item.category == FileCategory.VIDEOS
        val cacheKey = "${item.file.absolutePath}:${item.size}:${item.lastModified}"
        val thumb = produceState<ImageBitmap?>(null, cacheKey) {
            value = withContext(thumbnailDispatcher) {
                if (isVideo) loadVideoThumbnail(item.file, cacheKey) else loadThumbnail(item.file, 128, cacheKey)
            }
        }.value
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = stringResource(if (item.category == FileCategory.VIDEOS) R.string.video_thumbnail else R.string.picture_thumbnail, item.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size.dp)
            )
        } else {
            Icon(
                imageVector = categoryIcon(item.category),
                contentDescription = null,
                tint = categoryColor(item.category),
                modifier = Modifier.size(size.dp)
            )
        }
    } else {
        Icon(
            imageVector = categoryIcon(item.category),
            contentDescription = null,
            tint = categoryColor(item.category),
            modifier = Modifier.size(size.dp)
        )
    }
}
