package com.lagradost.quicknovel

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.speech.tts.Voice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.ScaleGestureDetector
import android.view.GestureDetector
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.net.Uri
import androidx.preference.PreferenceManager
import com.facebook.shimmer.ShimmerFrameLayout
import com.lagradost.quicknovel.ui.roundedbg.RoundedBgTextView
import com.lagradost.quicknovel.util.UsageStatsManager
import com.lagradost.quicknovel.util.DrawerHelper
import com.lagradost.quicknovel.util.GrainDrawableCache
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.doOnEnd
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.children
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import com.lagradost.quicknovel.CommonActivity.showToast
import com.lagradost.quicknovel.DataStore.getKey
import com.lagradost.quicknovel.DataStore.setKey
import com.lagradost.quicknovel.TTSNotifications.TTS_NOTIFICATION_ID
import com.lagradost.quicknovel.databinding.ColorRoundCheckmarkBinding
import com.lagradost.quicknovel.databinding.DialogMlDownloadBinding
import com.lagradost.quicknovel.databinding.ReadBottomSettingsBinding
import com.lagradost.quicknovel.util.UIHelper.colorFromAttribute
import com.lagradost.quicknovel.util.UIHelper.fixPaddingStatusbar
import com.lagradost.quicknovel.util.UIHelper.getStatusBarHeight
import com.lagradost.quicknovel.util.UIHelper.popupMenu
import com.lagradost.quicknovel.util.getSafeInt
import com.lagradost.quicknovel.util.getSafeFloat
import com.lagradost.quicknovel.util.UIHelper.systemFonts
import com.lagradost.quicknovel.databinding.ReadMainBinding
import com.lagradost.quicknovel.databinding.SingleOverscrollChapterBinding
import com.lagradost.quicknovel.util.bindBackgroundEffects
import com.lagradost.quicknovel.util.getBackgroundEffectState
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import android.widget.Toast
import com.lagradost.quicknovel.ui.CONFIG_COLOR
import com.lagradost.quicknovel.ui.CONFIG_FONT
import com.lagradost.quicknovel.ui.CONFIG_FONT_BOLD
import com.lagradost.quicknovel.databinding.ReadThemePickerBinding
import com.lagradost.quicknovel.ui.OrientationType
import com.lagradost.quicknovel.ui.ReadingType
import com.lagradost.quicknovel.ui.ScrollIndex
import com.lagradost.quicknovel.ui.ScrollVisibilityIndex
import com.lagradost.quicknovel.ui.ScrollVisibilityItem
import com.lagradost.quicknovel.ui.TextAdapter
import com.lagradost.quicknovel.ui.TextConfig
import com.lagradost.quicknovel.ui.TextVisualLine
import com.lagradost.quicknovel.ui.DictionaryBottomSheet
import com.lagradost.quicknovel.ui.TranslationBottomSheet
import com.lagradost.quicknovel.ui.reader.PaginatedReaderView
import com.lagradost.quicknovel.ui.reader.ReadingTimerOverlay
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.getValue
import com.lagradost.quicknovel.ui.ViewHolderState
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import com.lagradost.quicknovel.util.applyGlassStyle
import com.lagradost.quicknovel.util.divCeil
import com.lagradost.quicknovel.util.toPx
import com.lagradost.quicknovel.util.AuraTransparencyHelper
import java.lang.Integer.max
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.properties.Delegates


class ReadActivity2 : AppCompatActivity(), ColorPickerDialogListener {
    companion object {
        private var _readActivity: WeakReference<ReadActivity2>? = null
        var readActivity
            get() = _readActivity?.get()
            private set(value) {
                _readActivity = WeakReference(value)
            }
    }

