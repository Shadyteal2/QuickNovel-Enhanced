package com.lagradost.quicknovel.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.preference.PreferenceManager
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme
import com.lagradost.quicknovel.ui.theme.glassCard
import com.lagradost.quicknovel.util.UsageStatsManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings = remember(context) { PreferenceManager.getDefaultSharedPreferences(context) }
    val imageUri = remember(settings) { settings.getString(context.getString(R.string.background_image_key), null) }
    val hasBackground = !imageUri.isNullOrBlank()

    // Database / DataStore reloading trigger
    var reloadTrigger by remember { mutableStateOf(0) }

    // Dialog state for setting goals
    var goalToChange by remember { mutableStateOf<String?>(null) } // "Daily" or "Weekly"
    var showGoalDialog by remember { mutableStateOf(false) }

    // Stats calculations running in a safe remember block driven by reloadTrigger
    val stats = remember(reloadTrigger) {
        // 1. Get totals and streaks
        val totalMs = context.getKey<Long>("TOTAL_READING_TIME", 0L) ?: 0L
        val currentStreak = context.getKey<Int>("CURRENT_STREAK", 0) ?: 0
        val bestStreak = context.getKey<Int>("BEST_STREAK", 0) ?: 0
        val totalChapters = context.getKey<Int>("TOTAL_CHAPTERS_READ", 0) ?: 0
        val customizations = context.getKey<Int>("CUSTOMIZATION_COUNT", 0) ?: 0

        // 2. Accurate Daily/Weekly/Monthly calculations (Separate Calendar instance to prevent shift bugs)
        val calendar = Calendar.getInstance()
        val originalTime = calendar.time
        
        val todayMs = UsageStatsManager.getDailyTimeMs(context, originalTime)
        
        var weekMs = 0L
        val weekHeights = mutableListOf<Long>()
        val weekLabels = mutableListOf<String>()
        val dayFormat = SimpleDateFormat("E", Locale.getDefault())

        for (i in 0 until 7) {
            val dayTime = UsageStatsManager.getDailyTimeMs(context, calendar.time)
            weekMs += dayTime
            weekHeights.add(0, dayTime)
            weekLabels.add(0, dayFormat.format(calendar.time).first().toString())
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        // Correct: Restore calendar time to today before running month loop
        calendar.time = originalTime
        var monthMs = 0L
        for (i in 0 until 30) {
            monthMs += UsageStatsManager.getDailyTimeMs(context, calendar.time)
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        val totalMinutes = todayMs / (1000 * 60)
        val weekMinutes = weekMs / (1000 * 60)
        val monthMinutes = monthMs / (1000 * 60)
        val totalHours = totalMs / (1000 * 60 * 60)

        // Level Profile Ratio
        val hoursPerLevel = 5
        val currentLevel = (totalHours / hoursPerLevel) + 1
        val progressHours = totalHours % hoursPerLevel
        val progressPercentage = (progressHours.toFloat() / hoursPerLevel).coerceIn(0f, 1f)

        // Reader levels lists
        val levelTitles = listOf("Novice", "Apprentice", "Scholar", "Sage", "Legend")
        val titleIdx = (currentLevel - 1).toInt().coerceIn(0, levelTitles.size - 1)
        val currentRank = levelTitles[titleIdx]

        // Goals
        val dailyGoal = context.getKey<Int>("DAILY_GOAL_MIN", 30) ?: 30
        val weeklyGoal = context.getKey<Int>("WEEKLY_GOAL_MIN", 180) ?: 180

        val dailyProgress = (totalMinutes.toFloat() / dailyGoal.toFloat()).coerceIn(0f, 1f)
        val weeklyProgress = (weekMinutes.toFloat() / weeklyGoal.toFloat()).coerceIn(0f, 1f)

        StatsData(
            totalHours = totalHours,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            totalChapters = totalChapters,
            customizations = customizations,
            todayMinutes = totalMinutes,
            weekMinutes = weekMinutes,
            monthMinutes = monthMinutes,
            currentLevel = currentLevel,
            progressHours = progressHours,
            progressPercentage = progressPercentage,
            currentRank = currentRank,
            dailyGoal = dailyGoal,
            weeklyGoal = weeklyGoal,
            dailyProgress = dailyProgress,
            weeklyProgress = weeklyProgress,
            weekHeights = weekHeights,
            weekLabels = weekLabels
        )
    }

    QuickNovelTheme {
        val containerColor = if (hasBackground) Color.Transparent else MaterialTheme.colorScheme.background
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.reading_status_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                shareStatistics(context, stats)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                )
            },
            containerColor = containerColor,
            modifier = modifier.fillMaxSize()
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // 1. Level Profile Card (Hero Display)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Level Circle Avatar
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Rank Title Clickable
                                Text(
                                    text = stats.currentRank,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        // Ranks dialog or info can trigger standard popup if requested
                                    }
                                )

                                Text(
                                    text = "Level ${stats.currentLevel} Reader",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Progress Indicator
                                LinearProgressIndicator(
                                    progress = { stats.progressPercentage },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "${stats.progressHours}h read / 5h to next level",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // 2. Metrics Pills Cards
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PillMetricCard(
                                label = "Streak",
                                value = "🔥 ${stats.currentStreak}d",
                                modifier = Modifier.weight(1f)
                            )
                            PillMetricCard(
                                label = "Chapters",
                                value = "📖 ${stats.totalChapters}",
                                modifier = Modifier.weight(1f)
                            )
                            PillMetricCard(
                                label = "Reading",
                                value = "🕒 ${stats.totalHours}h",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // 3. Streak Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "${stats.currentStreak} Day Streak",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Best Streak: ${stats.bestStreak} days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    // 4. Goals Section
                    item {
                        Text(
                            text = "My Goals",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            GoalCard(
                                type = "Daily",
                                current = stats.todayMinutes,
                                target = stats.dailyGoal,
                                progress = stats.dailyProgress,
                                onClick = {
                                    goalToChange = "Daily"
                                    showGoalDialog = true
                                }
                            )
                            GoalCard(
                                type = "Weekly",
                                current = stats.weekMinutes,
                                target = stats.weeklyGoal,
                                progress = stats.weeklyProgress,
                                onClick = {
                                    goalToChange = "Weekly"
                                    showGoalDialog = true
                                }
                            )
                        }
                    }

                    // 5. Weekly Stats (Bar Chart Card)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Weekly Activity",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    val maxTime = stats.weekHeights.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                                    stats.weekHeights.zip(stats.weekLabels).forEachIndexed { idx, (heightVal, label) ->
                                        val barHeightPercent = (heightVal.toFloat() / maxTime.toFloat()).coerceAtLeast(0.05f)
                                        val isToday = idx == stats.weekHeights.size - 1

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(14.dp)
                                                    .fillMaxHeight(barHeightPercent * 0.82f)
                                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                    .background(
                                                        if (isToday) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                                    )
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 6. Time Ranges Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .glassCard(RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TimeGridCell(label = "Today", minutes = stats.todayMinutes)
                                TimeGridCell(label = "This Week", minutes = stats.weekMinutes)
                                TimeGridCell(label = "This Month", minutes = stats.monthMinutes)
                            }
                        }
                    }

                    // 7. Achievements Section
                    item {
                        Text(
                            text = "Reader Achievements",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val achievementTiers = remember(stats) {
                                getAchievementsList(stats)
                            }
                            achievementTiers.forEach { ach ->
                                AchievementCard(ach = ach)
                            }
                        }
                    }
                }
            }
        }
    }

    // Set Goal dialog overlay
    if (showGoalDialog && goalToChange != null) {
        val defaultVal = if (goalToChange == "Daily") 30 else 180
        val datastoreKey = if (goalToChange == "Daily") "DAILY_GOAL_MIN" else "WEEKLY_GOAL_MIN"
        var inputValue by remember { mutableStateOf(context.getKey<Int>(datastoreKey, defaultVal)?.toString() ?: "") }

        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(text = "Set $goalToChange Goal", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Enter your goal in minutes:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { inputValue = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        placeholder = { Text("e.g., 30") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = inputValue.toIntOrNull() ?: defaultVal
                        context.setKey(datastoreKey, value)
                        showGoalDialog = false
                        reloadTrigger++
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PillMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .glassCard(RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun GoalCard(
    type: String,
    current: Long,
    target: Int,
    progress: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$type Reading Goal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Goal",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "$current / $target min",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun TimeGridCell(
    label: String,
    minutes: Long
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Text(
            text = "${minutes}m",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun AchievementCard(ach: Achievement) {
    val isCompleted = ach.currentProgress >= ach.maxProgress
    val progressPercent = (ach.currentProgress.toFloat() / ach.maxProgress.toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (ach.icon) {
                        R.drawable.ic_baseline_autorenew_24 -> Icons.Default.Loop
                        R.drawable.ic_baseline_color_lens_24 -> Icons.Default.Palette
                        else -> Icons.Default.MenuBook
                    },
                    contentDescription = null,
                    tint = if (isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ach.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = ach.desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { if (isCompleted) 1f else progressPercent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// Stats Holder Data Class
data class StatsData(
    val totalHours: Long,
    val currentStreak: Int,
    val bestStreak: Int,
    val totalChapters: Int,
    val customizations: Int,
    val todayMinutes: Long,
    val weekMinutes: Long,
    val monthMinutes: Long,
    val currentLevel: Long,
    val progressHours: Long,
    val progressPercentage: Float,
    val currentRank: String,
    val dailyGoal: Int,
    val weeklyGoal: Int,
    val dailyProgress: Float,
    val weeklyProgress: Float,
    val weekHeights: List<Long>,
    val weekLabels: List<String>
)

// Helper to construct Roman Numerals
private fun toRoman(number: Int): String {
    if (number <= 0) return ""
    val romanValues = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
    val romanSymbols = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
    
    var n = number
    val res = StringBuilder()
    for (i in romanValues.indices) {
        while (n >= romanValues[i]) {
            res.append(romanSymbols[i])
            n -= romanValues[i]
        }
    }
    return res.toString()
}

// Achievements list factory
data class Achievement(
    val title: String,
    val desc: String,
    val icon: Int,
    val currentProgress: Int,
    val maxProgress: Int
)

private fun getAchievementsList(stats: StatsData): List<Achievement> {
    val streakTiers = listOf(3, 7, 30, 100, 365)
    val chapterTiers = listOf(10, 50, 100, 500, 1000, 5000)
    val customTiers = listOf(5, 15, 50, 100)

    fun getTier(valIn: Int, tiers: List<Int>): Pair<Int, Int> {
        val tierIndex = tiers.indexOfFirst { valIn < it }
        return if (tierIndex == -1) {
            tiers.last() to tiers.size
        } else {
            tiers[tierIndex] to tierIndex
        }
    }

    val (nextStreak, streakLvl) = getTier(stats.currentStreak, streakTiers)
    val (nextChapter, chapterLvl) = getTier(stats.totalChapters, chapterTiers)
    val (nextCustom, customLvl) = getTier(stats.customizations, customTiers)

    return listOf(
        Achievement(
            title = if (streakLvl > 0) "Habit Former ${toRoman(streakLvl + 1)}" else "Early Bird",
            desc = "Maintain a $nextStreak-day streak",
            icon = R.drawable.ic_baseline_autorenew_24,
            currentProgress = stats.currentStreak,
            maxProgress = nextStreak
        ),
        Achievement(
            title = "Page Turner ${toRoman(chapterLvl + 1)}",
            desc = "Read $nextChapter chapters",
            icon = R.drawable.ic_baseline_menu_book_24,
            currentProgress = stats.totalChapters,
            maxProgress = nextChapter
        ),
        Achievement(
            title = "Customizer ${toRoman(customLvl + 1)}",
            desc = "Personalize fonts/themes $nextCustom times",
            icon = R.drawable.ic_baseline_color_lens_24,
            currentProgress = stats.customizations,
            maxProgress = nextCustom
        )
    )
}

// Statistics Sharing Intent
private fun shareStatistics(context: Context, stats: StatsData) {
    val date = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date())
    val text = """
        📖 *My Reading Stats - $date* 📖

        🏆 Reader Level: ${stats.currentRank} (Level ${stats.currentLevel})
        🔥 Streak: ${stats.currentStreak} days
        📖 Chapters Read: ${stats.totalChapters}
        🕒 Time Spent: ${stats.todayMinutes / 60}h ${stats.todayMinutes % 60}m
        
        🎯 Goals:
        Daily: ${stats.todayMinutes}/${stats.dailyGoal} min
        Weekly: ${stats.weekMinutes}/${stats.weeklyGoal} min

        ✨ Reading with *NeoQN* - https://github.com/Shadyteal2/QuickNovel-Enhanced
    """.trimIndent()

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, null).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(shareIntent)
}
