package com.lagradost.quicknovel

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.quicknovel.databinding.ActivityPdfViewerBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class PdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfViewerBinding
    private var pdfRenderer: PdfRenderer? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val path = intent.getStringExtra("path")
        val title = intent.getStringExtra("title") ?: "PDF Reader"
        supportActionBar?.title = title

        if (path != null) {
            setupPdf(path)
            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                "Tip: Click the top-right 3-dots to open with your favorite PDF viewer app.",
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show()
        } else {
            com.lagradost.quicknovel.CommonActivity.showToast("Invalid PDF Path")
            finish()
        }
    }

    private fun setupPdf(path: String) {
        try {
            val file = File(path)
            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(parcelFileDescriptor!!)

            val adapter = PdfAdapter(pdfRenderer!!)
            binding.pdfRecycler.layoutManager = LinearLayoutManager(this)
            binding.pdfRecycler.adapter = adapter

            // Implement Page Snapping so with scrolling feels like a book
            val snapHelper = androidx.recyclerview.widget.PagerSnapHelper()
            snapHelper.attachToRecyclerView(binding.pdfRecycler)

            // Setup Page Scroller Slider
            val totalPages = pdfRenderer!!.pageCount
            binding.pdfPageSlider.max = (totalPages - 1).coerceAtLeast(0)
            binding.pdfPageTxt.text = "1 / $totalPages" // Initial state

            binding.pdfPageSlider.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.pdfPageTxt.text = "${progress + 1} / $totalPages"
                    if (fromUser) {
                        binding.pdfRecycler.scrollToPosition(progress)
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            })

            binding.pdfRecycler.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager
                    val position = layoutManager.findFirstVisibleItemPosition()
                    if (position != androidx.recyclerview.widget.RecyclerView.NO_POSITION) {
                        binding.pdfPageSlider.progress = position
                        binding.pdfPageTxt.text = "${position + 1} / $totalPages"
                    }
                }
            })

        } catch (e: Exception) {
            com.lagradost.quicknovel.mvvm.logError(e)
            com.lagradost.quicknovel.CommonActivity.showToast("Failed to load PDF: ${e.message}")
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        } catch (e: Exception) {
            // Smooth shutdown
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.pdf_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_open_with) {
            val path = intent.getStringExtra("path")
            if (path != null) {
                openExternalPdf(path)
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun openExternalPdf(path: String) {
        try {
            val file = java.io.File(path)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val viewIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(viewIntent, "Open with"))
        } catch (e: Exception) {
            com.lagradost.quicknovel.CommonActivity.showToast("No app found to open PDF")
        }
    }

    data class PdfPage(val bitmap: Bitmap, val isPhoto: Boolean)

    private class PdfAdapter(private val renderer: PdfRenderer) : RecyclerView.Adapter<PdfAdapter.PageViewHolder>() {

        private val renderMutex = Mutex()
        private val pageCache = android.util.LruCache<Int, PdfPage>(15)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return PageViewHolder(view)
        }

        override fun getItemCount(): Int = renderer.pageCount

        override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
            holder.renderJob?.cancel()
            holder.progressBar.visibility = View.VISIBLE
            holder.imageView.setImageBitmap(null)
            holder.imageView.resetScale() 

            // Disable hardware layer to test if memory is capping GPU buffers

            val isDarkMode = (holder.itemView.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

            val cached = pageCache.get(position)
            if (cached != null) {
                holder.progressBar.visibility = View.GONE
                
                if (isDarkMode && !cached.isPhoto) {
                    applyInversion(holder.imageView)
                } else {
                    holder.imageView.colorFilter = null
                }
                
                holder.imageView.setImageBitmap(cached.bitmap)
                return
            }

            holder.renderJob = CoroutineScope(Dispatchers.Main).launch {
                val pageData = withContext(Dispatchers.IO) {
                    renderMutex.withLock {
                        try {
                            val page = renderer.openPage(position)
                            val width = holder.itemView.context.resources.displayMetrics.widthPixels
                            val height = (page.height.toFloat() / page.width.toFloat() * width).toInt()

                            // Render at native width to avoid OOM or Texture size crashes
                            val scale = 1.0f
                            val upscaleWidth = (width * scale).toInt()
                            val upscaleHeight = (height * scale).toInt()

                            val bmp = Bitmap.createBitmap(upscaleWidth, upscaleHeight, Bitmap.Config.ARGB_8888)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            page.close()
                            
                            val isPhoto = isPhotoPage(bmp)
                            PdfPage(bmp, isPhoto)
                        } catch (e: Exception) {
                            com.lagradost.quicknovel.mvvm.logError(e)
                            null
                        }
                    }
                }

                if (isActive) {
                    holder.progressBar.visibility = View.GONE
                    if (pageData != null) {
                        pageCache.put(position, pageData)
                        
                        if (isDarkMode && !pageData.isPhoto) {
                            applyInversion(holder.imageView)
                        } else {
                            holder.imageView.colorFilter = null
                        }
                        holder.imageView.setImageBitmap(pageData.bitmap)
                    }
                }
            }
        }

        private fun applyInversion(imageView: com.lagradost.quicknovel.ZoomableImageView) {
            val matrix = android.graphics.ColorMatrix(floatArrayOf(
                -1.0f, 0.0f, 0.0f, 0.0f, 255.0f,
                0.0f, -1.0f, 0.0f, 0.0f, 255.0f,
                0.0f, 0.0f, -1.0f, 0.0f, 255.0f,
                0.0f, 0.0f, 0.0f, 1.0f, 0.0f
            ))
            imageView.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
        }

        private fun isPhotoPage(bitmap: Bitmap): Boolean {
            val width = bitmap.width
            val height = bitmap.height
            var colorSampleCount = 0
            val totalSamples = 100 
            
            val stepX = (width / 10).coerceAtLeast(1)
            val stepY = (height / 10).coerceAtLeast(1)

            for (x in 0 until width step stepX) {
                for (y in 0 until height step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)

                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    if ((max - min) > 25) { 
                        colorSampleCount++
                    }
                }
            }
            return (colorSampleCount.toFloat() / totalSamples.toFloat()) > 0.15f
        }

        override fun onViewRecycled(holder: PageViewHolder) {
            super.onViewRecycled(holder)
            holder.renderJob?.cancel()
            holder.imageView.setImageBitmap(null)
            holder.imageView.resetScale()
        }

        class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val imageView: com.lagradost.quicknovel.ZoomableImageView = view.findViewById(R.id.pdf_page_image)
            val progressBar: ProgressBar = view.findViewById(R.id.pdf_page_progress)
            var renderJob: Job? = null
        }
    }
}
