package com.lagradost.quicknovel

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import com.lagradost.quicknovel.databinding.SortBottomSingleChoiceBinding
import com.lagradost.quicknovel.ui.NoStateAdapter
import com.lagradost.quicknovel.ui.ViewHolderState
import com.lagradost.quicknovel.util.UIHelper.parseFontFileName
import com.lagradost.quicknovel.util.UIHelper.popupMenuCustom
import java.io.File

data class FontFile(
    val file : File?
)

class FontAdapter(
    val context: Context, 
    private val checked: Int?, 
    val clickCallback : (FontFile) -> Unit,
    val deleteCallback : (FontFile) -> Unit
) : NoStateAdapter<FontFile>() {
    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        return ViewHolderState(
            com.lagradost.quicknovel.databinding.SortBottomSingleChoiceBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, FontFileItem: FontFile, position: Int) {
        val binding = holder.view as? com.lagradost.quicknovel.databinding.SortBottomSingleChoiceBinding ?: return
        val font = FontFileItem.file

        binding.text1.text = parseFontFileName(font?.name)
        binding.text1.isActivated = position == checked
        if (font != null) {
            try {
                binding.text1.typeface = Typeface.createFromFile(font)
            } catch (t: Throwable) {
                com.lagradost.quicknovel.mvvm.logError(t)
            }
        }
        binding.text1.setOnClickListener {
            this.clickCallback.invoke(FontFileItem)
        }

        binding.text1.setOnLongClickListener { view ->
            if (font != null) {
                val customFolder = java.io.File(context.filesDir, "fonts")
                val isCustom = font.parentFile?.absolutePath == customFolder.absolutePath
                if (isCustom) {
                    view.popupMenuCustom(
                        items = listOf(1 to "Delete"),
                        selectedItemId = -1
                    ) { menuItem ->
                        if (menuItem.itemId == 1) {
                            deleteCallback.invoke(FontFileItem)
                        }
                    }
                }
            }
            true
        }
    }
}