package com.stockfileexplorer.app.ui

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.stockfileexplorer.app.R
import com.stockfileexplorer.app.data.FileLogic
import com.stockfileexplorer.app.theme.AmberDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local tab: one compact storage card (opens the storage analysis) and a consistent
 * 2-column shortcut grid: Hauptspeicher, Downloads, Apps, Papierkorb.
 * The media categories live in the Library tab.
 */
@Composable
fun DashboardScreen(
    onOpenStorage: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenStorageAnalysis: () -> Unit,
    onOpenPapierkorb: () -> Unit = {}
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    val usage by com.stockfileexplorer.app.data.VolumeStats.usage.collectAsState()
    val totalBytes = usage?.totalBytes ?: 0L
    val freeBytes = usage?.freeBytes ?: 0L
    val status by com.stockfileexplorer.app.data.FileIndex.state.collectAsState()

    // Read when the dashboard appears and whenever the app comes back to the foreground, so the
    // numbers follow deletes made elsewhere (bin, other apps) without waiting for a file scan.
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { com.stockfileexplorer.app.data.VolumeStats.refresh(root) } }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) Thread { com.stockfileexplorer.app.data.VolumeStats.refresh(root) }.start()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val usedFraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenStorageAnalysis() },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        val barColor = MaterialTheme.colorScheme.primary
                        Canvas(Modifier.size(64.dp)) {
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = barColor,
                                startAngle = -90f,
                                sweepAngle = 360f * usedFraction,
                                useCenter = false,
                                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "${(usedFraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.internal_storage),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.used_of,
                                FileLogic.formatSize(usedBytes),
                                FileLogic.formatSize(totalBytes)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.free_space, FileLogic.formatSize(freeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = SfeIcons.Analytics,
                        contentDescription = stringResource(R.string.storage_analysis),
                        tint = AmberDark,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        if (status.refreshing || status.incomplete) {
            item(span = { GridItemSpan(2) }) {
                Text(
                    stringResource(if (status.refreshing) R.string.index_updating else R.string.index_incomplete),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        item { ShortcutCard(SfeIcons.Folder, Color(0xFFFF9800), stringResource(R.string.main_storage), onOpenStorage) }
        item { ShortcutCard(SfeIcons.Download, Color(0xFF009688), stringResource(R.string.downloads), onOpenDownloads) }
        item { ShortcutCard(SfeIcons.Android, Color(0xFF4CAF50), stringResource(R.string.cat_apps), onOpenApps) }
        item { ShortcutCard(Icons.Filled.Delete, Color(0xFF607D8B), stringResource(R.string.papierkorb), onOpenPapierkorb) }
    }
}

@Composable
private fun ShortcutCard(icon: ImageVector, tint: Color, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).background(tint, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Folder-shaped yellow backdrop behind the header and storage card. The background itself
 * is the folder (tab on the top left), with a soft gradient and subtle edge shading for a
 * modern 3D feel. Decorative only; text and cards stay on top.
 */
@Composable
fun FolderBackdrop(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val tabH = 26.dp.toPx()
        val tabW = w * 0.46f
        val corner = 20.dp.toPx()
        val fillet = 14.dp.toPx()

        val folder = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(tabW - corner, 0f)
            quadraticBezierTo(tabW, 0f, tabW, corner)
            lineTo(tabW, tabH - fillet)
            quadraticBezierTo(tabW, tabH, tabW + fillet, tabH)
            lineTo(w - corner, tabH)
            quadraticBezierTo(w, tabH, w, tabH + corner)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }

        drawPath(
            path = folder,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0xFFFFECB3), Color(0xFFFFD54F)),
                startY = 0f,
                endY = h
            )
        )

        // Light catch on the tab so it reads as the raised part of the folder.
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0x40FFFFFF), Color(0x00FFFFFF)),
                startY = 0f,
                endY = tabH
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(tabW, tabH)
        )

        // Soft shadow the tab casts onto the folder body.
        val shadowH = 12.dp.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0x1F000000), Color(0x00000000)),
                startY = tabH,
                endY = tabH + shadowH
            ),
            topLeft = androidx.compose.ui.geometry.Offset(tabW + fillet, tabH),
            size = androidx.compose.ui.geometry.Size(w - tabW - fillet, shadowH)
        )

        // Gentle grounding shadow along the bottom edge.
        val groundH = 18.dp.toPx()
        drawRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                colors = listOf(Color(0x00000000), Color(0x14000000)),
                startY = h - groundH,
                endY = h
            ),
            topLeft = androidx.compose.ui.geometry.Offset(0f, h - groundH),
            size = androidx.compose.ui.geometry.Size(w, groundH)
        )
    }
}
