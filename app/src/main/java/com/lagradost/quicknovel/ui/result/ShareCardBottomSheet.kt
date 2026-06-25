package com.lagradost.quicknovel.ui.result

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.CommonActivity
import com.lagradost.quicknovel.ui.UiImage
import com.lagradost.quicknovel.ui.theme.LoadingIndicator
import com.lagradost.quicknovel.util.ShareCardEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareCardBottomSheet(
    image: UiImage?,
    title: String,
    author: String?,
    rating: Int?,
    tags: List<String>?,
    synopsis: String? = null,
    apiName: String = "NeoQN",
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showTitle by remember { mutableStateOf(true) }
    var showAuthor by remember { mutableStateOf(true) }
    var showRating by remember { mutableStateOf(true) }
    var showTags by remember { mutableStateOf(true) }

    var isPremiumStyle by remember { mutableStateOf(false) }
    var premiumWord by remember { mutableStateOf("VISION") }
    var blurRadius by remember { mutableStateOf(10f) }
    
    var focusHeightPct by remember { mutableStateOf(100f) } // 10% to 100% height
    var focusPositionXPct by remember { mutableStateOf(35f) } // 0% to 100% X center

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(true) }
    var isSharing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(image, title, author, rating, tags, showTitle, showAuthor, showRating, showTags, isPremiumStyle, premiumWord, synopsis, apiName, blurRadius, focusHeightPct, focusPositionXPct) {
        isGenerating = true
        try {
            val heightVal = (focusHeightPct / 100f) * 1500f
            val centerYVal = 800f
            var top = centerYVal - (heightVal / 2f)
            var bottom = centerYVal + (heightVal / 2f)
            if (top < 50f) {
                val diff = 50f - top
                top = 50f
                bottom = (bottom + diff).coerceAtMost(1550f)
            } else if (bottom > 1550f) {
                val diff = bottom - 1550f
                bottom = 1550f
                top = (top - diff).coerceAtLeast(50f)
            }

            val centerXVal = 150f + (focusPositionXPct / 100f) * 780f
            val left = centerXVal - 100f
            val right = centerXVal + 100f

            bitmap = ShareCardEngine.generateShareCard(
                context = context,
                image = image,
                title = title,
                author = author,
                rating = rating,
                tags = tags,
                apiName = apiName,
                showTitle = showTitle,
                showAuthor = showAuthor,
                showRating = showRating,
                showTags = showTags,
                isPremium = isPremiumStyle,
                premiumWord = premiumWord,
                synopsis = synopsis,
                blurRadius = blurRadius.roundToInt(),
                focusLeft = left,
                focusTop = top,
                focusRight = right,
                focusBottom = bottom
            )
        } catch (t: Throwable) {
            CommonActivity.showToast("Failed to generate share card")
        } finally {
            isGenerating = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Share Novel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Preview Zone
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                if (isGenerating) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Generating Share Card...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    val bmp = bitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Share card preview",
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(1080f / 1600f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "Failed to render card preview",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Poster Style
            Text(
                text = "Poster Style",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(top = 16.dp, bottom = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !isPremiumStyle,
                    onClick = { isPremiumStyle = false },
                    label = { Text("Standard", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = isPremiumStyle,
                    onClick = { isPremiumStyle = true },
                    label = { Text("Premium (Vision)", fontSize = 12.sp) }
                )
            }

            // Custom Premium Word TextField & Blur Level Slider
            if (isPremiumStyle) {
                OutlinedTextField(
                    value = premiumWord,
                    onValueChange = { input ->
                        val filtered = input.trim().take(14).takeWhile { !it.isWhitespace() }
                        premiumWord = filtered
                    },
                    label = { Text("Custom Word (Max 14 letters)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Background Blur Level",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${blurRadius.roundToInt()}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = blurRadius,
                        onValueChange = { blurRadius = it },
                        valueRange = 1f..50f,
                        steps = 48,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }


                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus Window Position (X)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${focusPositionXPct.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = focusPositionXPct,
                        onValueChange = { focusPositionXPct = it },
                        valueRange = 0f..100f,
                        steps = 100,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Focus Window Height",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${focusHeightPct.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = focusHeightPct,
                        onValueChange = { focusHeightPct = it },
                        valueRange = 10f..100f,
                        steps = 90,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Options Row
            if (!isPremiumStyle) {
                val hasAuthorField = !author.isNullOrBlank()
                val hasRatingField = rating != null && rating > 0
                val hasTagsField = !tags.isNullOrEmpty()

                if (hasAuthorField || hasRatingField || hasTagsField) {
                    Text(
                        text = "Card Content Options",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(top = 8.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showTitle,
                            onClick = { showTitle = !showTitle },
                            label = { Text("Title", fontSize = 11.sp) }
                        )
                        if (hasAuthorField) {
                            FilterChip(
                                selected = showAuthor,
                                onClick = { showAuthor = !showAuthor },
                                label = { Text("Author", fontSize = 11.sp) }
                            )
                        }
                        if (hasRatingField) {
                            FilterChip(
                                selected = showRating,
                                onClick = { showRating = !showRating },
                                label = { Text("Rating", fontSize = 11.sp) }
                            )
                        }
                        if (hasTagsField) {
                            FilterChip(
                                selected = showTags,
                                onClick = { showTags = !showTags },
                                label = { Text("Genres", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Save Button
                FilledTonalButton(
                    onClick = {
                        val bmp = bitmap
                        if (bmp != null && !isSaving) {
                            coroutineScope.launch {
                                isSaving = true
                                val uri = ShareCardEngine.saveImageToGallery(context, bmp)
                                if (uri != null) {
                                    CommonActivity.showToast("Saved to Pictures/NeoQN")
                                } else {
                                    CommonActivity.showToast("Failed to save image")
                                }
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = bitmap != null && !isSaving,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save")
                    }
                }

                // Share Button
                Button(
                    onClick = {
                        val bmp = bitmap
                        if (bmp != null && !isSharing) {
                            coroutineScope.launch {
                                isSharing = true
                                val uri = ShareCardEngine.getShareCacheUri(context, bmp)
                                if (uri != null) {
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "image/jpeg"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share Novel Card"))
                                } else {
                                    CommonActivity.showToast("Failed to share card")
                                }
                                isSharing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = bitmap != null && !isSharing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
