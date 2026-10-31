package com.stockfileexplorer.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileIndex
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.data.Trash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapierkorbScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<Trash.Entry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Trash.Entry?>(null) }
    var confirmDelete by remember { mutableStateOf<Trash.Entry?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    fun reload() { scope.launch { entries = withContext(Dispatchers.IO) { Trash.list() }; loaded = true } }
    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.papierkorb)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null) } },
            actions = {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { confirmEmpty = true }, modifier = Modifier.testTag("papierkorb_empty_action")) {
                        Text(stringResource(R.string.papierkorb_empty_action))
                    }
                }
            },
        )
        Text(stringResource(R.string.papierkorb_note), style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (loaded && entries.isEmpty()) {
            Text(stringResource(R.string.papierkorb_empty), Modifier.padding(16.dp))
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clickable { selected = e }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (e.isDirectory) SfeIcons.Folder else Icons.Filled.Delete, null, Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(FileLogic.formatSize(e.sizeBytes) + " • " + FileLogic.formatDate(e.deletedAt), style = MaterialTheme.typography.bodySmall)
                        Text(e.originalPath, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HorizontalDivider()
            }
        }
    }
    selected?.let { e ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(e.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = { Text(e.originalPath) },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    scope.launch {
                        val restored = withContext(Dispatchers.IO) { Trash.restore(e) }
                        Toast.makeText(context, if (restored != null) R.string.restored_ok else R.string.restore_failed, Toast.LENGTH_SHORT).show()
                        FileIndex.invalidate(); reload()
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { selected = null; confirmDelete = e }) { Text(stringResource(R.string.delete_permanently)) }
                    TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }
    confirmDelete?.let { e ->
        ConfirmDialog(
            title = stringResource(R.string.delete_permanently_title, e.name),
            body = stringResource(R.string.delete_permanently_body),
            confirmLabel = stringResource(R.string.delete_permanently),
            onConfirm = {
                confirmDelete = null
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Trash.deletePermanently(e) }
                    Toast.makeText(context, if (ok) R.string.deleted_permanently_ok else R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    FileIndex.invalidate(); reload()
                    com.stockfileexplorer.app.data.VolumeStats.refreshAfterDelete()
                }
            },
            onDismiss = { confirmDelete = null },
        )
    }
    if (confirmEmpty) {
        ConfirmDialog(
            title = stringResource(R.string.papierkorb_empty_confirm_title),
            body = stringResource(R.string.papierkorb_empty_confirm_body),
            confirmLabel = stringResource(R.string.papierkorb_empty_action),
            onConfirm = {
                confirmEmpty = false
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { Trash.empty() }
                    Toast.makeText(context, if (ok) R.string.papierkorb_emptied else R.string.delete_failed, Toast.LENGTH_SHORT).show()
                    FileIndex.invalidate(); reload()
                    com.stockfileexplorer.app.data.VolumeStats.refreshAfterDelete()
                }
            },
            onDismiss = { confirmEmpty = false },
        )
    }
}
