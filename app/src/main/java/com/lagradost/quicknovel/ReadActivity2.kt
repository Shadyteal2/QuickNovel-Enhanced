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
import android.widget.TextView
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.EditText
import android.widget.LinearLayout
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.Uri
import androidx.preference.PreferenceManager
import coil3.load
import coil3.request.crossfade
import com.facebook.shimmer.ShimmerFrameLayout
import com.lagradost.quicknovel.util.UsageStatsManager
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
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.lagradost.quicknovel.util.UIHelper.systemFonts
import com.lagradost.quicknovel.databinding.ReadMainBinding
import com.lagradost.quicknovel.databinding.SingleOverscrollChapterBinding
import com.lagradost.quicknovel.mvvm.Resource
import com.lagradost.quicknovel.mvvm.observe
import com.lagradost.quicknovel.mvvm.observeNullable
import android.widget.Toast
import com.lagradost.quicknovel.ui.CONFIG_COLOR
import com.lagradost.quicknovel.ui.CONFIG_FONT
import com.lagradost.quicknovel.ui.CONFIG_FONT_BOLD
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
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.Coroutines.ioSafe
import com.lagradost.quicknovel.util.SingleSelectionHelper.showDialog
import com.lagradost.quicknovel.util.divCeil
import com.lagradost.quicknovel.util.toPx
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

        fun higherBottomNavView(v: View) {
            v.isVisible = true
            val params = v.layoutParams as? android.view.ViewGroup.MarginLayoutParams
            val margin = params?.bottomMargin?.toFloat() ?: 0f
            v.translationY = v.height.toFloat() + margin
            ObjectAnimator.ofFloat(v, "translationY", 0f).apply {
                duration = 200
                start()
            }
        }

        higherBottomNavView(binding.readerBottomViewHolder)

        // Pixel-Perfect Reset: Start from offset that clears the notch/status bar
        val params = binding.readToolbarHolder.layoutParams as? android.view.ViewGroup.MarginLayoutParams
        val topMargin = params?.topMargin?.toFloat() ?: (12 * resources.displayMetrics.density)
        binding.readToolbarHolder.translationY = -(binding.readToolbarHolder.height.toFloat() + topMargin)

        ObjectAnimator.ofFloat(binding.readToolbarHolder, "translationY", 0f).apply {
            duration = 200
            start()
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

    override fun onColorSelected(dialog: Int, color: Int) {
        when (dialog) {
            0 -> setBackgroundColor(color)
            1 -> setTextColor(color)
        }
    }

    private var readingSessionStartTime: Long = 0L

    fun showDictionary(word: String) {
        DictionaryBottomSheet.newInstance(word).show(supportFragmentManager, "dictionary")
    }

    fun showTranslation(text: String) {
        TranslationBottomSheet(text).show(supportFragmentManager, "translation")
    }

    private fun setBackgroundColor(color: Int) {
        viewModel.backgroundColor = color
    }

    private fun updateGlobalBackground() {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)
        val blur = settingsManager.getInt(getString(R.string.background_blur_key), 0)
        val dim = settingsManager.getInt(getString(R.string.background_dim_key), 0)
        val isLightTheme = settingsManager.getString(getString(R.string.theme_key), "Amoled") == "Light"
        val isEnabled = settingsManager.getBoolean(getString(R.string.reader_background_key), false)

        binding.apply {
            if (!isEnabled || imageUri.isNullOrBlank()) {
                readerBackgroundImage.isVisible = false
                readerBackgroundDim.isVisible = false
                readerBackgroundLightScrim.isVisible = false
                // Restore solid background if disabled
                root.setBackgroundColor(viewModel.backgroundColor)
                return@apply
            }

            readerBackgroundImage.isVisible = true
            readerBackgroundDim.isVisible = true
            readerBackgroundLightScrim.isVisible = true
            
            // Make actual containers transparent so background shows through
            root.setBackgroundColor(Color.TRANSPARENT)
            readOverlay.setBackgroundColor(Color.TRANSPARENT)

            readerBackgroundImage.load(Uri.parse(imageUri)) {
                crossfade(true)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (blur > 0) {
                    readerBackgroundImage.setRenderEffect(
                        RenderEffect.createBlurEffect(
                            blur.toFloat(),
                            blur.toFloat(),
                            Shader.TileMode.CLAMP
                        )
                    )
                } else {
                    readerBackgroundImage.setRenderEffect(null)
                }
            }

            readerBackgroundDim.alpha = dim / 100f
            readerBackgroundLightScrim.alpha = if (isLightTheme) 0.3f else 0f
        }
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
        val color = viewModel.backgroundColor
        val colorPrimary = colorFromAttribute(R.attr.colorPrimary)
        val colorPrim = ColorStateList.valueOf(colorPrimary)
        val colorTrans = ColorStateList.valueOf(Color.TRANSPARENT)
        var foundCurrentColor = false
        val fullAlpha = 200
        val fadedAlpha = 50

        for ((index, imgHolder) in imageHolder?.children?.withIndex() ?: return) {
            val img = imgHolder.findViewById<ImageView>(R.id.image1) ?: return

            if (index == bgColors.size) { // CUSTOM COLOR
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            kill()
            return true
        }
        if ((keyCode != KeyEvent.KEYCODE_VOLUME_DOWN && keyCode != KeyEvent.KEYCODE_VOLUME_UP)) return false

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
        val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver() {
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
        this.registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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
            )
        )
    }

    fun onScroll() {
        postLines(getAllLines())
    }

    private var cachedChapter: List<SpanDisplay> = emptyList()
    private fun scrollToDesired() {
        val desired: ScrollIndex = viewModel.desiredIndex ?: return
        val adapterPosition =
            cachedChapter.indexOfFirst { display -> display.index == desired.index && display.innerIndex == desired.innerIndex }
        if (adapterPosition == -1) return

        //val offset = 7.toPx
        textLayoutManager.scrollToPositionWithOffset(adapterPosition, 1)

        // don't inner-seek if zero because that is chapter break
        if (desired.innerIndex == 0) return

        binding.realText.post {
            getAllLines().also { postLines(it) }.firstOrNull { line ->
                line.index == desired.index && line.endChar >= desired.char
            }?.let { line ->
                //binding.tmpTtsStart2.fixLine(line.top)
                //binding.tmpTtsEnd2.fixLine(line.bottom)
                binding.realText.scrollBy(0, line.top - getTopY())
            }
        }

        /*desired.firstVisibleChar?.let { visible ->
                binding.realText.post {
                    binding.realText.scrollBy(
                        0,
                        (textAdapter.getViewOffset(
                            transformIndexToScrollVisibilityItem(adapterPosition),
                            visible
                        ) ?: 0) + offset
                    )
                }
            }*/

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

    private fun postDesired(view: View) {
        val currentDesired = viewModel.desiredIndex
        view.post {
            viewModel.desiredIndex = currentDesired
            scrollToDesired()
            updateTTSLine(viewModel.ttsLine.value)
        }
    }

    override fun onResume() {
        viewModel.resumedApp()
        super.onResume()
        readingSessionStartTime = System.currentTimeMillis()
    }

    override fun onPause() {
        viewModel.leftApp()
        super.onPause()
        if (readingSessionStartTime != 0L) {
            val sessionTime = System.currentTimeMillis() - readingSessionStartTime
            UsageStatsManager.addReadingTime(this, sessionTime)
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
        val builder =
            AlertDialog.Builder(this, R.style.AlertDialogCustom).setView(R.layout.font_bottom_sheet)

        val dialog = builder.create()
        dialog.show()

        val res = dialog.findViewById<RecyclerView>(R.id.sort_click)!!
        val addButton = dialog.findViewById<android.widget.Button>(R.id.add_custom_font)

        addButton?.setOnClickListener {
            dialog.dismiss()
            selectFontLauncher.launch("*/*")
        }

        val customFontsFolder = java.io.File(filesDir, "fonts")
        val customFonts = if (customFontsFolder.exists()) customFontsFolder.listFiles() ?: emptyArray<java.io.File>() else emptyArray<java.io.File>()
        val fonts = customFonts + systemFonts
        val items = listOf(FontFile(null)) + fonts.map { FontFile(it) }

        val currentName = viewModel.textFont ?: ""
        val storingIndex = items.indexOfFirst { (it.file?.name ?: "") == currentName }

        val adapter = FontAdapter(
            this, 
            storingIndex,
            clickCallback = { file ->
                viewModel.textFont = file.file?.name ?: ""
                dialog.dismiss()
            },
            deleteCallback = { file ->
                file.file?.delete()
                showToast("Font deleted")
                dialog.dismiss()
                showFonts()
            }
        )
        res.adapter = adapter
        adapter.submitIncomparableList(items)
        res.scrollToPosition(storingIndex)
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



    private fun saveReadingTime() {
        if (readStartTime == 0L) return
        val elapsed = System.currentTimeMillis() - readStartTime
        readStartTime = 0L
        if (elapsed < 1000) return

        val currentTotal = getKey<Long>("TOTAL_READING_TIME", 0L) ?: 0L
        setKey("TOTAL_READING_TIME", currentTotal + elapsed)

        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val lastReadDay = getKey<String>("LAST_READ_DAY", "") ?: ""
        val currentStreak = getKey<Int>("CURRENT_STREAK", 0) ?: 0

        if (lastReadDay != today) {
            if (lastReadDay.isNotEmpty()) {
                try {
                    val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    val lastDate = format.parse(lastReadDay)
                    val todayDate = format.parse(today)
                    if (lastDate != null && todayDate != null) {
                        val diff = (todayDate.time - lastDate.time) / (1000 * 60 * 60 * 24)
                        if (diff == 1L) {
                            setKey("CURRENT_STREAK", currentStreak + 1)
                        } else if (diff > 1L) {
                            setKey("CURRENT_STREAK", 1)
                        }
                    }
                } catch (e: Exception) {
                    com.lagradost.quicknovel.mvvm.logError(e)
                }
            } else {
                setKey("CURRENT_STREAK", 1)
            }
            setKey("LAST_READ_DAY", today)
        }
    }

    /*  private fun updateTimeText() {
          val string = if (viewModel.time12H) "hh:mm a" else "HH:mm"

          val currentTime: String = SimpleDateFormat(string, Locale.getDefault()).format(Date())

          binding.readTime.text = currentTime
          binding.readTime.postDelayed({ -> updateTimeText() }, 1000)
      }*/
    private var readStartTime: Long = 0L
    private var topBarHeight by Delegates.notNull<Int>()


    private var currentOverScrollValue = 0.0f

    private fun setProgressOfOverscroll(index: Int, progress: Float) {
        val id = generateId(5, index, 0, 0)
        ((binding.realText.findViewHolderForItemId(id) as? ViewHolderState<*>)?.view as? SingleOverscrollChapterBinding)?.let {
            it.progress.max = 10000
            it.progress.progress = (progress.absoluteValue * 10000.0f).toInt()
            it.progress.alpha = if (progress.absoluteValue > 0.05f) 1.0f else 0.0f
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



    override fun onDestroy() {
        viewModel.stopTTS()
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
        binding = ReadMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateGlobalBackground()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener { _, key ->
                if (key == getString(R.string.background_image_key) ||
                    key == getString(R.string.background_blur_key) ||
                    key == getString(R.string.background_dim_key) ||
                    key == getString(R.string.theme_key) ||
                    key == getString(R.string.reader_background_key)
                ) {
                    updateGlobalBackground()
                }
            }

        registerBattery()
        readingSessionStartTime = System.currentTimeMillis()

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
            val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
            val isEnabled = settingsManager.getBoolean(getString(R.string.reader_background_key), false)
            val imageUri = settingsManager.getString(getString(R.string.background_image_key), null)

            if (isEnabled && !imageUri.isNullOrBlank()) {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
                binding.readOverlay.setBackgroundColor(Color.TRANSPARENT)
            } else {
                binding.root.setBackgroundColor(color)
                binding.readOverlay.setBackgroundColor(color)
            }

            if (textAdapter.changeBackgroundColor(color)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.textVerticalPaddingLive) { padding ->
            if (textAdapter.changeTextVerticalPadding(padding)) {
                updateTextAdapterConfig()
            }
        }


        observe(viewModel.bionicReadingLive) { color ->
            if (textAdapter.changeBionicReading(color)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.isTextSelectableLive) { isTextSelectable ->
            if (textAdapter.changeTextSelectable(isTextSelectable)) {
                updateTextAdapterConfig()
            }
        }

        observe(viewModel.showBatteryLive) { show ->
            binding.readBattery.isVisible = show
            binding.readOverlay.isVisible = show && viewModel.showTime
        }

        observe(viewModel.showTimeLive) { show ->
            binding.readTimeClock.isVisible = show
            binding.readOverlay.isVisible = show && viewModel.showBattery
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

        observe(viewModel.textFontLive) { font ->
            if (textAdapter.changeFont(font)) {
                updateTextAdapterConfig()
            }
        }

        textLayoutManager = LinearLayoutManager(binding.realText.context)

        binding.ttsActionPausePlay.setOnClickListener {
            viewModel.pausePlayTTS()
        }

        binding.ttsActionStop.setOnClickListener {
            viewModel.stopTTS()
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
                        downloadProgressDialog =
                            com.google.android.material.dialog.MaterialAlertDialogBuilder(
                                this,
                                R.style.AlertDialogCustom
                            )
                                .setView(downloadProgressBinding?.root)
                                .setCancelable(false)
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
                    CommonActivity.showToast(this, "Applied successfully", Toast.LENGTH_SHORT)
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

        observe(viewModel.orientationLive) { position ->
            val org = OrientationType.fromSpinner(position)
            requestedOrientation = org.flag
            binding.readActionRotate.setImageResource(org.iconRes)

            binding.readActionRotate.apply {
                setOnClickListener {
                    popupMenu(
                        items = OrientationType.entries.map { it.prefValue to it.stringRes },
                        selectedItemId = org.prefValue
                    ) {
                        viewModel.orientation = itemId
                    }
                }
            }
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
                val builderSingle: AlertDialog.Builder = AlertDialog.Builder(this)
                //builderSingle.setIcon(R.drawable.ic_launcher)
                val currentChapter = viewModel.desiredIndex?.index
                // cant be too safe here
                val validChapter =
                    currentChapter != null && currentChapter >= 0 && currentChapter < titles.size
                if (validChapter) {
                    builderSingle.setTitle(titles[currentChapter!!].asString(this)) //  "Select Chapter"
                } else {
                    builderSingle.setTitle(R.string.select_chapter)
                }

                val arrayAdapter = ArrayAdapter<String>(this, R.layout.chapter_select_dialog)

                arrayAdapter.addAll(titles.map { it.asString(this) })

                builderSingle.setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }

                builderSingle.setAdapter(arrayAdapter) { _, which ->
                    viewModel.seekToChapter(which)
                }

                val dialog = builderSingle.create()
                dialog.show()

                dialog.listView.choiceMode = AbsListView.CHOICE_MODE_SINGLE
                if (validChapter) {
                    dialog.listView.setSelection(currentChapter!!)
                    dialog.listView.setItemChecked(currentChapter, true)
                }
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
                    binding.realText.isVisible = true
                    
                    // Force the toolbar title/subtitle refresh if needed
                    val title = viewModel.book.getChapterTitle(viewModel.currentIndex)
                    if (different) {
                        viewModel.updateReadArea(seekToDesired = false)
                    }

                    if (different) {
                        binding.readNormalLayout.alpha = 0.01f

                        ObjectAnimator.ofFloat(binding.readNormalLayout, "alpha", 1f).apply {
                            duration = 300
                            start()
                        }
                    } else {
                        binding.readNormalLayout.alpha = 1.0f
                    }
                }

                is Resource.Loading -> {
                    binding.readFail.isVisible = false
                    
                    // Chapter loading should use shimmer
                    binding.readLoading.isVisible = false
                    binding.readNormalLayout.isVisible = true 
                    binding.readNormalLayout.alpha = 1f
                    
                    val shimmer = binding.readSkeletonShimmer.root as? ShimmerFrameLayout
                    shimmer?.isVisible = true
                    shimmer?.startShimmer()
                    
                    // Ensure the real text is hidden while shimmering
                    binding.realText.isVisible = false

                    binding.loadingText.apply {
                        isGone = loading.url.isNullOrBlank()
                        text = loading.url ?: ""
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
            layoutManager = textLayoutManager
            adapter = textAdapter
            itemAnimator = null
            // testing overscroll
            setOnTouchListener { _, event ->
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
                    }

                    onScroll()
                    super.onScrolled(recyclerView, dx, dy)

                    // binding.tmpTtsEnd.fixLine((getBottomY()- remainingBottom) + 7.toPx)
                    // binding.tmpTtsStart.fixLine(remainingTop + 7.toPx)
                }
            })
        }

        //here inserted novel chapter text into recyclerview
        observe(viewModel.chapter) { chapter ->
            cachedChapter = chapter.data

            if (chapter.seekToDesired) {
                textAdapter.submitIncomparableList(chapter.data) {
                    viewModel.postLoadingStatus(Resource.Success(""))
                    scrollToDesired()
                    onScroll()
                    UsageStatsManager.incrementChapterRead(this@ReadActivity2)
                }
            } else {
                textAdapter.submitList(chapter.data) {
                    viewModel.postLoadingStatus(Resource.Success(""))
                    onScroll()
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
            val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)

            val binding = ReadBottomSettingsBinding.inflate(layoutInflater, null, false)
            builder.setView(binding.root)
            val bottomSheetDialog = builder.create()
            bottomSheetDialog.show()

            binding.readSettingsCharacterAliases.setOnClickListener {
                showAliasManagementDialog()
            }

            binding.readReadingType.setText(viewModel.readerType.stringRes)
            binding.readReadingType.setOnLongClickListener {
                it.popupMenu(items = listOf(1 to R.string.reset_value), selectedItemId = null) {
                    if (itemId == 1) {
                        binding.readReadingType.setText(ReadingType.DEFAULT.stringRes)
                        viewModel.readerType = ReadingType.DEFAULT
                    }
                }
                return@setOnLongClickListener true
            }
            binding.readReadingType.setOnClickListener {
                it.popupMenu(
                    items = ReadingType.entries.map { v -> v.prefValue to v.stringRes },
                    selectedItemId = viewModel.readerType.prefValue
                ) {
                    val set = ReadingType.fromSpinner(itemId)
                    binding.readReadingType.setText(set.stringRes)
                    viewModel.readerType = set
                }
            }

            binding.readSettingsTextSizeText.setOnClickListener {
                it.popupMenu(items = listOf(1 to R.string.reset_value), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.textSize = DEF_FONT_SIZE
                        binding.readSettingsTextSize.setValueRounded(
                            DEF_FONT_SIZE.toFloat()
                        )
                    }
                }
            }

            binding.readSettingsTextSize.apply {
                valueSuffix = "pt"
                valueTo = 30.0f
                valueFrom = 10.0f
                setValueRounded((viewModel.textSize).toFloat())
                setOnValueChangeListener { _, value, fromUser ->
                    viewModel.textSize = value.roundToInt()
                    UsageStatsManager.incrementCustomization(this@ReadActivity2)
                }
            }

            binding.readSettingsTtsPitchText.setOnClickListener {
                it.popupMenu(
                    items = listOf(1 to R.string.reset_value),
                    selectedItemId = null
                ) {
                    if (itemId == 1) {
                        viewModel.ttsPitch = 1.0f
                        binding.readSettingsTtsPitch.setValueRounded(viewModel.ttsPitch)
                    }
                }
            }

            binding.readSettingsTtsSpeedText.setOnClickListener {
                it.popupMenu(
                    items = listOf(1 to R.string.reset_value),
                    selectedItemId = null
                ) {
                    if (itemId == 1) {
                        viewModel.ttsSpeed = 1.0f
                        binding.readSettingsTtsSpeed.setValueRounded(viewModel.ttsSpeed)
                    }
                }
            }

            binding.readSettingsTextVerticalPaddingText.setOnClickListener {
                it.popupMenu(
                    items = listOf(1 to R.string.reset_value),
                    selectedItemId = null
                ) {
                    if (itemId == 1) {
                        viewModel.textVerticalPadding = 7.5f
                        binding.readSettingsTextVerticalPadding.setValueRounded(viewModel.textVerticalPadding)
                    }
                }
            }

            binding.readSettingsTtsPitch.apply {
                valueSuffix = "x"
                setValueRounded(viewModel.ttsPitch)
                setOnValueChangeListener { _, value, fromUser ->
                    viewModel.ttsPitch = value
                }
            }

            binding.readSettingsTtsSpeed.apply {
                valueSuffix = "x"
                setValueRounded(viewModel.ttsSpeed)
                setOnValueChangeListener { _, value, fromUser ->
                    viewModel.ttsSpeed = value
                }
            }

            binding.readSettingsTextPaddingText.setOnClickListener {
                it.popupMenu(
                    items = listOf(1 to R.string.reset_value),
                    selectedItemId = null
                ) {
                    if (itemId == 1) {
                        viewModel.paddingHorizontal = DEF_HORIZONTAL_PAD
                        binding.readSettingsTextPadding.setValueRounded(DEF_HORIZONTAL_PAD.toFloat())
                    }
                }
            }

            binding.readSettingsTextPaddingTextTop.setOnClickListener {
                it.popupMenu(
                    items = listOf(1 to R.string.reset_value),
                    selectedItemId = null
                ) {
                    if (itemId == 1) {
                        viewModel.paddingVertical = DEF_VERTICAL_PAD
                        binding.readSettingsTextPaddingTop.setValueRounded(DEF_VERTICAL_PAD.toFloat())
                    }
                }
            }

            binding.readSettingsTextPadding.apply {
                valueSuffix = "px"
                valueTo = 50.0f
                setValueRounded(viewModel.paddingHorizontal.toFloat())
                setOnValueChangeListener { _, value, fromUser ->
                    viewModel.paddingHorizontal = value.roundToInt()
                }
            }

            binding.readSettingsTextPaddingTop.apply {
                valueSuffix = "px"
                valueTo = 50.0f
                setValueRounded(viewModel.paddingVertical.toFloat())
                setOnValueChangeListener { _, value, fromUser ->
                    viewModel.paddingVertical = value.roundToInt()
                }
            }

            binding.readSettingsTextVerticalPadding.apply {
                valueSuffix = "px"
                setValueRounded(viewModel.textVerticalPadding)
                setOnValueChangeListener { _, value, fromUser ->
                    viewModel.textVerticalPadding = value
                }
            }


            binding.readSettingsUseGoogle.apply {
                isChecked = viewModel.ttsUseGoogle
                setOnCheckedChangeListener { _, isChecked ->
                    viewModel.ttsUseGoogle = isChecked
                }
            }


            viewModel.ttsUseGoogleLive.observe(this) {
                binding.readSettingsUseGoogle.isChecked = it == true
            }

            binding.readShowFonts.apply {
                //text = UIHelper.parseFontFileName(getKey(EPUB_FONT))
                setOnClickListener {
                    showFonts()
                }
            }

            binding.readSettingsTextFontText.setOnClickListener {
                it.popupMenu(items = listOf(1 to R.string.reset_value), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.textFont = ""
                    }
                }
            }

            binding.readMlTo.setOnClickListener { view ->
                if (view == null) return@setOnClickListener
                val context = view.context

                val items = ReadActivityViewModel.MLSettings.list

                context.showDialog(
                    items.map {
                        it.second
                    },
                    items.map { it.first }.indexOf(viewModel.mlToLanguage),
                    context.getString(R.string.sleep_timer), false, {}
                ) { index ->
                    viewModel.mlToLanguage = items[index].first
                    binding.readMlTo.text =
                        ReadActivityViewModel.MLSettings.fromShortToDisplay(viewModel.mlToLanguage)
                }
            }

            binding.readMlFrom.setOnClickListener { view ->
                if (view == null) return@setOnClickListener
                val context = view.context

                val items = ReadActivityViewModel.MLSettings.list

                context.showDialog(
                    items.map {
                        it.second
                    },
                    items.map { it.first }.indexOf(viewModel.mlFromLanguage),
                    context.getString(R.string.sleep_timer), false, {}
                ) { index ->
                    viewModel.mlFromLanguage = items[index].first
                    binding.readMlFrom.text =
                        ReadActivityViewModel.MLSettings.fromShortToDisplay(viewModel.mlFromLanguage)
                }
            }


            binding.readApplyTranslation.setOnClickListener { _ ->
                viewModel.applyMLSettings(true)
                bottomSheetDialog.dismiss()
            }

            binding.readMlInfoBtn.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ReadActivity2, R.style.AlertDialogCustom)
                    .setTitle(R.string.ml_info_title)
                    .setMessage(R.string.ml_info_text)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }

            binding.readMlTo.text =
                ReadActivityViewModel.MLSettings.fromShortToDisplay(viewModel.mlToLanguage)
            binding.readMlFrom.text =
                ReadActivityViewModel.MLSettings.fromShortToDisplay(viewModel.mlFromLanguage)

            val mlSettings = viewModel.mlSettings

            binding.readMlTitle.text = if (viewModel.isTranslationActive) {
                "${getString(R.string.google_ml)} (${mlSettings.fromDisplay} -> ${mlSettings.toDisplay})"
            } else {
                getString(R.string.google_ml)
            }

            binding.readLanguage.setOnClickListener { _ ->
                ioSafe {
                    viewModel.ttsSession.requireEngine({ tts ->
                        runOnUiThread {
                            val voices = tts.getVoices()
                            val languages = mutableListOf<Locale?>(null).apply {
                                val allLocales = voices.map { it.locale }.distinct().sortedBy { it.displayName }
                                addAll(allLocales)
                            }
                            val currentVoiceName = tts.getCurrentVoiceName()
                            val currentVoice = voices.find { it.name == currentVoiceName }
                            val ctx = binding.readLanguage.context ?: return@runOnUiThread
                            
                            val currentIndex = if (currentVoice != null) languages.indexOf(currentVoice.locale) else 0
                            
                            ctx.showDialog(
                                languages.map {
                                    it?.displayName ?: ctx.getString(R.string.default_text)
                                },
                                currentIndex,
                                ctx.getString(R.string.tts_locale), false, {}
                            ) { index ->
                                viewModel.setTTSVoice(null)
                            }
                        }
                    }, action = { false })
                }
            }

            binding.readLanguage.setOnLongClickListener {
                it.popupMenu(items = listOf(1 to R.string.reset_value), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.setTTSVoice(null)
                    }
                }

                return@setOnLongClickListener true
            }

            binding.readVoice.setOnLongClickListener {
                it.popupMenu(items = listOf(1 to R.string.reset_value), selectedItemId = null) {
                    if (itemId == 1) {
                        viewModel.setTTSVoice(null)
                    }
                }

                return@setOnLongClickListener true
            }

            binding.readSleepTimer.setOnClickListener { view ->
                if (view == null) return@setOnClickListener
                val context = view.context

                val items =
                    mutableListOf(
                        context.getString(R.string.default_text) to 0L,
                    )

                for (i in 1L..120L) {
                    items.add(
                        context.getString(R.string.sleep_format).format(i.toInt()) to (i * 60000L)
                    )
                }

                context.showDialog(
                    items.map {
                        it.first
                    },
                    items.map { it.second }.indexOf(viewModel.ttsTimer),
                    context.getString(R.string.sleep_timer), false, {}
                ) { index ->
                    viewModel.ttsTimer = items[index].second
                }
            }

            binding.readVoice.setOnClickListener {
                ioSafe {
                    viewModel.ttsSession.requireEngine({ tts ->
                        runOnUiThread {
                            val allVoices = tts.getVoices()
                            val currentVoiceName = tts.getCurrentVoiceName()
                            val matchAgainst = allVoices.find { it.name == currentVoiceName }?.locale
                            val ctx = binding.readVoice.context ?: return@runOnUiThread
                            val voices =
                                mutableListOf<Pair<String, EngineVoice?>>(ctx.getString(R.string.default_text) to null).apply {
                                    val filtered = if (matchAgainst == null) {
                                        allVoices
                                    } else {
                                        allVoices.filter { it.locale == matchAgainst }
                                    }

                                    val mapped = filtered.map {
                                        ("${it.name} ${
                                            if (it.isNetworkRequired) {
                                                "(☁)"
                                            } else {
                                                ""
                                            }
                                        }") to it
                                    }

                                    addAll(mapped.sortedBy { (name, _) -> name })
                                }

                            val selectedIndex = if (currentVoiceName != null) {
                                voices.indexOfFirst { it.second?.name == currentVoiceName }.takeIf { it != -1 } ?: 0
                            } else 0

                            ctx.showDialog(
                                voices.map { it.first },
                                selectedIndex,
                                ctx.getString(R.string.tts_locale), false, {}
                            ) { index ->
                                val voice = voices.getOrNull(index)?.second
                                viewModel.setTTSVoice(voice?.name)
                            }
                        }
                    }, action = { false })
                }
            }

            binding.apply {
                hardResetStream.isVisible = viewModel.canReload()
                hardResetStream.setOnClickListener {
                    showToast(getString(R.string.reload_chapter_format).format(""))
                    viewModel.reloadChapter()
                }

                readSettingsScrollVol.isChecked = viewModel.scrollWithVolume
                readSettingsScrollVol.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.scrollWithVolume = isChecked
                }

                readSettingsAuthorNotes.isChecked = viewModel.authorNotes
                readSettingsAuthorNotes.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.authorNotes = isChecked
                    viewModel.refreshChapters()
                }

                readSettingsShowBionic.isChecked = viewModel.bionicReading
                readSettingsShowBionic.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.bionicReading = isChecked
                }

                readSettingsIsTextSelectable.isChecked = viewModel.isTextSelectable
                readSettingsIsTextSelectable.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.isTextSelectable = isChecked
                }

                readSettingsLockTts.isChecked = viewModel.ttsLock
                readSettingsLockTts.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.ttsLock = isChecked
                }

                readSettingsShowTime.isChecked = viewModel.showTime
                readSettingsShowTime.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.showTime = isChecked
                }

                //readSettingsTwelveHourTime.isChecked = viewModel.time12H
                //readSettingsTwelveHourTime.setOnCheckedChangeListener { _, isChecked ->
                //    viewModel.time12H = isChecked
                //}

                readSettingsShowBattery.isChecked = viewModel.showBattery
                readSettingsShowBattery.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.showBattery = isChecked
                }

                readSettingsKeepScreenActive.isChecked = viewModel.screenAwake
                readSettingsKeepScreenActive.setOnCheckedChangeListener { _, isChecked ->
                    viewModel.screenAwake = isChecked
                }

            }

            val bgColors = resources.getIntArray(R.array.readerBgColors)
            val textColors = resources.getIntArray(R.array.readerTextColors)

            imageHolder = binding.readSettingsColors
            for ((newBgColor, newTextColor) in bgColors zip textColors) {
                ColorRoundCheckmarkBinding.inflate(
                    layoutInflater,
                    binding.readSettingsColors,
                    true
                ).image1.apply {
                    backgroundTintList = ColorStateList.valueOf(newBgColor)
                    //foregroundTintList = ColorStateList.valueOf(newTextColor)
                    setOnClickListener {
                        viewModel.backgroundColor = newBgColor
                        viewModel.textColor = newTextColor
                        updateImages()
                    }
                }
            }

            ColorRoundCheckmarkBinding.inflate(
                layoutInflater,
                binding.readSettingsColors,
                true
            ).image1.apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    foreground =
                        ContextCompat.getDrawable(this.context, R.drawable.ic_baseline_add_24)
                }

                setOnClickListener {
                    val builder: AlertDialog.Builder = AlertDialog.Builder(this.context)
                    builder.setTitle(getString(R.string.reading_color))

                    val colorAdapter =
                        ArrayAdapter<String>(this.context, R.layout.chapter_select_dialog)
                    val array = arrayListOf(
                        getString(R.string.background_color),
                        getString(R.string.text_color)
                    )
                    colorAdapter.addAll(array)

                    builder.setPositiveButton(R.string.ok) { dialog, _ ->
                        dialog.dismiss()
                        updateImages()
                    }

                    builder.setAdapter(colorAdapter) { _, which ->
                        ColorPickerDialog.newBuilder()
                            .setDialogId(which)
                            .setColor(
                                when (which) {
                                    0 -> viewModel.backgroundColor
                                    1 -> viewModel.textColor
                                    else -> 0
                                }
                            )
                            .show(readActivity ?: return@setAdapter)
                    }

                    builder.show()
                    updateImages()
                }
            }
            updateImages()

            bottomSheetDialog.show()
        }
    }

    private fun showAliasManagementDialog() {
        val currentAliases = viewModel.aliases.value ?: emptyMap()
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)
        builder.setTitle(R.string.character_aliases)

        if (currentAliases.isEmpty()) {
            builder.setMessage(R.string.no_aliases)
        } else {
            val items = currentAliases.keys.toTypedArray()
            val displayItems = items.map { "$it -> ${currentAliases[it]}" }.toTypedArray()
            val adapter = object : ArrayAdapter<String>(this, android.R.layout.select_dialog_item, displayItems) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent) as TextView
                    view.setTextColor(viewModel.textColor)
                    return view
                }
            }
            builder.setAdapter(adapter) { _, which ->
                val key = items[which]
                val options = arrayOf(getString(R.string.edit_alias), getString(R.string.delete_alias))
                val optAdapter = object : ArrayAdapter<String>(this@ReadActivity2, android.R.layout.select_dialog_item, options) {
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val view = super.getView(position, convertView, parent) as TextView
                        view.setTextColor(viewModel.textColor)
                        return view
                    }
                }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)
                    .setAdapter(optAdapter) { _, optionIdx ->
                        if (optionIdx == 0) {
                            showAddAliasDialog(key, currentAliases[key])
                        } else {
                            viewModel.removeAlias(key)
                            showAliasManagementDialog()
                        }
                    }.show()
            }
        }

        builder.setPositiveButton(R.string.add_alias) { _, _ ->
            showAddAliasDialog()
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }

    private fun showAddAliasDialog(editKey: String? = null, editValue: String? = null) {
        val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.AlertDialogCustom)
        builder.setTitle(if (editKey == null) R.string.add_alias else R.string.edit_alias)

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_alias, null)
        val originalInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.original_input)
        val replacementInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.replacement_input)

        originalInput.setText(editKey)
        originalInput.setTextColor(viewModel.textColor)
        replacementInput.setText(editValue)
        replacementInput.setTextColor(viewModel.textColor)

        builder.setView(dialogView)

        builder.setPositiveButton(android.R.string.ok) { _, _ ->
            val original = originalInput.text.toString().trim()
            val replacement = replacementInput.text.toString().trim()
            if (original.isNotEmpty() && replacement.isNotEmpty()) {
                if (editKey != null && editKey != original) {
                    viewModel.removeAlias(editKey)
                }
                viewModel.addAlias(original, replacement)
                showAliasManagementDialog()
            }
        }
        builder.setNegativeButton(android.R.string.cancel, null)
        builder.show()
    }
}