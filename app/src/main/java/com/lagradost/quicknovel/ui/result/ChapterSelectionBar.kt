package com.lagradost.quicknovel.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.R

// ─── Shared Chapter Selection Bottom Bar ──────────────────────────────────────
// Used by ResultDetailModernScreen, ResultDetailDefaultScreen, and NovelTabScreen.
// Keeps UX consistent and avoids duplicating the bar in each screen file.
//
// Parameters:
//   topCornerRadius  – callers pass their own value so the bar matches the host
//                      screen's surface style (Modern = 24.dp, Default = 20.dp)
//   isBatchDownloading – when true the download icon shows a disabled state so
//                        the user knows the job has been queued already
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun ChapterSelectionBar(
    selectedCount: Int,
    isBatchDownloading: Boolean,
    topCornerRadius: Dp = 24.dp,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onBookmark: () -> Unit,
    onUnbookmark: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(
        tonalElevation  = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = topCornerRadius, topEnd = topCornerRadius),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // ── Top row: Close | count | Select-All ───────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) {
                    Text(stringResource(R.string.close))
                }
                Text(
                    text = if (selectedCount == 0) stringResource(R.string.no_data)
                           else "$selectedCount Selected",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onSelectAll) { Text("All") }
            }

            // ── Bottom row: actions ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBookmark,
                    enabled = selectedCount > 0
                ) { Text("Bookmark") }

                TextButton(
                    onClick = onUnbookmark,
                    enabled = selectedCount > 0
                ) { Text("Unbookmark") }

                TextButton(
                    onClick = onMarkRead,
                    enabled = selectedCount > 0
                ) { Text("Read") }

                TextButton(
                    onClick = onMarkUnread,
                    enabled = selectedCount > 0
                ) { Text("Unread") }

                // ── Download button ───────────────────────────────────────────
                // Disabled while a batch job is already running to prevent
                // duplicate queue submissions for the same index set.
                IconButton(
                    onClick = onDownload,
                    enabled = selectedCount > 0 && !isBatchDownloading
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download selected chapters",
                        tint = if (selectedCount > 0 && !isBatchDownloading)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }
}