    private var batteryReceiver: BroadcastReceiver? = null
    private var autoScrollJob: Job? = null
    private var lastTouchTime: Long = 0L

    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, binding.readerContainer).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        fun lowerBottomNav(v: View) {
            v.translationY = 0f
            val params = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            val margin = params?.bottomMargin?.toFloat() ?: 0f
            ObjectAnimator.ofFloat(v, "translationY", v.height.toFloat() + margin).apply {
                duration = 200
                start()
            }.doOnEnd {
                v.isVisible = false
            }
        }

        lowerBottomNav(binding.readerBottomViewHolder)
        
        // Design Spell: Synchronize Progress Bar (Return to baseline in full-screen)
        ObjectAnimator.ofFloat(binding.readerProgressContainer, "translationY", 0f).apply {
            duration = if (viewModel.premiumAnimations) 600L else 300L
            start()
        }

        // Pixel-Perfect Translation: Pull actual dynamic margin to ensure it clears the notch/status bar
        val params = binding.readToolbarHolder.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        val topMargin = params?.topMargin?.toFloat() ?: (12 * resources.displayMetrics.density)
        binding.readToolbarHolder.translationY = 0f
        ObjectAnimator.ofFloat(
            binding.readToolbarHolder,
            "translationY",
            -(binding.readToolbarHolder.height.toFloat() + topMargin)
        ).apply {
            duration = 200
            start()
        }.doOnEnd {
            binding.readToolbarHolder.isVisible = false
        }
    }

    private fun showSystemUI() {
        WindowInsetsControllerCompat(
            window,
            binding.readerContainer
        ).show(WindowInsetsCompat.Type.systemBars())

        binding.readToolbarHolder.isVisible = true

        val isPremium = viewModel.premiumAnimations
        val entranceDuration = if (isPremium) 600L else 300L

        fun higherBottomNavView(v: View) {
            v.isVisible = true
            v.post {
                val params = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                val margin = params?.bottomMargin?.toFloat() ?: 0f
                v.translationY = v.height.toFloat() + margin
                ObjectAnimator.ofFloat(v, "translationY", 0f).apply {
                    duration = entranceDuration
                    if (isPremium) interpolator = android.view.animation.OvershootInterpolator(1.1f)
                    start()
                }

                // Design Spell: Synchronize Progress Bar (Move above settings bar)
                ObjectAnimator.ofFloat(binding.readerProgressContainer, "translationY", -(v.height.toFloat() + margin)).apply {
                    duration = entranceDuration
                    if (isPremium) interpolator = android.view.animation.OvershootInterpolator(1.1f)
                    start()
                }
            }
        }

        higherBottomNavView(binding.readerBottomViewHolder)

        // Pixel-Perfect Reset: Start from offset that clears the notch/status bar
        val params = binding.readToolbarHolder.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        val topMargin = params?.topMargin?.toFloat() ?: (12 * resources.displayMetrics.density)
        binding.readToolbarHolder.translationY = -(binding.readToolbarHolder.height.toFloat() + topMargin)

        ObjectAnimator.ofFloat(binding.readToolbarHolder, "translationY", 0f).apply {
            duration = entranceDuration
            if (isPremium) interpolator = android.view.animation.OvershootInterpolator(1.1f)
            start()
        }

        // Kinetic Stagger: Animate children of toolbar for "burst" entrance
        if (isPremium) {
            binding.readToolbar.children.forEachIndexed { index, child ->
                child.alpha = 0f
                child.translationX = -20f
                child.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(300)
                    .setStartDelay(100 + index * 30L)
                    .start()
            }
        }
    }

    lateinit var binding: ReadMainBinding
    val viewModel: ReadActivityViewModel by viewModels()

    private var _imageHolder: WeakReference<LinearLayout>? = null
    var imageHolder
        get() = _imageHolder?.get()
        set(value) {
            _imageHolder = WeakReference(value)
        }

    override fun onColorSelected(dialogId: Int, color: Int) {
        val activity = this ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        
        try {
            when (dialogId) {
                0 -> setBackgroundColor(color)
                1 -> setTextColor(color)
            }
        } catch (e: Exception) {
            com.lagradost.quicknovel.mvvm.logError(e)
        }
    }

    private var readingSessionStartTime: Long = 0L

    fun showDictionary(word: String) {
        DictionaryBottomSheet.newInstance(word, binding.readNormalLayout.id).show(supportFragmentManager, "dictionary")
    }

    fun showTranslation(text: String) {
        TranslationBottomSheet(text, binding.readNormalLayout.id).show(supportFragmentManager, "dictionary")
    }

    private fun setBackgroundColor(color: Int) {
        viewModel.backgroundColor = color
    }

    private fun updateGlobalBackground() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)
        val isEnabled = settingsManager.getBoolean(getString(R.string.reader_background_key), false)
        val themeColor = viewModel.backgroundColor

        binding.apply {
            // ─── No custom image background ───────────────────────────
            if (!isEnabled || imageUri.isNullOrBlank()) {
                readerBackgroundImage.isVisible = false
                readerBackgroundDim.isVisible = false
                readerBackgroundLightScrim.isVisible = false
                readerBackgroundVignette.isVisible = false

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    readerBackgroundImage.setRenderEffect(null)
                }
                readerBackgroundImage.colorFilter = null

                // Always paint every container with the theme color first.
                // Grain sits on top as a semi-transparent overlay — it does NOT
                // require the background to be transparent.
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(themeColor))
                root.setBackgroundColor(themeColor)
                readOverlay.setBackgroundColor(themeColor)
                readNormalLayout.setBackgroundColor(themeColor)
                readerLinContainer.setBackgroundColor(themeColor)
                realText.setBackgroundColor(themeColor)
                paginatedTextCompose.setBackgroundColor(themeColor)

                val grainStrength = viewModel.backgroundGrain
                if (grainStrength > 0) {
                    // Grain view is a sibling that sits above the colored views in Z-order.
                    // Its alpha encodes how strong the grain is (0.0 = invisible, 1.0 = full).
                    val normalizedAlpha = (grainStrength / 100f).coerceIn(0.05f, 1.0f)
                    val bitmap = GrainDrawableCache.getOrCreate(this@ReadActivity2, grainStrength)
                    val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap).apply {
                        tileModeX = android.graphics.Shader.TileMode.REPEAT
                        tileModeY = android.graphics.Shader.TileMode.REPEAT
                        // Use multiply blend if the grain bitmap is pre-coloured; otherwise OVERLAY
                        // is fine. Setting paint alpha here keeps the bitmap intact.
                    }
                    readerBackgroundGrain.background = drawable
                    readerBackgroundGrain.alpha = normalizedAlpha
                    readerBackgroundGrain.isVisible = true
                } else {
                    readerBackgroundGrain.isVisible = false
                }
                return@apply
            }

            // ─── Custom image background ───────────────────────────────
            // Containers are transparent so the image wallpaper shows through.
            root.setBackgroundColor(Color.TRANSPARENT)
            readOverlay.setBackgroundColor(Color.TRANSPARENT)
            bindBackgroundEffects(
                context = this@ReadActivity2,
                imageView = readerBackgroundImage,
                dimView = readerBackgroundDim,
                lightScrimView = readerBackgroundLightScrim,
                grainView = readerBackgroundGrain,
                vignetteView = readerBackgroundVignette,
                imageUri = imageUri,
                enabled = true,
                state = settingsManager.getBackgroundEffectState(this@ReadActivity2).copy(
                    grain = viewModel.backgroundGrain
                ),
                onError = { throwable ->
                    com.lagradost.quicknovel.mvvm.logError(throwable)
                    // SecurityException or load failure: fall back to solid color + grain
                    readerBackgroundImage.isVisible = false
                    readerBackgroundDim.isVisible = false
                    readerBackgroundLightScrim.isVisible = false
                    readerBackgroundVignette.isVisible = false

                    window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(themeColor))
                    root.setBackgroundColor(themeColor)
                    readOverlay.setBackgroundColor(themeColor)
                    readNormalLayout.setBackgroundColor(themeColor)
                    readerLinContainer.setBackgroundColor(themeColor)
                    realText.setBackgroundColor(themeColor)
                    paginatedTextCompose.setBackgroundColor(themeColor)

                    val grainStrength = viewModel.backgroundGrain
                    if (grainStrength > 0) {
                        val normalizedAlpha = (grainStrength / 100f).coerceIn(0.05f, 1.0f)
                        val bitmap = GrainDrawableCache.getOrCreate(this@ReadActivity2, grainStrength)
                        val drawable = android.graphics.drawable.BitmapDrawable(resources, bitmap).apply {
                            tileModeX = android.graphics.Shader.TileMode.REPEAT
                            tileModeY = android.graphics.Shader.TileMode.REPEAT
                        }
                        readerBackgroundGrain.background = drawable
                        readerBackgroundGrain.alpha = normalizedAlpha
                        readerBackgroundGrain.isVisible = true
                    } else {
                        readerBackgroundGrain.isVisible = false
                    }
                }
            )
            if (viewModel.isContrastCompromised) {
                readerBackgroundDim.alpha = maxOf(readerBackgroundDim.alpha, 0.4f)
                readerBackgroundDim.isVisible = true
            }
        }
    }

    private var haloAnimator: ObjectAnimator? = null

    private fun applyLuminescenceToView(view: View, enabled: Boolean, intensity: Int) {
        val target = if (view is RoundedBgTextView) view else view.findViewById<RoundedBgTextView>(R.id.real_text_item) ?: return
        
        if (!enabled) {
            target.setShadowLayer(0f, 0f, 0f, 0)
            return
        }

        val glowColorBase = Color.parseColor("#FFE8B5") // Amber Warm

        val blurRadius = (intensity / 100f) * 15f
        val alphaFactor = (intensity / 100f) * 0.8f

        val glowColor = Color.argb(
            (255 * alphaFactor).toInt().coerceIn(0, 255),
            Color.red(glowColorBase),
            Color.green(glowColorBase),
            Color.blue(glowColorBase)
        )
        target.setShadowLayer(blurRadius, 0f, 0f, glowColor)
    }

    private fun applyTextLuminescence(enabled: Boolean, intensity: Int) {
        binding.realText.children.forEach { view ->
            applyLuminescenceToView(view, enabled, intensity)
        }
    }

    private fun updateLuminescentEffects() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val performanceMode = settingsManager.getBoolean("performance_mode_enabled", false)
        val lEnabled = !performanceMode && viewModel.luminescentReader
        val lIntensity = (viewModel.luminescentIntensity * 100).toInt().coerceIn(0, 100)

        binding.readerHalo.apply {
            if (lEnabled) {
                visibility = android.view.View.VISIBLE
                val baseAlpha = (lIntensity / 100f) * 0.5f

                val glowColorBase = Color.parseColor("#FFE8B5") // Amber Warm

                (background as? android.graphics.drawable.GradientDrawable)?.let { gd ->
                    val glowColor = Color.argb(
                        (baseAlpha * 255).toInt().coerceIn(0, 255),
                        Color.red(glowColorBase),
                        Color.green(glowColorBase),
                        Color.blue(glowColorBase)
                    )
                    gd.colors = intArrayOf(Color.TRANSPARENT, glowColor)
                }

                if (haloAnimator == null) {
                    haloAnimator = ObjectAnimator.ofFloat(this, "alpha", 0.4f, 1.0f).apply {
                        duration = 5000
                        repeatMode = ObjectAnimator.REVERSE
                        repeatCount = ObjectAnimator.INFINITE
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                }
            } else {
                visibility = android.view.View.GONE
                haloAnimator?.cancel()
                haloAnimator = null
            }
        }

        applyTextLuminescence(lEnabled, lIntensity)
    }



    private fun setTextColor(color: Int) {
        viewModel.textColor = color
    }

    override fun onDialogDismissed(dialog: Int) {
        updateImages()
    }

    private fun updateImages() {
        val bgColors = resources.getIntArray(R.array.readerBgColors)
        val textColors = resources.getIntArray(R.array.readerTextColors)
        val themeNames = resources.getStringArray(R.array.reader_theme_names)
        val color = viewModel.backgroundColor
        val colorPrimary = colorFromAttribute(R.attr.colorPrimary)
        val colorPrim = ColorStateList.valueOf(colorPrimary)
        val colorTrans = ColorStateList.valueOf(Color.TRANSPARENT)
        var foundCurrentColor = false
        val fullAlpha = 200
        val fadedAlpha = 50

        for ((index, imgHolder) in imageHolder?.children?.withIndex() ?: return) {
            val img = imgHolder.findViewById<ImageView>(R.id.image1) ?: return

            if (index < themeNames.size) {
                img.contentDescription = getString(R.string.a11y_theme_format, themeNames[index])
            }

            if (index == bgColors.size) { // CUSTOM COLOR
                img.contentDescription = getString(R.string.a11y_theme_format, "Custom")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    img.foregroundTintList = colorPrim
                    img.foreground = ContextCompat.getDrawable(
                        this,
                        if (foundCurrentColor) R.drawable.ic_baseline_add_24 else R.drawable.ic_baseline_check_24
                    )
                }
                img.imageAlpha = if (foundCurrentColor) fadedAlpha else fullAlpha
                img.backgroundTintList =
                    ColorStateList.valueOf(if (foundCurrentColor) Color.parseColor("#161616") else color)
                continue
            }

            if ((color == bgColors[index] && viewModel.textColor == textColors[index])) {
                foundCurrentColor = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    img.foregroundTintList = colorPrim
                }
                img.imageAlpha = fullAlpha
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    img.foregroundTintList = colorTrans
                }
                img.imageAlpha = fadedAlpha
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.scrollWithVolume) {
            val keyCode = event.keyCode
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (viewModel.bottomVisibility.isInitialized && viewModel.bottomVisibility.value == true) {
                    return super.dispatchKeyEvent(event)
                }
                if (event.action == KeyEvent.ACTION_DOWN) {
                    onKeyDown(keyCode, event)
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            kill()
            return true
        }
        if ((keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP)) return false

        viewModel.onUserInteraction()

        // if we have the bottom bar up then we ignore the override functionality
        if (viewModel.bottomVisibility.isInitialized && viewModel.bottomVisibility.value == true) return false

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.forwardsTTS()
                    return true
                } else if (viewModel.scrollWithVolume) {
                    val bottomY = getBottomY()
                    val lines = getAllLines()
                    val line = lines.firstOrNull {
                        it.bottom >= bottomY
                    } ?: lines.lastOrNull() ?: return true
                    binding.realText.scrollBy(0, line.top - getTopY())

                    return true
                }
            }

            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.backwardsTTS()
                    return true
                } else if (viewModel.scrollWithVolume) {
                    binding.realText.scrollBy(0, getTopY() - getBottomY())
                    binding.realText.post {
                        val lines = getAllLines()
                        val topY = getTopY()
                        val line = lines.firstOrNull {
                            it.top >= topY
                        } ?: return@post
                        binding.realText.scrollBy(0, line.top - getTopY())
                    }

                    return true
                }
            }
        }

        return false
    }

    private fun kill() {
        with(NotificationManagerCompat.from(this)) { // KILLS NOTIFICATION
            cancel(TTS_NOTIFICATION_ID)
        }
        finish()
    }

    private fun registerBattery() {
        val receiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctxt: Context?, intent: Intent) {
                val batteryPct: Float = run {
                    val level: Int = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale: Int = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    level * 100 / scale.toFloat()
                }
                binding.readBattery.text =
                    getString(R.string.battery_format).format(batteryPct.toInt())
            }
        }
        batteryReceiver = receiver
        this.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    fun parseAction(input: TTSHelper.TTSActionType): Boolean {
        return viewModel.parseAction(input)
    }

    private val selectFontLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            saveCustomFont(uri)
        }
    }

    private lateinit var textAdapter: TextAdapter
    private lateinit var textLayoutManager: LinearLayoutManager

    private fun transformIndexToScrollVisibilityItem(adapterPosition: Int): ScrollVisibilityItem {
        return ScrollVisibilityItem(
            adapterPosition = adapterPosition,
            viewHolder = binding.realText.findViewHolderForAdapterPosition(adapterPosition),
        )
    }

    private fun getTopY(): Int {
        val outLocation = IntArray(2)
        binding.readTopItem.getLocationInWindow(outLocation)
        val (_, topY) = outLocation
        return topY + binding.realText.paddingTop
    }

    private fun getBottomY(): Int {
        val outLocation = IntArray(2)
        binding.readBottomItem.getLocationInWindow(outLocation)
        val (_, bottomY) = outLocation
        return bottomY - max(
            binding.realText.paddingBottom,
            if (viewModel.showTime || viewModel.showBattery) binding.readOverlay.height else 0
        )
    }

    /**

    ________________
    [ hello ]
    -- screen cut --
    [ world ]
    [ ! ]
    [ From kotlin ]
    ________________

    here the first index of "world" would be stored as it is the first whole line visible,
    while "hello" would be stored as the first invisible line, this is used to scroll the exact char
    you are on. so while rotating you would rotate to the first line that contains "world"

    This is also used for TTS because TTS *must* start at the first visible whole sentence,
    so in this case it would start at "From kotlin" because hello is not visible.
     */

    private fun getAllLines(): ArrayList<TextVisualLine> {
        val lines: ArrayList<TextVisualLine> = arrayListOf()

        for (i in textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
            lines.addAll(textAdapter.getLines(transformIndexToScrollVisibilityItem(i)))
        }
        return lines
    }

    private fun postLines(lines: ArrayList<TextVisualLine>) {
        if (lines.isEmpty()) {
            return
        }

        val topY = getTopY()
        val bottomY = getBottomY()

        viewModel.onScroll(
            ScrollVisibilityIndex(
                firstInMemory = lines.first(),
                lastInMemory = lines.last(),
                firstFullyVisible = lines.firstOrNull {
                    it.top >= topY
                },
                firstFullyVisibleUnderLine = lines.firstOrNull {
                    it.top >= topBarHeight
                },
                lastHalfVisible = lines.firstOrNull {
                    it.bottom >= bottomY
                },
                firstVisible = lines.firstOrNull {
                    it.bottom > topY
                }
            )
        )
    }

    fun onScroll() {
        postLines(getAllLines())
        updateReadingProgress()
    }

    /** Updates the chapter reading progress fill bar (0→1 via scaleX). Zero alloc per frame. */
    private fun updateReadingProgress() {
        val fill = binding.readerReadingProgressFill
        fill.pivotX = 0f
        
        val currentIdx = viewModel.currentIndex
        val items = textAdapter.immutableCurrentList
        if (items.isEmpty()) {
            fill.scaleX = 0f
            return
        }

        // Find the range of items belonging to the current chapter
        var firstPos = -1
        var lastPos = -1
        for (i in items.indices) {
            val item = items[i]
            if (item.index == currentIdx) {
                if (firstPos == -1) firstPos = i
                lastPos = i
            } else if (firstPos != -1) {
                // We've moved past the current chapter
                break
            }
        }

        if (firstPos == -1) {
            fill.scaleX = 0f
            return
        }
        val chapterItemCount = lastPos - firstPos + 1
        val lm = binding.realText.layoutManager as? LinearLayoutManager
        val firstVisible = lm?.findFirstVisibleItemPosition() ?: 0
        val lastVisible = lm?.findLastVisibleItemPosition() ?: firstVisible
        
        // Improved Progress Logic:
        // Calculation: (Current first visible - Chapter start) / (Max possible first visible - Chapter start)
        // This ensures progress reaches 100% when the user reaches the end of the scroll for this chapter.
        val visibleCount = (lastVisible - firstVisible).coerceAtLeast(0)
        val maxFirstVisible = (lastPos - visibleCount).coerceAtLeast(firstPos)
        val range = (maxFirstVisible - firstPos).coerceAtLeast(1)
        val progress = ((firstVisible - firstPos).toFloat() / range).coerceIn(0f, 1f)
        
        fill.scaleX = progress
        


        updateReaderInkFlow(progress)
    }

    private fun updateReaderInkFlow(progress: Float) {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val isPremium = settingsManager.getBoolean(com.lagradost.quicknovel.ui.theme.VibePrefs.PREMIUM_VISUALS_ENABLED, false)
        val isInkFlow = settingsManager.getBoolean(com.lagradost.quicknovel.ui.theme.VibePrefs.READER_INK_FLOW, false)
        
        val isAmoled = viewModel.backgroundColor == Color.BLACK
        val overlay = binding.readerInkFlowOverlay
        if (!isPremium || !isInkFlow || isAmoled) {
            overlay.visibility = View.GONE
            return
        }
        
        overlay.visibility = View.VISIBLE
        val clamped = progress.coerceIn(0f, 1f)
        
        // Cool top colors (semi-transparent)
        val startCool = intArrayOf(0x08, 0x10, 0x3A, 0x55)
        val endCool = intArrayOf(0x10, 0x08, 0x3A, 0x66)
        
        // Warm bottom colors
        val startWarm = intArrayOf(0x3A, 0x10, 0x08, 0x55)
        val endWarm = intArrayOf(0x3A, 0x20, 0x08, 0x66)
        
        // Interpolated argb colors
        val topR = (startCool[0] + (startWarm[0] - startCool[0]) * clamped).toInt()
        val topG = (startCool[1] + (startWarm[1] - startCool[1]) * clamped).toInt()
        val topB = (startCool[2] + (startWarm[2] - startCool[2]) * clamped).toInt()
        val topA = (startCool[3] + (startWarm[3] - startCool[3]) * clamped).toInt()
        
        val bottomR = (endCool[0] + (endWarm[0] - endCool[0]) * clamped).toInt()
        val bottomG = (endCool[1] + (endWarm[1] - endCool[1]) * clamped).toInt()
        val bottomB = (endCool[2] + (endWarm[2] - endCool[2]) * clamped).toInt()
        val bottomA = (endCool[3] + (endWarm[3] - endCool[3]) * clamped).toInt()
        
        val topColor = Color.argb(topA, topR, topG, topB)
        val bottomColor = Color.argb(bottomA, bottomR, bottomG, bottomB)
        
        val gd = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor)
        )
        overlay.background = gd
    }

    private var cachedChapter: List<SpanDisplay> = emptyList()
    private fun scrollToDesired() {
        val desired: ScrollIndex = viewModel.desiredIndex ?: return
        val chapterCopy = cachedChapter
        
        ioSafe {
            val adapterPosition = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                var pos = -1
                if (desired.innerIndex == 0 && desired.char > 0) {
                    pos = chapterCopy.indexOfFirst { display ->
                        display.index == desired.index && display is TextSpan &&
                                display.start <= desired.char && desired.char <= display.end
                    }
                }
                if (pos == -1) {
                    pos = chapterCopy.indexOfFirst { display ->
                        display.index == desired.index && display.innerIndex == desired.innerIndex
                    }
                }
                pos
            }
            
            if (adapterPosition == -1) return@ioSafe

            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                textLayoutManager.scrollToPositionWithOffset(adapterPosition, 1)
                binding.realText.post {
                    binding.realText.post {
                        viewModel.hasPerformedInitialSeek = true
                    }
                }

                if (pendingFlingDirection != 0) {
                    val dir = pendingFlingDirection
                    pendingFlingDirection = 0
                    binding.realText.post {
                        if (dir == 1) {
                            binding.realText.fling(0, 3000)
                        } else {
                            binding.realText.fling(0, -3000)
                        }
                    }
                }

                val targetInnerIndex = if (adapterPosition != -1) {
                    chapterCopy.getOrNull(adapterPosition)?.innerIndex ?: desired.innerIndex
                } else {
                    desired.innerIndex
                }

                // don't inner-seek if zero because that is chapter break
                if (targetInnerIndex == 0 && desired.char == 0) return@withContext

                var sought = false
                fun performSeek() {
                    if (sought) return
                    val lines = getAllLines()
                    if (lines.isNotEmpty()) {
                        sought = true
                        postLines(lines)
                        lines.firstOrNull { line ->
                            line.index == desired.index && line.innerIndex == desired.innerIndex && line.endChar >= desired.char
                        }?.let { line ->
                            binding.realText.scrollBy(0, line.top - getTopY())
                        }
                    }
                }

                binding.realText.post {
                    val lines = getAllLines()
                    if (lines.isNotEmpty()) {
                        performSeek()
                    } else {
                        binding.realText.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                            override fun onLayoutChange(
                                v: View?,
                                left: Int,
                                top: Int,
                                right: Int,
                                bottom: Int,
                                oldLeft: Int,
                                oldTop: Int,
                                oldRight: Int,
                                oldBottom: Int
                            ) {
                                val currentLines = getAllLines()
                                if (currentLines.isNotEmpty()) {
                                    binding.realText.removeOnLayoutChangeListener(this)
                                    binding.realText.post {
                                        performSeek()
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }

    }

    private fun View.fixLine(offset: Int) {
        // this.setPadding(0, 200, 0, 0)
        val layoutParams =
            this.layoutParams as FrameLayout.LayoutParams// FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,offset)
        layoutParams.setMargins(0, offset, 0, 0)
        this.layoutParams = layoutParams
    }

    var lockTop: Int? = null
    var lockBottom: Int? = null
    var currentScroll: Int = 0

    private fun updateTTSLine(line: TTSHelper.TTSLine?, depth: Int = 0) {
        // update the visual component
        /*println("LINE: ${line?.speakOutMsg} =>")
        line?.speakOutMsg?.codePoints()?.forEachOrdered {
            println(">>" + String(intArrayOf(it), 0, 1) + "|" + it.toString())
        }*/

        textAdapter.updateTTSLine(line)
        val first = textLayoutManager.findFirstVisibleItemPosition()
        val last = textLayoutManager.findLastVisibleItemPosition()
        textAdapter.notifyItemRangeChanged(first, last + 1 - first)
        /*for (position in textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
            val viewHolder = binding.realText.findViewHolderForAdapterPosition(position)
            if (viewHolder !is TextAdapter.TextAdapterHolder) continue
            viewHolder.updateTTSLine(line)
        }*/

        // update the lock area
        if (line == null || !viewModel.ttsLock) {
            lockTop = null
            lockBottom = null
            return
        }

        val lines = getAllLines()
        postLines(lines)

        val top = lines.firstOrNull { it.index == line.index && it.endChar > line.startChar }
        val bottom =
            lines.firstOrNull { it.index == line.index && it.startChar <= line.endChar && line.endChar <= it.endChar }

        if (top == null || bottom == null) {
            lockTop = null
            lockBottom = null

            // this should never happened as tts line must be valid
            val innerIndex = viewModel.innerCharToIndex(line.index, line.startChar) ?: return

            // scroll to the top of that line, first search the adapter
            val adapterPosition =
                cachedChapter.indexOfFirst { display -> display.index == line.index && display.innerIndex == innerIndex }

            // if we tts out of bounds somehow? we scroll to that and refresh everything
            if (adapterPosition == -1) {
                viewModel.scrollToDesired(
                    ScrollIndex(
                        index = line.index,
                        innerIndex = innerIndex,
                        line.startChar
                    )
                )
                return
            }

            if (depth < 3) {
                textLayoutManager.scrollToPositionWithOffset(adapterPosition, 1)
                textLayoutManager.postOnAnimation {
                    updateTTSLine(line, depth = depth + 1)
                }
            }

            return
        }

        val topScroll = top.top - getTopY()
        lockTop = currentScroll + topScroll
        val bottomScroll =
            bottom.bottom - getBottomY()
        lockBottom = currentScroll + bottomScroll

        // binding.tmpTtsStart.fixLine(top.top)
        //binding.tmpTtsEnd.fixLine(bottom.bottom)

        // we have reached the end, scroll to the top
        if (bottomScroll > 0) {
            binding.realText.scrollBy(0, topScroll)
        }
        // we have scrolled up while being on top
        else if (topScroll < 0) {
            binding.realText.scrollBy(0, topScroll)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // we save this just in case the user fucks it up somehow
        postDesired(binding.realText)
        super.onConfigurationChanged(newConfig)
    }

    private fun updateOtherTextConfig(config: TextConfig) {
        config.setArgs(binding.loadingText, CONFIG_FONT or CONFIG_COLOR)
        config.setArgs(binding.readBattery, CONFIG_FONT or CONFIG_COLOR or CONFIG_FONT_BOLD)
        config.setArgs(binding.readTimeClock, CONFIG_FONT or CONFIG_COLOR or CONFIG_FONT_BOLD)
        config.setArgs(binding.readLoadingProgressBar)
    }

    private fun updatePadding() {
        val h = viewModel.paddingHorizontal.toPx
        val v = viewModel.paddingVertical.toPx
        binding.realText.apply {
            if (paddingLeft == h && paddingRight == h && paddingBottom == v && paddingTop == v) return
            setPadding(
                h,
                v,
                h,
                v
            )
            scrollToDesired()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateTextAdapterConfig() {
        // this did not work so I just rebind everything, it does not happend often so idc
        textAdapter.notifyDataSetChanged()
        updateOtherTextConfig(textAdapter.config)
        /* binding.realText.apply {
             for (idx in 0..childCount) {//textLayoutManager.findFirstVisibleItemPosition()..textLayoutManager.findLastVisibleItemPosition()) {
                 val viewHolder = getChildViewHolder(getChildAt(idx) ?: continue) ?: continue
                 if (viewHolder !is TextAdapter.TextAdapterHolder) continue
                 viewHolder.setConfig(textAdapter.config)
             }
         }*/
    }

    private fun startAutoScrollLoop() {
        autoScrollJob?.cancel()
        if (viewModel.autoScroll != true) return

        autoScrollJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastTouchTime > 2500) {
                    val speed = viewModel.autoScrollSpeed
                    binding.realText.scrollBy(0, speed)
                }
                delay(16)
            }
        }
    }

    private var isPostDesiredPending = false
    private fun postDesired(view: View) {
        viewModel.hasPerformedInitialSeek = false
        if (isPostDesiredPending) return
        isPostDesiredPending = true
        val currentDesired = viewModel.desiredIndex
        view.post {
            isPostDesiredPending = false
            viewModel.desiredIndex = currentDesired
            scrollToDesired()
            updateTTSLine(viewModel.ttsLine.value)
        }
    }

    override fun onResume() {
        viewModel.resumedApp()
        super.onResume()
        readingSessionStartTime = android.os.SystemClock.elapsedRealtime()
        if (viewModel.autoScroll == true) {
            startAutoScrollLoop()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.init(intent, this)
    }

    override fun onPause() {
        viewModel.leftApp()
        super.onPause()
        autoScrollJob?.cancel()
        if (readingSessionStartTime != 0L) {
            val sessionTime = android.os.SystemClock.elapsedRealtime() - readingSessionStartTime
            // Safety cap: No reading session can be > 12 hours (43,200,000 ms)
            // This prevents massive outliers if the activity isn't paused correctly for days
            // or if the system clock jumps.
            if (sessionTime > 0) {
                val cappedSessionTime = minOf(sessionTime, 12 * 60 * 60 * 1000L)
                UsageStatsManager.addReadingTime(this, cappedSessionTime)
            }
            readingSessionStartTime = 0L
        }
    }

    /*private fun pendingPost() {
        binding.readToolbarHolder.post {
            val height = binding.readToolbarHolder.height
            // height cant be 0
            if(height == 0) {
                pendingPost()
                return@post
            }

            if(textAdapter.changeHeight(binding.readToolbarHolder.height + getStatusBarHeight())) {
                updateTextAdapterConfig()
            }
        }
    }*/
    private fun showFonts() {
        val currentFontName = viewModel.textFont ?: ""
        val sheet = com.lagradost.quicknovel.ui.reader.FontPickerBottomSheet.newInstance(
            currentFontName,
            binding.readNormalLayout.id
        )
        sheet.setListeners(
            onFontSelected = { fontFile ->
                viewModel.textFont = fontFile.file?.name ?: ""
            },
            onDeleteFont = { fontFile ->
                fontFile.file?.delete()
                showToast("Font deleted")
            },
            onAddCustomFont = {
                selectFontLauncher.launch("*/*")
            }
        )
        sheet.show(supportFragmentManager, "font_picker")
    }

    private fun saveCustomFont(uri: android.net.Uri) {
        try {
            val contentResolver = contentResolver
            var name = "custom_font_${System.currentTimeMillis()}.ttf"
            val cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            name = cursor.getString(index)
                        }
                    }
                } finally {
                    cursor.close()
                }
            }

            if (!name.endsWith(".ttf", ignoreCase = true) && !name.endsWith(".otf", ignoreCase = true)) {
                showToast("Please select a valid font (.ttf or .otf)")
                return
            }

            val tempFile = java.io.File(cacheDir, "temp_font_validate.ttf")
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            try {
                android.graphics.Typeface.createFromFile(tempFile.absolutePath)
                
                val folder = java.io.File(filesDir, "fonts")
                if (!folder.exists()) folder.mkdirs()
                val destFile = java.io.File(folder, name)
                tempFile.copyTo(destFile, overwrite = true)
                
                showToast("Font added: ${com.lagradost.quicknovel.util.UIHelper.parseFontFileName(name)}")
                viewModel.textFont = name
                binding.root.post { showFonts() }
            } catch (t: Throwable) {
                showToast("Invalid font format")
                tempFile.delete()
            }
            tempFile.delete()
        } catch (t: Throwable) {
            com.lagradost.quicknovel.mvvm.logError(t)
            showToast("Failed to save font")
        }
    }



    /*  private fun updateTimeText() {
          val string = if (viewModel.time12H) "hh:mm a" else "HH:mm"

          val currentTime: String = SimpleDateFormat(string, Locale.getDefault()).format(Date())

          binding.readTime.text = currentTime
          binding.readTime.postDelayed({ -> updateTimeText() }, 1000)
      }*/

    private var topBarHeight by Delegates.notNull<Int>()
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private lateinit var tapGestureDetector: GestureDetector
    private var startTwoFingerX = 0f
    private var startBrightness = 0f
    private var isTwoFingerSwiping = false
    private var lastChapterHapticIndex = -1


    private var currentOverScrollValue = 0.0f
    private var pendingFlingDirection = 0

    private fun setProgressOfOverscroll(index: Int, progress: Float) {
        val id = generateId(5, index, 0, 0)
        ((binding.realText.findViewHolderForItemId(id) as? ViewHolderState<*>)?.view as? SingleOverscrollChapterBinding)?.let {
            it.progress.max = 10000
            it.progress.progress = (progress.absoluteValue * 10000.0f).toInt()
            val progressVal = progress.absoluteValue
            val targetAlpha = ((progressVal - 0.15f) / 0.65f).coerceIn(0f, 1f)
            it.overscrollCard.alpha = targetAlpha
        }
    }

    private var currentOverScroll: Float
        get() = currentOverScrollValue
        set(value) {
            currentOverScrollValue = if (viewModel.readerType != ReadingType.OVERSCROLL_SCROLL) {
                0.0f
            } else {
                val setTo = value.coerceIn(-1.0f, 1.0f)
                if (setTo == 0.0f) {
                    setProgressOfOverscroll(viewModel.currentIndex + 1, setTo)
                    setProgressOfOverscroll(viewModel.currentIndex - 1, setTo)
                    if (currentOverScrollValue > 0.9) {
                        viewModel.seekToChapter(viewModel.currentIndex - 1)
                    } else if (currentOverScrollValue < -0.9) {
                        viewModel.seekToChapter(viewModel.currentIndex + 1)
                    }
                } else {
                    setProgressOfOverscroll(
                        viewModel.currentIndex + if (setTo < 0.0f) 1 else -1,
                        setTo
                    )
                }
                setTo
            }
            // binding.realText.alpha = (1.0f - currentOverScrollValue.absoluteValue)


            //  val nextId = generateId(5, viewModel.currentIndex+1,0,0)
            //  val prevId = generateId(5, viewModel.currentIndex-1,0,0)


            // binding.realText.translationY =
            //     overscrollMaxTranslation * currentOverScrollValue //alpha = (1.0f - currentOverScrollValue.absoluteValue)
        }



    private fun updateProgressBarVisibility() {
        val showProgress = viewModel.showReaderProgress
        val onlyOnTap = viewModel.showProgressOnlyOnTap
        val isMenuVisible = viewModel.bottomVisibility.value == true
        binding.readerProgressContainer.isVisible = showProgress && (!onlyOnTap || isMenuVisible)
    }

    override fun onDestroy() {
        haloAnimator?.cancel()
        haloAnimator = null
        if (isFinishing) {
            viewModel.stopTTS()
        }
        viewModel.context = null
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // ignore
            }
            batteryReceiver = null
        }
        super.onDestroy()
    }

    fun Slider.setValueRounded(value: Float) {
        this.value = (value.coerceIn(this.valueFrom, this.valueTo) / this.stepSize).roundToInt()
            .toFloat() * this.stepSize
    }




    private var downloadProgressDialog: AlertDialog? = null
    private var downloadProgressBinding: DialogMlDownloadBinding? = null

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        CommonActivity.loadThemes(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        readActivity = this
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var lastScaleTime = 0L
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!viewModel.pinchFontEnabled) return false
                val now = System.currentTimeMillis()
                if (now - lastScaleTime > 150L) {
                    val factor = detector.scaleFactor
                    if (factor > 1.05f) {
                        viewModel.textSize = (viewModel.textSize + 1).coerceIn(8, 60)
                        lastScaleTime = now
                    } else if (factor < 0.95f) {
                        viewModel.textSize = (viewModel.textSize - 1).coerceIn(8, 60)
                        lastScaleTime = now
                    }
                }
                return true
            }
        })
        tapGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return viewModel.tapZonesEnabled && viewModel.bottomVisibility.value != true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!viewModel.tapZonesEnabled) return false
                if (viewModel.bottomVisibility.value == true) return false
                if (viewModel.isTextSelectable) return false
                if (viewModel.isTTSRunning() || viewModel.autoScroll == true) return false

                routeTapZoneClick(e.x, e.y)
                return true
            }
        })
        binding = ReadMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateGlobalBackground()
        updateLuminescentEffects()
        updateReaderInkFlow(0f)
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener { _, key ->
                if (key == getString(R.string.background_image_key) ||
                    key == getString(R.string.background_effect_mode_key) ||
                    key == getString(R.string.background_blur_key) ||
                    key == getString(R.string.background_dim_key) ||
                    key == getString(R.string.background_grain_key) ||
                    key == getString(R.string.background_vignette_key) ||
                    key == getString(R.string.theme_key) ||
                    key == getString(R.string.reader_background_key)
                ) {
                    updateGlobalBackground()
                    if (key == getString(R.string.background_image_key) ||
                        key == getString(R.string.reader_background_key)
                    ) {
                        viewModel.checkDynamicLuminanceContrast()
                    }
                }
                if (key == LUMINESCENT_READER ||
                    key == LUMINESCENT_INTENSITY
                ) {
                    updateLuminescentEffects()
                }
                if (key == com.lagradost.quicknovel.ui.theme.VibePrefs.PREMIUM_VISUALS_ENABLED ||
                    key == com.lagradost.quicknovel.ui.theme.VibePrefs.READER_INK_FLOW
                ) {
                    updateReaderInkFlow(0f)
                }
            }

        registerBattery()
        readingSessionStartTime = android.os.SystemClock.elapsedRealtime()

        viewModel.init(intent, this)
        // Dynamic Slotting: Set topBarHeight based on a slim 64dp standard + the system safe area
        topBarHeight = (64 * resources.displayMetrics.density).toInt() + getStatusBarHeight()
        textAdapter = TextAdapter(
            viewModel,
            TextConfig(
                toolbarHeight = topBarHeight,
                defaultFont = binding.readText.typeface,
                textColor = viewModel.textColor,
                textSize = viewModel.textSize,
                textFont = viewModel.textFont,
                backgroundColor = viewModel.backgroundColor,
                bionicReading = viewModel.bionicReading,
                isTextSelectable = viewModel.isTextSelectable,
                verticalPadding = viewModel.textVerticalPadding,
                lineHeightMultiplier = viewModel.lineHeightMultiplier,
                letterSpacing = viewModel.letterSpacing,
                luminescent = viewModel.luminescentReader,
                luminescentIntensity = viewModel.luminescentIntensity,
                bionicBoldRatio = viewModel.bionicBoldRatio,
            ).also { config ->
                updateOtherTextConfig(config)
            }
        ).apply {
            setHasStableIds(true)
        }

        binding.readToolbar.apply {
            setNavigationIcon(R.drawable.ic_baseline_arrow_back_24)
            setNavigationOnClickListener {
                this@ReadActivity2.onBackPressed()
            }
        }

        // Pixel-Perfect Refinement: Dynamically slot the header below the status bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.readToolbarHolder) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            val density = v.resources.displayMetrics.density
            params.topMargin = systemBars.top + (12 * density).toInt()
            params.leftMargin = (16 * density).toInt()
            params.rightMargin = (16 * density).toInt()
            v.layoutParams = params
            insets
        }

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.readerBottomViewHolder) { v, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val params = v.layoutParams as android.view.ViewGroup.MarginLayoutParams
            val density = v.resources.displayMetrics.density
            params.bottomMargin = (24 * density).toInt() + systemBars.bottom
            v.layoutParams = params
            insets
        }

        observe(viewModel.paddingHorizontalLive) {
            updatePadding()
        }

        observe(viewModel.paddingVerticalLive) {
            updatePadding()
        }


        //observe(viewModel.time12HLive) { time12H ->
        //    binding.readTimeClock.is24HourModeEnabled = !time12H
        //}

        observe(viewModel.backgroundColorLive) { color ->
            // Update the text adapter config immediately, then let updateGlobalBackground
            // handle all view backgrounds consistently (including grain layering).
            if (textAdapter.changeBackgroundColor(color)) {
                updateTextAdapterConfig()
            }
            updateGlobalBackground()
        }

        observe(viewModel.textVerticalPaddingLive) { padding ->
            if (textAdapter.changeTextVerticalPadding(padding)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.lineHeightMultiplierLive) { multiplier ->
            if (textAdapter.changeLineHeightMultiplier(multiplier)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }


        observe(viewModel.bionicReadingLive) { color ->
            if (textAdapter.changeBionicReading(color)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.bionicBoldRatioLive) { ratio ->
            if (textAdapter.changeBionicBoldRatio(ratio)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.autoScrollLive) { enabled ->
            if (enabled == true) {
                startAutoScrollLoop()
            } else {
                autoScrollJob?.cancel()
            }
        }

        observe(viewModel.autoScrollSpeedLive) { _ ->
            if (viewModel.autoScroll == true) {
                startAutoScrollLoop()
            }
        }

        observe(viewModel.isTextSelectableLive) { isTextSelectable ->
            if (textAdapter.changeTextSelectable(isTextSelectable)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.showBatteryLive) { _ ->
            updateOverlayVisibility()
        }

        observe(viewModel.showTimeLive) { _ ->
            updateOverlayVisibility()
        }

        observe(viewModel.luminescentLive) { enabled ->
            updateLuminescentEffects()
            if (textAdapter.changeLuminescent(enabled)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.luminescentIntensityLive) { intensity ->
            updateLuminescentEffects()
            if (textAdapter.changeLuminescentIntensity(intensity)) {
                updateTextAdapterConfig()
            }
        }

        updateOverlayVisibility()

        observe(viewModel.showReaderProgressLive) { _ ->
            updateProgressBarVisibility()
        }

        observe(viewModel.showProgressOnlyOnTapLive) { _ ->
            updateProgressBarVisibility()
        }

        observe(viewModel.screenAwakeLive) { awake ->
            if (awake)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        observe(viewModel.textSizeLive) { size ->
            if (textAdapter.changeSize(size)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.textColorLive) { color ->
            if (textAdapter.changeColor(color)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.isContrastCompromisedLive) { compromised ->
            if (textAdapter.changeContrastCompromised(compromised)) {
                updateTextAdapterConfig()
            }
            updateGlobalBackground()
        }

        observe(viewModel.textFontLive) { font ->
            if (textAdapter.changeFont(font)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.backgroundGrainLive) {
            updateGlobalBackground()
        }

        observe(viewModel.letterSpacingLive) { spacing ->
            if (textAdapter.changeLetterSpacing(spacing)) {
                updateTextAdapterConfig()
                postDesired(binding.realText)
            }
        }

        observe(viewModel.lastReadBreadcrumbLive) { message ->
            if (message != null) {
                com.google.android.material.snackbar.Snackbar.make(
                    binding.root,
                    message,
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                ).show()
                viewModel.lastReadBreadcrumbLive.value = null
            }
        }
 
        textLayoutManager = LinearLayoutManager(binding.realText.context)

        binding.paginatedTextCompose.apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                    PaginatedReaderView(
                        viewModel = viewModel,
                        onToggleMenu = {
                            viewModel.switchVisibility()
                        }
                    )
                }
            }
        }

        val timerComposeView = androidx.compose.ui.platform.ComposeView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setViewTreeLifecycleOwner(this@ReadActivity2)
            setViewTreeViewModelStoreOwner(this@ReadActivity2)
            setViewTreeSavedStateRegistryOwner(this@ReadActivity2)
        }
        (binding.root as? ViewGroup)?.addView(timerComposeView)

        observe(viewModel.showReadingTimerLive) { show ->
            if (show == true) {
                timerComposeView.visibility = View.VISIBLE
                timerComposeView.setContent {
                    com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                        val anchor by viewModel.readingTimerAnchorLive.observeAsState(viewModel.readingTimerAnchor)
                        ReadingTimerOverlay(
                            initialAnchor = anchor,
                            onAnchorChanged = { newAnchor ->
                                viewModel.readingTimerAnchor = newAnchor
                            }
                        )
                    }
                }
            } else {
                timerComposeView.visibility = View.GONE
                timerComposeView.setContent {}
            }
        }

        observe(viewModel.paginatedSwipeEnabledLive) { enabled ->
            if (enabled == true) {
                binding.realText.visibility = View.GONE
                binding.paginatedTextCompose.visibility = View.VISIBLE
            } else {
                binding.realText.visibility = View.VISIBLE
                binding.paginatedTextCompose.visibility = View.GONE
                scrollToDesired()
            }
        }

        binding.ttsActionPausePlay.setOnClickListener {
            viewModel.pausePlayTTS()
        }

        binding.ttsActionStop.setOnClickListener {
            viewModel.stopTTS()
        }

        binding.readStopTranslationBtn.setOnClickListener {
            viewModel.stopTranslation()
        }

        viewModel.isShowingOriginalLive.observe(this) { isOriginal ->
            binding.readTranslateToggle.setImageResource(
                if (isOriginal) R.drawable.ic_google_translate // Show "Translated" icon when showing original
                else R.drawable.translate_24px // Show "Translate" icon when showing translated
            )
        }

        viewModel.translationLoadingStatus.observe(this) { resource ->
            when (resource) {
                is Resource.Failure -> {
                    if (resource.cause is java.util.concurrent.TimeoutException) {
                        CommonActivity.showToast(this, R.string.unable_to_download_language)
                    } else {
                        CommonActivity.showToast(this, "Download Failed: ${resource.errorString}")
                    }
                    downloadProgressDialog?.dismiss()
                    downloadProgressDialog = null
                }

                is Resource.Loading -> {
                    if (downloadProgressDialog == null) {
                        downloadProgressBinding = DialogMlDownloadBinding.inflate(layoutInflater)
                        // ── Non-cancelable: user must press the Cancel button explicitly. ──
                        // This prevents the dialog from being dismissed by a back-press or
                        // outside-tap, which would orphan the download job and silently
                        // skip auto-triggering translation after the download finishes.
                        downloadProgressDialog =
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                this,
                                R.style.AlertDialogCustom
                            )
                                .setView(downloadProgressBinding?.root)
                                .setCancelable(false)
                                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                    // Intentional user cancellation: stop the download job cleanly.
                                    viewModel.stopTranslation()
                                    dialog.dismiss()
                                }
                                .setOnDismissListener {
                                    com.lagradost.quicknovel.util.DrawerHelper.resetScaling(binding.readNormalLayout)
                                }
                                .create()
                        downloadProgressDialog?.show()
                    }
                    downloadProgressBinding?.mlDownloadStatus?.setText(
                        resource.url ?: getString(R.string.download_ml)
                    )
                }

                is Resource.Success<String> -> {
                    downloadProgressDialog?.dismiss()
                    downloadProgressDialog = null

                    when (resource.value) {
                        "Model applied" -> {
                            // ── Auto-trigger: ML Kit model just finished downloading. ──
                            // The original applyMLSettings(true) call returned after kicking
                            // off the download; now that the model is ready we call
                            // applyMLSettings(false) so translation starts immediately without
                            // requiring the user to press "Apply" again.
                            CommonActivity.showToast(this, "Translation model downloaded! Translating...")
                            viewModel.applyMLSettings(false)
                        }
                        else -> { /* Normal success after a non-download translation cycle */ }
                    }
                }
            }
        }

        viewModel.isTranslationActiveLive.observe(this) { active ->
            binding.readTranslateToggle.isVisible = active
        }

        binding.readTranslateToggle.setOnClickListener {
            val current = viewModel.isShowingOriginalLive.value ?: false
            viewModel.isShowingOriginalLive.postValue(!current)
            viewModel.updateReadArea() // Instant switch
        }

        binding.readActionTts.setOnClickListener {
            viewModel.startTTS()
        }

        binding.ttsActionForward.setOnClickListener {
            viewModel.forwardsTTS()
        }

        binding.ttsActionBack.setOnClickListener {
            viewModel.backwardsTTS()
        }

        binding.readActionColorPalette.setOnClickListener {
            showReaderCustomizationDialog(initialTab = 0)
        }

        observeNullable(viewModel.ttsLine) { line ->
            updateTTSLine(line)
        }

        observe(viewModel.title) { title ->
            binding.readToolbar.title = title
        }

        observe(viewModel.chapterTile) { title ->
            binding.readToolbar.subtitle = title.asString(binding.readToolbar.context)
        }

        observe(viewModel.chaptersTitles) { titles ->
            binding.readActionChapters.setOnClickListener {
                val currentChapter = viewModel.desiredIndex?.index ?: -1
                val titlesList = ArrayList(titles.map { it.asString(this) })
                
                val sheet = com.lagradost.quicknovel.ui.reader.ChapterListBottomSheet.newInstance(
                    titlesList,
                    currentChapter,
                    binding.readNormalLayout.id
                )
                sheet.setOnChapterSelectedListener { which ->
                    viewModel.seekToChapter(which)
                }
                sheet.show(supportFragmentManager, "chapter_list")
            }
        }


        /*binding.readToolbar.setOnMenuItemClickListener {
            TimePickerDialog(
                binding.readToolbar.context,
                { _, hourOfDay, minute -> println("TIME PICKED: $hourOfDay , $minute") },
                Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                Calendar.getInstance().get(Calendar.MINUTE),
                true
            )
            true
        }*/

        observe(viewModel.ttsStatus) { status ->
            val isTTSRunning = status != TTSHelper.TTSStatus.IsStopped

            /*if (isTTSRunning) {
                binding.readToolbar.inflateMenu(R.menu.sleep_timer)
            } else {
                binding.readToolbar.menu.clear()
            }*/

            binding.readerBottomView.isGone = isTTSRunning
            binding.readerBottomViewTts.isVisible = isTTSRunning
            binding.ttsActionPausePlay.setImageResource(
                when (status) {
                    TTSHelper.TTSStatus.IsPaused -> R.drawable.ic_baseline_play_arrow_24
                    TTSHelper.TTSStatus.IsRunning -> R.drawable.ic_baseline_pause_24
                    TTSHelper.TTSStatus.IsStopped -> R.drawable.ic_baseline_play_arrow_24
                }
            )
        }

        binding.apply {
            realText.setOnClickListener {
                viewModel.switchVisibility()
            }
            readToolbar.setOnClickListener {
                viewModel.switchVisibility()
            }
            readerLinContainer.setOnClickListener {
                viewModel.switchVisibility()
            }
        }

        observe(viewModel.bottomVisibility) { visibility ->
            updateProgressBarVisibility()
            if (visibility) {
                showSystemUI()
                // here we actually do not want to fix the tts bug, as it will cause a bad behavior
                // when the text is very low
            } else {
                hideSystemUI()
                // otherwise we have a shitty bug with tts locking range
                binding.root.post {
                    updateTTSLine(viewModel.ttsLine.value)
                }
            }
        }

        var last: Resource<String>? = null // very dirty
        observe(viewModel.loadingStatus) { loading ->
            val different = last != loading
            last = loading
            when (loading) {
                is Resource.Success<String> -> {
                    binding.readLoading.isVisible = false
                    binding.readFail.isVisible = false
                    binding.readSkeletonShimmer.root.isVisible = false
                    (binding.readSkeletonShimmer.root as? ShimmerFrameLayout)?.stopShimmer()

                    binding.readNormalLayout.isVisible = true
                    val isPaginated = viewModel.paginatedSwipeEnabled
                    binding.realText.isVisible = !isPaginated
                    binding.paginatedTextCompose.isVisible = isPaginated
                    
                    // Force the toolbar title/subtitle refresh if needed
                    val title = viewModel.book.getChapterTitle(viewModel.currentIndex)
                    if (different) {
                        viewModel.updateReadArea(seekToDesired = false)
                    }

                    if (different) {
                        binding.readNormalLayout.alpha = 0f

                        ObjectAnimator.ofFloat(binding.readNormalLayout, "alpha", 1f).apply {
                            duration = 400
                            interpolator = android.view.animation.DecelerateInterpolator(1.5f)
                            start()
                        }
                        // Reset progress bar on chapter change
                        binding.readerReadingProgressFill.pivotX = 0f
                        binding.readerReadingProgressFill.scaleX = 0f
                    } else {
                        binding.readNormalLayout.alpha = 1.0f
                    }
                }

                is Resource.Loading -> {
                    binding.readFail.isVisible = false
                    binding.realText.isVisible = false
                    binding.paginatedTextCompose.isVisible = false

                    val urlText = loading.url
                    val isTranslatingProgress = urlText != null && urlText.contains("/") && urlText.contains("(")

                    if (isTranslatingProgress) {
                        // Translation progress active - show translation progress card overlay
                        binding.readLoading.isVisible = true
                        binding.readSkeletonShimmer.root.isVisible = false
                        (binding.readSkeletonShimmer.root as? ShimmerFrameLayout)?.stopShimmer()

                        val regex = Regex("""\((\d+)/(\d+)\)""")
                        val match = regex.find(urlText!!)
                        if (match != null) {
                            val current = match.groupValues[1].toIntOrNull() ?: 0
                            val total = match.groupValues[2].toIntOrNull() ?: 100
                            binding.readLoadingProgressBar.apply {
                                max = total
                                progress = current
                            }
                            binding.readLoadingPercentage.text = "${if (total > 0) (current * 100) / total else 0}%"
                            binding.readLoadingFraction.text = "$current / $total"
                            
                            // Clean the text shown inside the loading card to look nice
                            val cleanText = urlText.replace(regex, "").trim()
                            binding.loadingText.apply {
                                isVisible = true
                                text = cleanText
                            }
                        } else {
                            binding.loadingText.apply {
                                isVisible = true
                                text = urlText
                            }
                            binding.readLoadingProgressBar.isIndeterminate = true
                        }
                    } else {
                        // Standard chapter loading - hide progress card and show skeleton shimmer
                        binding.readLoading.isVisible = false
                        binding.readNormalLayout.isVisible = true 
                        binding.readNormalLayout.alpha = 1f
                        
                        val shimmer = binding.readSkeletonShimmer.root as? ShimmerFrameLayout
                        shimmer?.isVisible = true
                        shimmer?.startShimmer()

                        binding.loadingText.apply {
                            isGone = urlText.isNullOrBlank()
                            text = urlText ?: ""
                        }
                    }
                }

                is Resource.Failure -> {
                    binding.readLoading.isVisible = false
                    binding.readSkeletonShimmer.root.isVisible = false
                    (binding.readSkeletonShimmer.root as? ShimmerFrameLayout)?.stopShimmer()
                    binding.readFail.isVisible = true
                    binding.failText.text = loading.errorString
                    binding.readNormalLayout.isVisible = false
                }
            }
        }

        binding.realText.apply {
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    if (viewModel.tapZonesEnabled && viewModel.bottomVisibility.value != true) {
                        tapGestureDetector.onTouchEvent(e)
                    }
                    if (e.pointerCount >= 2 && (viewModel.pinchFontEnabled || viewModel.swipeBrightnessEnabled)) {
                        return true
                    }
                    return false
                }
            })
            addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) {
                    val settingsManager = PreferenceManager.getDefaultSharedPreferences(this@ReadActivity2)
                    val performanceMode = settingsManager.getBoolean("performance_mode_enabled", false)
                    val lEnabled = !performanceMode && settingsManager.getBoolean(getString(R.string.luminescent_reader_key), false)
                    if (lEnabled) {
                        val lIntensity = settingsManager.getSafeInt(getString(R.string.luminescent_intensity_key), 50)
                        applyLuminescenceToView(view, lEnabled, lIntensity)
                    }
                }

                override fun onChildViewDetachedFromWindow(view: View) {}
            })
            layoutManager = textLayoutManager
            adapter = textAdapter
            itemAnimator = null
            // testing overscroll
            setOnTouchListener { _, event ->
                viewModel.onUserInteraction()
                if (event.pointerCount >= 2 || isTwoFingerSwiping) {
                    if (viewModel.pinchFontEnabled && event.pointerCount >= 2) {
                        scaleGestureDetector.onTouchEvent(event)
                    }
                    if (viewModel.swipeBrightnessEnabled) {
                        when (event.actionMasked) {
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                startTwoFingerX = (event.getX(0) + event.getX(1)) / 2
                                val lp = window.attributes
                                startBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                isTwoFingerSwiping = true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (event.pointerCount >= 2) {
                                    if (!isTwoFingerSwiping) {
                                        startTwoFingerX = (event.getX(0) + event.getX(1)) / 2
                                        val lp = window.attributes
                                        startBrightness = if (lp.screenBrightness < 0f) 0.5f else lp.screenBrightness
                                        isTwoFingerSwiping = true
                                    }
                                    val currentX = (event.getX(0) + event.getX(1)) / 2
                                    val dx = currentX - startTwoFingerX
                                    val newBrightness = (startBrightness + dx / 800f).coerceIn(0.01f, 1f)
                                    val lp = window.attributes
                                    lp.screenBrightness = newBrightness
                                    window.attributes = lp
                                }
                            }
                            MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isTwoFingerSwiping = false
                            }
                        }
                    }
                    return@setOnTouchListener true
                }
                when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        lastTouchTime = System.currentTimeMillis()
                    }
                }
                when (event.action) {
                    MotionEvent.ACTION_MOVE -> {
                        if (event.historySize <= 1) return@setOnTouchListener false
                        val start = event.getHistoricalY(0, event.historySize - 1)
                        val end = event.getY(0)
                        val dy = (end - start).div(Resources.getSystem().displayMetrics.density)
                            .coerceIn(-1.5f, 1.5f)
                        // if cant scroll in the direction then translate Y with the dy
                        val translated = !canScrollVertically(-1) || !canScrollVertically(1)
                        if (translated) {
                            // * (maxScrollOver - currentOverScroll.absoluteValue))
                            currentOverScroll += dy * 0.1f
                        }

                        // if we can scroll down then we cant translate down
                        if (canScrollVertically(1) && currentOverScroll < 0.0f) {
                            currentOverScroll = 0.0f
                            return@setOnTouchListener false
                        }

                        // if we can scroll up then we cant translate up
                        if (canScrollVertically(-1) && currentOverScroll > 0.0f) {
                            currentOverScroll = 0.0f
                            return@setOnTouchListener false
                        }

                        return@setOnTouchListener false
                    }

                    MotionEvent.ACTION_UP -> {
                        currentOverScroll = 0.0f
                    }

                    else -> {}
                }
                return@setOnTouchListener false
            }


            addOnScrollListener(object :
                RecyclerView.OnScrollListener() {
                var updateFromCode = false
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (recyclerView.scrollState == RecyclerView.SCROLL_STATE_SETTLING && viewModel.loadingStatus.value !is Resource.Loading) {
                        if (dy > 0 && !recyclerView.canScrollVertically(1)) {
                            if (viewModel.readerType == ReadingType.OVERSCROLL_SCROLL || viewModel.readerType == ReadingType.BTT_SCROLL) {
                                if (viewModel.currentIndex + 1 < viewModel.book.size()) {
                                    pendingFlingDirection = 1
                                    viewModel.seekToChapter(viewModel.currentIndex + 1)
                                }
                            }
                        } else if (dy < 0 && !recyclerView.canScrollVertically(-1)) {
                            if (viewModel.readerType == ReadingType.OVERSCROLL_SCROLL || viewModel.readerType == ReadingType.BTT_SCROLL) {
                                if (viewModel.currentIndex - 1 >= 0) {
                                    pendingFlingDirection = -1
                                    viewModel.seekToChapter(viewModel.currentIndex - 1)
                                }
                            }
                        }
                    }
                    if (dy != 0 && !updateFromCode) {
                        var rdy = dy

                        lockTop?.let { lock ->
                            if (currentScroll + rdy > lock) {
                                rdy = lock - currentScroll
                                fling(0, 0)
                            }
                        }

                        lockBottom?.let { lock ->
                            if (currentScroll + rdy < lock) {
                                rdy = lock - currentScroll
                                fling(0, 0)
                            }
                        }

                        if (currentOverScroll < 0.0f && rdy < 0) {
                            rdy = 0
                        } else if (currentOverScroll > 0.0f && rdy > 0) {
                            rdy = 0
                        }
                        /*println("currentOverScrollTranslation=$currentOverScrollTranslation rdy=$rdy")
                        val dscroll = minOf(currentOverScrollTranslation.absoluteValue.toInt(), rdy.absoluteValue)
                        if(currentOverScrollTranslation < 0 && rdy < 0) {
                            currentOverScrollTranslation += dscroll
                            rdy += dscroll
                        }
                        if(currentOverScrollTranslation > 0 && rdy > 0) {
                            currentOverScrollTranslation -= dscroll
                            rdy -= dscroll
                        }*/

                        currentScroll += dy
                        val delta = rdy - dy
                        if (delta != 0 && canScrollVertically(delta)) {
                            //updateFromCode = true
                            scrollBy(0, delta)
                        }
                    } else {
                        updateFromCode = false
                        onScroll()
                    }

                    super.onScrolled(recyclerView, dx, dy)
                }

                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    super.onScrollStateChanged(recyclerView, newState)
                    if (newState == RecyclerView.SCROLL_STATE_SETTLING || newState == RecyclerView.SCROLL_STATE_IDLE) {
                        onScroll()
                    }

                    // binding.tmpTtsEnd.fixLine((getBottomY()- remainingBottom) + 7.toPx)
                    // binding.tmpTtsStart.fixLine(remainingTop + 7.toPx)
                }
            })
        }

        //here inserted novel chapter text into recyclerview
        observe(viewModel.chapter) { chapter ->
            cachedChapter = chapter.data

            val currentIdx = viewModel.currentIndex
            if (lastChapterHapticIndex != -1 && lastChapterHapticIndex != currentIdx) {
                binding.realText.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
            lastChapterHapticIndex = currentIdx

            if (chapter.seekToDesired && !viewModel.isTTSRunning()) {
                textAdapter.submitIncomparableList(chapter.data) {
                    viewModel.postLoadingStatus(Resource.Success(""))
                    scrollToDesired()
                    UsageStatsManager.incrementChapterRead(this@ReadActivity2)
                }
            } else {
                textAdapter.submitList(chapter.data) {
                    viewModel.postLoadingStatus(Resource.Success(""))
                    if (chapter.seekToDesired) {
                        scrollToDesired()
                    } else {
                        binding.realText.post {
                            onScroll()
                        }
                    }
                    UsageStatsManager.incrementChapterRead(this@ReadActivity2)
                }
            }
        }

        observeNullable(viewModel.ttsTimeRemaining) { time ->
            if (time == null) {
                binding.ttsStopTime.isVisible = false
            } else {
                binding.ttsStopTime.isVisible = true
                binding.ttsStopTime.text =
                    binding.ttsStopTime.context.getString(R.string.sleep_format_stop)
                        .format(time.divCeil(60_000L))
            }
        }

        binding.readActionSettings.setOnClickListener {
            // Fix: Hide the main reader controls to prevent overlap with settings
            hideSystemUI()
            
            val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDrawerTheme)
            bottomSheetDialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            bottomSheetDialog.behavior.isDraggable = false
            val composeView = androidx.compose.ui.platform.ComposeView(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                        com.lagradost.quicknovel.ui.reader.ReaderSettingsSheet(
                            viewModel = viewModel,
                            onHardReset = {
                                showToast(getString(R.string.reload_chapter_format).format(""))
                                viewModel.reloadChapter()
                            },
                            onShowCustomization = { showReaderCustomizationDialog(initialTab = 0) },
                            onShowTapZones = { showTapZonesCustomizationDialog() },
                            onReadingTypeClick = {
                                val items = ReadingType.entries.toTypedArray()
                                val displayItems = ArrayList(items.map { getString(it.stringRes) })
                                val currentIndex = items.indexOf(viewModel.readerType)

                                val sheet = com.lagradost.quicknovel.ui.reader.OptionsSelectionBottomSheet.newInstance(
                                    getString(R.string.scroll_type),
                                    displayItems,
                                    currentIndex,
                                    this@ReadActivity2.binding.readNormalLayout.id
                                )
                                sheet.setOnItemSelectedListener { which ->
                                    viewModel.readerType = items[which]
                                }
                                sheet.show(supportFragmentManager, "reading_type")
                            },
                            onShowFonts = { showFonts() },
                            onLanguageClick = {
                                ioSafe {
                                    viewModel.ttsSession.requireEngine({ tts ->
                                        runOnUiThread {
                                            val voices = tts.getVoices()
                                            val languages = mutableListOf<java.util.Locale?>(null).apply {
                                                addAll(voices.map { it.locale }.distinct().sortedBy { it.displayName })
                                            }
                                            val currentVoiceName = tts.getCurrentVoiceName()
                                            val currentVoice = voices.find { it.name == currentVoiceName }
                                            val currentIndex = if (currentVoice != null) languages.indexOf(currentVoice.locale) else 0
                                            
                                            this@ReadActivity2.showDialog(
                                                languages.map { it?.displayName ?: getString(R.string.default_text) },
                                                currentIndex,
                                                getString(R.string.tts_locale), false, {}
                                            ) { index ->
                                                viewModel.setTTSVoice(null)
                                            }
                                        }
                                    }, action = { false })
                                }
                            },
                            onVoiceClick = {
                                ioSafe {
                                    viewModel.ttsSession.requireEngine({ tts ->
                                        runOnUiThread {
                                            val allVoices = tts.getVoices()
                                            val currentVoiceName = tts.getCurrentVoiceName()
                                            val matchAgainst = allVoices.find { it.name == currentVoiceName }?.locale
                                            val voices = mutableListOf<Pair<String, com.lagradost.quicknovel.EngineVoice?>>(getString(R.string.default_text) to null).apply {
                                                val filtered = if (matchAgainst == null) allVoices else allVoices.filter { it.locale == matchAgainst }
                                                addAll(filtered.map { ("${it.name} ${if (it.isNetworkRequired) "(☁)" else ""}") to it }.sortedBy { (name, _) -> name })
                                            }
                                            val selectedIndex = if (currentVoiceName != null) voices.indexOfFirst { it.second?.name == currentVoiceName }.takeIf { it != -1 } ?: 0 else 0
                                            this@ReadActivity2.showDialog(
                                                voices.map { it.first },
                                                selectedIndex,
                                                getString(R.string.tts_locale), false, {}
                                            ) { index ->
                                                val voice = voices.getOrNull(index)?.second
                                                viewModel.setTTSVoice(voice?.name)
                                            }
                                        }
                                    }, action = { false })
                                }
                            },
                            onSleepTimerClick = {
                                val items = mutableListOf(getString(R.string.default_text) to 0L)
                                for (i in 1L..120L) items.add(getString(R.string.sleep_format).format(i.toInt()) to (i * 60000L))
                                this@ReadActivity2.showDialog(
                                    items.mapNotNull { it.first },
                                    items.map { it.second }.indexOf(viewModel.ttsTimer),
                                    getString(R.string.sleep_timer), false, {}
                                ) { index ->
                                    if (index >= 0 && index < items.size) viewModel.ttsTimer = items[index].second
                                }
                            },
                            onMlFromClick = {
                                val items = ReadActivityViewModel.MLSettings.list
                                this@ReadActivity2.showDialog(
                                    items.map { it.second },
                                    items.map { it.first }.indexOf(viewModel.mlFromLanguage),
                                    getString(R.string.sleep_timer), false, {}
                                ) { index ->
                                    viewModel.mlFromLanguage = items[index].first
                                }
                            },
                            onMlToClick = {
                                val items = ReadActivityViewModel.MLSettings.list
                                this@ReadActivity2.showDialog(
                                    items.map { it.second },
                                    items.map { it.first }.indexOf(viewModel.mlToLanguage),
                                    getString(R.string.sleep_timer), false, {}
                                ) { index ->
                                    viewModel.mlToLanguage = items[index].first
                                }
                            },
                            onApplyTranslationClick = {
                                viewModel.applyMLSettings(true)
                                bottomSheetDialog.dismiss()
                            },
                            onMlInfoClick = {
                                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ReadActivity2, R.style.AlertDialogCustom)
                                    .setTitle(R.string.ml_info_title)
                                    .setMessage(R.string.ml_info_text)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show()
                            },
                            onColorCustomClick = {
                                val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ReadActivity2, R.style.AlertDialogCustom)
                                builder.setTitle(getString(R.string.reading_color))
                                
                                var dialogRef: android.content.DialogInterface? = null
                                val composeView = androidx.compose.ui.platform.ComposeView(this@ReadActivity2).apply {
                                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                                    setContent {
                                        com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                                            androidx.compose.foundation.layout.Column(
                                                modifier = androidx.compose.ui.Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                                            ) {
                                                // Background Color Card
                                                androidx.compose.material3.Card(
                                                    modifier = androidx.compose.ui.Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            dialogRef?.dismiss()
                                                            ColorPickerDialog.newBuilder()
                                                                .setDialogId(0)
                                                                .setColor(viewModel.backgroundColor)
                                                                .show(this@ReadActivity2)
                                                        },
                                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                                ) {
                                                    androidx.compose.foundation.layout.Row(
                                                        modifier = androidx.compose.ui.Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                    ) {
                                                        androidx.compose.foundation.layout.Box(
                                                            modifier = androidx.compose.ui.Modifier
                                                                .size(24.dp)
                                                                .background(androidx.compose.ui.graphics.Color(viewModel.backgroundColor), androidx.compose.foundation.shape.CircleShape)
                                                                .border(1.dp, androidx.compose.ui.graphics.Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                                        )
                                                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                                                        androidx.compose.material3.Text(
                                                            text = getString(R.string.background_color),
                                                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                
                                                // Text Color Card
                                                androidx.compose.material3.Card(
                                                    modifier = androidx.compose.ui.Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            dialogRef?.dismiss()
                                                            ColorPickerDialog.newBuilder()
                                                                .setDialogId(1)
                                                                .setColor(viewModel.textColor)
                                                                .show(this@ReadActivity2)
                                                        },
                                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                                    ),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                                ) {
                                                    androidx.compose.foundation.layout.Row(
                                                        modifier = androidx.compose.ui.Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                                    ) {
                                                        androidx.compose.foundation.layout.Box(
                                                            modifier = androidx.compose.ui.Modifier
                                                                .size(24.dp)
                                                                .background(androidx.compose.ui.graphics.Color(viewModel.textColor), androidx.compose.foundation.shape.CircleShape)
                                                                .border(1.dp, androidx.compose.ui.graphics.Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                                        )
                                                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                                                        androidx.compose.material3.Text(
                                                            text = getString(R.string.text_color),
                                                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                builder.setView(composeView)
                                builder.setPositiveButton(R.string.ok) { dialog, _ -> dialog.dismiss(); updateImages() }
                                val dialog = builder.show()
                                dialogRef = dialog
                                dialog.applyGlassStyle()
                                updateImages()
                            },
                            onColorSelect = { bg, txt ->
                                viewModel.backgroundColor = bg
                                viewModel.textColor = txt
                                updateImages()
                            },
                            onDismiss = {
                                bottomSheetDialog.dismiss()
                            }
                        )
                    }
                }
            }
            composeView.setViewTreeLifecycleOwner(this@ReadActivity2)
            composeView.setViewTreeViewModelStoreOwner(this@ReadActivity2)
            composeView.setViewTreeSavedStateRegistryOwner(this@ReadActivity2)
            bottomSheetDialog.setContentView(composeView)

            // Background scaling animation using activity outer binding
            val activityBinding = this@ReadActivity2.binding
            val backgroundView = activityBinding.readNormalLayout
            val behavior = bottomSheetDialog.behavior
            
            // Crucial fix: Reset scaling when dismissed regardless of how it's closed
            bottomSheetDialog.setOnDismissListener {
                com.lagradost.quicknovel.util.DrawerHelper.resetScaling(backgroundView)
            }

            behavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {
                    if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN || 
                        newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                        com.lagradost.quicknovel.util.DrawerHelper.resetScaling(backgroundView)
                    }
                }
                override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                    com.lagradost.quicknovel.util.DrawerHelper.applyScalingAnimation(backgroundView, slideOffset)
                }
            })

            bottomSheetDialog.show()
            bottomSheetDialog.applyGlassStyle()
        }
    }

    private fun showReaderCustomizationDialog(initialTab: Int = 0) {
        val sheet = com.lagradost.quicknovel.ui.reader.customization.ReaderCustomizationSheet.newInstance(initialTab)
        sheet.setViewModel(viewModel)
        sheet.show(supportFragmentManager, "reader_customization")
    }

    private fun showTapZonesCustomizationDialog() {
        val sheet = com.lagradost.quicknovel.ui.reader.TapZoneCustomizationSheet.newInstance()
        sheet.setViewModel(viewModel)
        sheet.show(supportFragmentManager, "tap_zones_customization")
    }

    fun routeTapZoneClick(x: Float, y: Float) {
        val width = binding.realText.width
        val height = binding.realText.height
        if (width <= 0 || height <= 0) return

        val zone = when {
            y < height * 0.2f -> viewModel.tapZoneTop
            y > height * 0.8f -> viewModel.tapZoneBottom
            x < width * 0.3f -> viewModel.tapZoneLeft
            x > width * 0.7f -> viewModel.tapZoneRight
            else -> viewModel.tapZoneCenter
        }

        executeTapZoneAction(zone)
    }

    private fun executeTapZoneAction(action: String) {
        when (action) {
            "Prev Chapter" -> {
                if (viewModel.currentIndex > 0) {
                    viewModel.seekToChapter(viewModel.currentIndex - 1)
                }
            }
            "Next Chapter" -> {
                if (viewModel.currentIndex + 1 < viewModel.book.size()) {
                    viewModel.seekToChapter(viewModel.currentIndex + 1)
                }
            }
            "Toggle UI" -> {
                viewModel.switchVisibility()
            }
            "Toggle TTS" -> {
                if (viewModel.isTTSRunning()) {
                    viewModel.pausePlayTTS()
                } else {
                    viewModel.startTTS()
                }
            }
            "Scroll Up" -> {
                binding.realText.smoothScrollBy(0, -binding.realText.height / 2)
            }
            "Scroll Down" -> {
                binding.realText.smoothScrollBy(0, binding.realText.height / 2)
            }
        }
    }

    private fun showThemePicker() {
        val backgroundView = binding.readNormalLayout
        val bottomSheetDialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDrawerTheme)
        val dialogBinding = ReadThemePickerBinding.inflate(layoutInflater, null, false)
        bottomSheetDialog.setContentView(dialogBinding.root)

        // Set imageHolder so updateImages() can locate the checkmark views and custom color button
        imageHolder = dialogBinding.readThemeColorsContainer

        val bgColors = resources.getIntArray(R.array.readerBgColors)
        val textColors = resources.getIntArray(R.array.readerTextColors)

        for ((newBgColor, newTextColor) in bgColors zip textColors) {
            ColorRoundCheckmarkBinding.inflate(
                layoutInflater,
                dialogBinding.readThemeColorsContainer,
                true
            ).image1.apply {
                backgroundTintList = ColorStateList.valueOf(newBgColor)
                setOnClickListener {
                    viewModel.backgroundColor = newBgColor
                    viewModel.textColor = newTextColor
                    updateImages()
                }
            }
        }

        // Add custom color button
        ColorRoundCheckmarkBinding.inflate(
            layoutInflater,
            dialogBinding.readThemeColorsContainer,
            true
        ).image1.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                foreground = ContextCompat.getDrawable(this.context, R.drawable.ic_baseline_add_24)
            }

            setOnClickListener {
                val builder = MaterialAlertDialogBuilder(this.context, R.style.AlertDialogCustom)
                builder.setTitle(getString(R.string.reading_color))
                builder.setOnDismissListener {
                    com.lagradost.quicknovel.util.DrawerHelper.resetScaling(backgroundView)
                }

                var dialogRef: android.content.DialogInterface? = null
                val composeView = androidx.compose.ui.platform.ComposeView(this.context).apply {
                    setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                    setContent {
                        com.lagradost.quicknovel.ui.theme.QuickNovelTheme {
                            androidx.compose.foundation.layout.Column(
                                modifier = androidx.compose.ui.Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                            ) {
                                // Background Color Card
                                androidx.compose.material3.Card(
                                    modifier = androidx.compose.ui.Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dialogRef?.dismiss()
                                            ColorPickerDialog.newBuilder()
                                                .setDialogId(0)
                                                .setColor(viewModel.backgroundColor)
                                                .show(this@ReadActivity2)
                                        },
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = androidx.compose.ui.Modifier
                                                .size(24.dp)
                                                .background(androidx.compose.ui.graphics.Color(viewModel.backgroundColor), androidx.compose.foundation.shape.CircleShape)
                                                .border(1.dp, androidx.compose.ui.graphics.Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                        )
                                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                                        androidx.compose.material3.Text(
                                            text = getString(R.string.background_color),
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                
                                // Text Color Card
                                androidx.compose.material3.Card(
                                    modifier = androidx.compose.ui.Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            dialogRef?.dismiss()
                                            ColorPickerDialog.newBuilder()
                                                .setDialogId(1)
                                                .setColor(viewModel.textColor)
                                                .show(this@ReadActivity2)
                                        },
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                                     ),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                                ) {
                                    androidx.compose.foundation.layout.Row(
                                        modifier = androidx.compose.ui.Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        androidx.compose.foundation.layout.Box(
                                            modifier = androidx.compose.ui.Modifier
                                                .size(24.dp)
                                                .background(androidx.compose.ui.graphics.Color(viewModel.textColor), androidx.compose.foundation.shape.CircleShape)
                                                .border(1.dp, androidx.compose.ui.graphics.Color.Gray, androidx.compose.foundation.shape.CircleShape)
                                        )
                                        androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(16.dp))
                                        androidx.compose.material3.Text(
                                            text = getString(R.string.text_color),
                                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                builder.setView(composeView)
                builder.setPositiveButton(R.string.ok) { dialog, _ ->
                    dialog.dismiss()
                    updateImages()
                }

                val dialog = builder.show()
                dialogRef = dialog
                dialog.applyGlassStyle()
                updateImages()
            }
        }

        // Background scaling and blur animation
        val behavior = bottomSheetDialog.behavior
        bottomSheetDialog.setOnDismissListener {
            com.lagradost.quicknovel.util.DrawerHelper.resetScaling(backgroundView)
        }

        behavior.addBottomSheetCallback(object : com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: android.view.View, newState: Int) {
                if (newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_HIDDEN ||
                    newState == com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED) {
                    com.lagradost.quicknovel.util.DrawerHelper.resetScaling(backgroundView)
                }
            }
            override fun onSlide(bottomSheet: android.view.View, slideOffset: Float) {
                com.lagradost.quicknovel.util.DrawerHelper.applyScalingAnimation(backgroundView, slideOffset)
            }
        })

        updateImages() // Dynamic theme and checkmark initialization
        bottomSheetDialog.show()
        bottomSheetDialog.applyGlassStyle()
    }

    private fun updateOverlayVisibility() {
        val showTime = viewModel.showTime
        val showBattery = viewModel.showBattery

        binding.apply {
            readTimeClock.isVisible = showTime
            readBattery.isVisible = showBattery
            readOverlay.isVisible = showTime || showBattery
        }

        if (viewModel.bottomVisibility.value != true) {
            hideSystemUI()
        }

        updateLuminescentEffects()
    }
    private fun interpolateColor(a: Int, b: Int, proportion: Float): Int {
        val hsvA = FloatArray(3)
        val hsvB = FloatArray(3)
        Color.colorToHSV(a, hsvA)
        Color.colorToHSV(b, hsvB)
        for (i in 0..2) {
            hsvB[i] = hsvA[i] + (hsvB[i] - hsvA[i]) * proportion
        }
        return Color.HSVToColor(hsvB)
    }
}
