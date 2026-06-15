package com.lagradost.quicknovel

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.quicknovel.ui.pdf.PdfViewerScreen
import com.lagradost.quicknovel.ui.theme.QuickNovelTheme

class PdfActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val path = intent.getStringExtra("path")
        val title = intent.getStringExtra("title") ?: "PDF Reader"

        if (path != null) {
            setContent {
                QuickNovelTheme {
                    PdfViewerScreen(
                        pdfPath = path,
                        title = title,
                        onNavigateBack = { finish() }
                    )
                }
            }
        } else {
            com.lagradost.quicknovel.CommonActivity.showToast("Invalid PDF Path")
            finish()
        }
    }
}
