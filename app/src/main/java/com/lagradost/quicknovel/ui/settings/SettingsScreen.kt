package com.lagradost.quicknovel.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.glassCard

/**
 * Custom spring press interaction effect for modern premium micro-interactions.
 */
fun Modifier.springPressEffect(): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "spring_press"
    )
    
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                }
            )
        }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToSubSettings: (xmlRes: Int, iconRes: Int, titleRes: Int) -> Unit,
    onNavigateToReadingStats: () -> Unit,
    showAboutDialog: () -> Unit,
    onOpenSocialUrl: (url: String) -> Unit
) {
    val scrollState = rememberScrollState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            
            // ─── Top Toolbar ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_search_24),
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // ─── Header Title ─────────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.02.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            
            // ─── Hero About Card ──────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .springPressEffect()
                    .glassCard(shape = RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = showAboutDialog
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo Box
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .glassCard(
                                shape = RoundedCornerShape(16.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_quicknovel),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = "NeoQN",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        )
                        Text(
                            text = "Premium Reader Experience",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }
            
            // ─── Reading Stats Card ───────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .springPressEffect()
                    .glassCard(shape = RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onNavigateToReadingStats
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .glassCard(
                                shape = RoundedCornerShape(14.dp),
                                backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_history_24),
                            contentDescription = "Stats",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Text(
                        text = "Reading Status",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                }
            }
            
            // ─── Bento Grid ───────────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedGrid(12.dp)
            ) {
                // Row 1: Appearance & Reader
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoItem(
                        modifier = Modifier.weight(1f),
                        title = "Appearance",
                        iconRes = R.drawable.ic_baseline_color_lens_24,
                        onClick = {
                            onNavigateToSubSettings(
                                R.xml.settings_appearance,
                                R.drawable.ic_baseline_color_lens_24,
                                R.string.appearance
                            )
                        }
                    )
                    
                    BentoItem(
                        modifier = Modifier.weight(1f),
                        title = "Reader",
                        iconRes = R.drawable.ic_baseline_menu_book_24,
                        onClick = {
                            onNavigateToSubSettings(
                                R.xml.settings_general,
                                R.drawable.ic_baseline_menu_book_24,
                                R.string.reader
                            )
                        }
                    )
                }
                
                // Row 2: Storage & Advanced
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoItem(
                        modifier = Modifier.weight(1f),
                        title = "Storage",
                        iconRes = R.drawable.ic_baseline_cloud_24,
                        onClick = {
                            onNavigateToSubSettings(
                                R.xml.settings_storage,
                                R.drawable.ic_baseline_cloud_24,
                                R.string.storage
                            )
                        }
                    )
                    
                    BentoItem(
                        modifier = Modifier.weight(1f),
                        title = "Advanced",
                        iconRes = R.drawable.ic_baseline_tune_24,
                        onClick = {
                            onNavigateToSubSettings(
                                R.xml.settings_dev,
                                R.drawable.ic_baseline_tune_24,
                                R.string.advanced
                            )
                        }
                    )
                }
                
                // Row 3: Vibe & Aura (Full width)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    BentoItem(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Vibe & Aura",
                        iconRes = R.drawable.ic_baseline_star_24,
                        onClick = {
                            onNavigateToSubSettings(
                                R.xml.settings_vibe,
                                R.drawable.ic_baseline_star_24,
                                R.string.vibe_aura
                            )
                        },
                        isFullWidth = true
                    )
                }
            }
            
            // ─── Social Chips Row ─────────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SocialChipSegment(
                        modifier = Modifier.weight(1f),
                        title = "Discord",
                        iconRes = R.drawable.ic_baseline_discord_24,
                        onClick = { onOpenSocialUrl("https://discord.gg/uvFXvtS3u8") }
                    )
                    
                    VerticalDivider(
                        color = Color(0x1AFFFFFF),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 14.dp)
                            .width(1.dp)
                    )
                    
                    SocialChipSegment(
                        modifier = Modifier.weight(1f),
                        title = "Telegram",
                        iconRes = R.drawable.ic_telegram,
                        onClick = { onOpenSocialUrl("https://t.me/+i9MSwgeoXzU0NTE1") }
                    )
                    
                    VerticalDivider(
                        color = Color(0x1AFFFFFF),
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = 14.dp)
                            .width(1.dp)
                    )
                    
                    SocialChipSegment(
                        modifier = Modifier.weight(1f),
                        title = "GitHub",
                        iconRes = R.drawable.ic_github_logo,
                        onClick = { onOpenSocialUrl("https://github.com/Shadyteal2/QuickNovel-Enhanced") }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(130.dp))
        }
    }
}

@Composable
fun BentoItem(
    modifier: Modifier = Modifier,
    title: String,
    iconRes: Int,
    onClick: () -> Unit,
    isFullWidth: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .springPressEffect()
            .glassCard(shape = RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = if (isFullWidth) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glassCard(
                        shape = RoundedCornerShape(14.dp),
                        backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
            }
            
            if (isFullWidth) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }
    }
}

@Composable
fun SocialChipSegment(
    modifier: Modifier = Modifier,
    title: String,
    iconRes: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .springPressEffect()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * Arrangement utility helper since Arrangement.spacedBy inside a Column lacks Grid row alignment features.
 */
fun Arrangement.spacedGrid(space: androidx.compose.ui.unit.Dp): Arrangement.Vertical {
    return Arrangement.spacedBy(space)
}
