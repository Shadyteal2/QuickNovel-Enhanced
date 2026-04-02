package com.lagradost.quicknovel.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.databinding.FragmentReadingStatsBinding
import com.lagradost.quicknovel.databinding.ItemAchievementBinding
import com.lagradost.quicknovel.util.UsageStatsManager
import androidx.core.view.isVisible
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar

class ReadingStatsFragment : Fragment() {
    private var _binding: FragmentReadingStatsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReadingStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Fix status bar padding covers
        activity?.fixPaddingStatusbar(binding.statsAppbar)

        binding.statsToolbar.setNavigationOnClickListener {
            activity?.onBackPressed()
        }
        com.lagradost.quicknovel.util.GlassHeaderHelper.applyGlassHeader(
            binding.statsToolbar,
            binding.statsScrollview
        )

        // Setup Toolbar Menu for Share
        binding.statsToolbar.inflateMenu(R.menu.stats_menu)
        binding.statsToolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_share_stats) {
                shareStatistics()
                true
            } else false
        }

        // Click on Level Title for ranks info
        binding.tvLevelTitle.setOnClickListener {
            val ranks = listOf("Novice (Level 1)", "Apprentice (Level 2)", "Scholar (Level 3)", "Sage (Level 4)", "Legend (Level 5+)")
            AlertDialog.Builder(requireContext(), R.style.AlertDialogCustom)
                .setTitle("Reader Ranks")
                .setItems(ranks.toTypedArray(), null)
                .show()
        }

        // Click handlers for Goals
        binding.llDailyGoal.setOnClickListener {
            showSetGoalDialog("Daily")
        }
        binding.llWeeklyGoal.setOnClickListener {
            showSetGoalDialog("Weekly")
        }

        loadStatistics()
    }

    private fun loadStatistics() {
        val context = context ?: return

        // 1. Get Totals
        val totalMs = context.getKey<Long>("TOTAL_READING_TIME", 0L) ?: 0L
        val currentStreak = context.getKey<Int>("CURRENT_STREAK", 0) ?: 0

        val totalMinutes = totalMs / (1000 * 60)
        val totalHours = totalMinutes / 60

        // 2. Level System Ratio
        val hoursPerLevel = 5
        val currentLevel = (totalHours / hoursPerLevel) + 1
        val progressHours = totalHours % hoursPerLevel
        val progressPercentage = ((progressHours.toFloat() / hoursPerLevel) * 100).toInt()

        // Level Title Lists
        val levelTitles = listOf("Novice", "Apprentice", "Scholar", "Sage", "Legend")
        val titleIdx = (currentLevel - 1).toInt().coerceIn(0, levelTitles.size - 1)

        // Bind Profile
        binding.tvLevelTitle.text = levelTitles[titleIdx]
        binding.tvLevelSubtitle.text = "Level $currentLevel Reader"
        binding.pbLevelProgress.progress = progressPercentage
        binding.tvLevelProgressText.text = "${progressHours}h read / ${hoursPerLevel}h to next"

        // Bind Pills
        val totalChapters = context.getKey<Int>("TOTAL_CHAPTERS_READ", 0) ?: 0
        binding.tvPillStreak.text = "🔥 $currentStreak"
        binding.tvPillChapters.text = "📖 $totalChapters"
        binding.tvPillHours.text = "🕒 ${totalHours}h"

        // Streak Card
        val bestStreak = context.getKey<Int>("BEST_STREAK", 0) ?: 0
        binding.tvStreakHeader.text = "$currentStreak day streak"
        binding.tvBestStreak.text = "Best: $bestStreak days"

        // 3. Goals Logic from DataStore
        val dailyGoal = context.getKey<Int>("DAILY_GOAL_MIN", 30) ?: 30
        val weeklyGoal = context.getKey<Int>("WEEKLY_GOAL_MIN", 180) ?: 180

        binding.pbDailyGoal.progress = (totalMinutes.toFloat().coerceIn(0f, dailyGoal.toFloat()) / dailyGoal.toFloat() * 100f).toInt()
        binding.tvDailyGoalSub.text = "$totalMinutes / $dailyGoal min"
        
        binding.pbWeeklyGoal.progress = (totalMinutes.toFloat().coerceIn(0f, weeklyGoal.toFloat()) / weeklyGoal.toFloat() * 100f).toInt()
        binding.tvWeeklyGoalSub.text = "$totalMinutes / $weeklyGoal min"

        // Times Grid
        binding.tvTimeToday.text = "${totalMinutes}m"
        binding.tvTimeWeek.text = "${totalMinutes}m"
        binding.tvTimeMonth.text = "${totalMinutes}m"

        setupBarChart(totalMinutes)
        setupAchievements()
    }

    private fun showSetGoalDialog(type: String) {
        val context = context ?: return
        val key = if (type == "Daily") "DAILY_GOAL_MIN" else "WEEKLY_GOAL_MIN"
        val defaultVal = if (type == "Daily") 30 else 180

        val builder = AlertDialog.Builder(context, R.style.AlertDialogCustom)
        builder.setTitle("Set $type Goal")
        
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "Value in minutes (e.g., 30)"
            setText(context.getKey<Int>(key, defaultVal)?.toString())
        }
        builder.setView(input)

        builder.setPositiveButton("Save") { _, _ ->
            val value = input.text.toString().toIntOrNull() ?: defaultVal
            context.setKey(key, value)
            loadStatistics() // reload
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    data class Achievement(
        val title: String,
        val desc: String,
        val icon: Int,
        val currentProgress: Int,
        val maxProgress: Int,
        val isUnlocked: Boolean = false, // Handled logic during creation
        val level: String? = null
    )

    private fun setupAchievements() {
        val context = context ?: return
        binding.layoutAchievements.removeAllViews()

        val streak = context.getKey<Int>("CURRENT_STREAK", 0) ?: 0
        val bestStreak = context.getKey<Int>("BEST_STREAK", 0) ?: 0
        val chapters = context.getKey<Int>("TOTAL_CHAPTERS_READ", 0) ?: 0
        val customizations = context.getKey<Int>("CUSTOMIZATION_COUNT", 0) ?: 0

        // Helper to find the next tier for a given value
        fun getTier(valIn: Int, tiers: List<Int>): Pair<Int, Int> {
            val tierIndex = tiers.indexOfFirst { valIn < it }
            return if (tierIndex == -1) {
                tiers.last() to tiers.size // All completed
            } else {
                tiers[tierIndex] to tierIndex // Next target tier and the count of completed tiers before it
            }
        }

        fun toRoman(number: Int): String {
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

        val streakTiers = listOf(3, 7, 30, 100, 365)
        val chapterTiers = listOf(10, 50, 100, 500, 1000, 5000)
        val customTiers = listOf(5, 15, 50, 100)

        val (nextStreak, streakLvl) = getTier(streak, streakTiers)
        val (nextChapter, chapterLvl) = getTier(chapters, chapterTiers)
        val (nextCustom, customLvl) = getTier(customizations, customTiers)

        val achievements = mutableListOf<Achievement>()
        
        // Streak Achievement
        achievements.add(Achievement(
            title = if (streakLvl > 0) "Habit Former ${toRoman(streakLvl + 1)}" else "Early Bird",
            desc = "Maintain a $nextStreak-day streak",
            icon = R.drawable.ic_baseline_autorenew_24,
            currentProgress = streak,
            maxProgress = nextStreak
        ))

        // Volume Achievement
        achievements.add(Achievement(
            title = "Page Turner ${toRoman(chapterLvl + 1)}",
            desc = "Read $nextChapter chapters",
            icon = R.drawable.ic_baseline_menu_book_24,
            currentProgress = chapters,
            maxProgress = nextChapter
        ))

        // Customizer Achievement
        achievements.add(Achievement(
            title = "Customizer ${toRoman(customLvl + 1)}",
            desc = "Personalize fonts/themes $nextCustom times",
            icon = R.drawable.ic_baseline_color_lens_24,
            currentProgress = customizations,
            maxProgress = nextCustom
        ))

        for (ach in achievements) {
            val itemBinding = ItemAchievementBinding.inflate(layoutInflater, binding.layoutAchievements, false)
            itemBinding.achievementTitle.text = ach.title
            itemBinding.achievementDesc.text = ach.desc
            itemBinding.achievementIcon.setImageResource(ach.icon)
            
            val isCompleted = ach.currentProgress >= ach.maxProgress
            val progress = (ach.currentProgress.toFloat() / ach.maxProgress.toFloat() * 100f).toInt().coerceIn(0, 100)
            itemBinding.achievementProgress.progress = if (isCompleted && ach.maxProgress != 0) 100 else progress
            
            if (isCompleted) {
                itemBinding.achievementIcon.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.colorPrimary, null))
                itemBinding.achievementLocked.setImageResource(R.drawable.ic_baseline_check_24)
                itemBinding.achievementLocked.isVisible = true
                itemBinding.achievementLocked.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.colorPrimary, null))
            } else {
                itemBinding.achievementIcon.imageTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.grayTextColor, null))
                itemBinding.achievementLocked.isVisible = false
            }

            binding.layoutAchievements.addView(itemBinding.root)
        }
    }

    private fun shareStatistics() {
        val context = context ?: return
        val totalMs = context.getKey<Long>("TOTAL_READING_TIME", 0L) ?: 0L
        val currentStreak = context.getKey<Int>("CURRENT_STREAK", 0) ?: 0
        val totalMinutes = totalMs / (1000 * 60)
        
        val currentLevel = (totalMinutes / 60 / 5) + 1
        val levelTitles = listOf("Novice", "Apprentice", "Scholar", "Sage", "Legend")
        val title = levelTitles[((currentLevel - 1).toInt().coerceIn(0, 4))]
        
        val dailyGoal = context.getKey<Int>("DAILY_GOAL_MIN", 30) ?: 30
        val weeklyGoal = context.getKey<Int>("WEEKLY_GOAL_MIN", 180) ?: 180

        val totalChapters = context.getKey<Int>("TOTAL_CHAPTERS_READ", 0) ?: 0
        val date = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())

        val text = """
            📖 *My Reading Stats - $date* 📖

            🏆 Reader Level: $title (Level $currentLevel)
            🔥 Streak: $currentStreak days
            📖 Chapters Read: $totalChapters
            🕒 Time Spent: ${totalMinutes / 60}h ${totalMinutes % 60}m
            
            🎯 Goals:
            Daily: $totalMinutes/$dailyGoal min
            Weekly: $totalMinutes/$weeklyGoal min

            ✨ Reading with **NeoQN** - https://github.com/Shadyteal2/QuickNovel-Enhanced
        """.trimIndent()

        val sendIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            putExtra(android.content.Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        val shareIntent = android.content.Intent.createChooser(sendIntent, null)
        startActivity(shareIntent)
    }

    private fun setupBarChart(todayMinutes: Long) {
        val chart = binding.layoutBarChart
        chart.removeAllViews()

        val days = listOf("M", "T", "W", "T", "F", "S", "S")
        val heights = listOf(10, 20, todayMinutes.toInt().coerceIn(10, 80), 30, 15, 25, 40) // Mock values for weight effects

        for (i in days.indices) {
            val barContainer = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }

            // The visual bar
            val bar = FrameLayout(context!!).apply {
                val params = LinearLayout.LayoutParams(
                    (12 * resources.displayMetrics.density).toInt(),
                    0,
                    heights[i].toFloat() // weight determines height
                )
                layoutParams = params
                background = resources.getDrawable(R.drawable.search_background, null)
                
                if (i == 2) { 
                    backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.colorPrimary, null))
                } else {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.grayTextColor, null))
                    alpha = 0.5f
                }
            }

            // Top spacer weight layout holder
            val spacer = FrameLayout(context!!).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 100f - heights[i].toFloat())
            }

            val text = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (4 * resources.displayMetrics.density).toInt()
                }
                this.text = days[i]
                textSize = 10f
                setTextColor(resources.getColor(R.color.grayTextColor, null))
            }

            barContainer.addView(spacer)
            barContainer.addView(bar)
            barContainer.addView(text)
            chart.addView(barContainer)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
