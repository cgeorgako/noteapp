package com.example.locknote

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The quick-note pad. You write on a paper-like sheet; when you leave the pad
 * the note is stamped automatically onto the lock screen as a "sticky note"
 * card over your current wallpaper (WallpaperManager / FLAG_LOCK).
 *
 * Declared with showWhenLocked/turnScreenOn so it appears over the keyguard
 * without an unlock.
 */
class LockNoteActivity : AppCompatActivity() {

    private lateinit var drawing: DrawingView
    private lateinit var btnPen: ImageButton
    private lateinit var btnEraser: ImageButton

    private var paperColor: Int = Color.rgb(255, 247, 194)
    private var inkColor: Int = Color.rgb(36, 26, 14)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_lock_note)
        paperColor = ContextCompat.getColor(this, R.color.note_paper)
        inkColor = ContextCompat.getColor(this, R.color.note_ink)

        drawing = findViewById(R.id.drawingView)
        drawing.strokeColor = inkColor
        btnPen = findViewById(R.id.btnPen)
        btnEraser = findViewById(R.id.btnEraser)

        btnPen.setOnClickListener { setEraser(false) }
        btnEraser.setOnClickListener { setEraser(true) }
        findViewById<ImageButton>(R.id.btnSize).setOnClickListener { showSizeDialog() }
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { drawing.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { drawing.redo() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<ImageButton>(R.id.btnPdf).setOnClickListener { exportPdf(share = false) }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { exportPdf(share = true) }
        // "Done" just closes; the note is applied to the lock screen in onPause.
        findViewById<ImageButton>(R.id.btnDone).setOnClickListener { finish() }

        setEraser(false)
        restoreDrawing()
    }

    override fun onPause() {
        super.onPause()
        persistDrawing()
        // Leaving the pad (Done / Back / swipe) → stamp it on the lock screen.
        if (isFinishing && !drawing.isEmpty()) {
            val note = drawing.exportBitmap(paperColor)
            applyToLockScreen(note)
        }
    }

    // --- Tools -------------------------------------------------------------

    private fun setEraser(eraser: Boolean) {
        drawing.isEraser = eraser
        btnPen.alpha = if (eraser) 0.4f else 1f
        btnEraser.alpha = if (eraser) 1f else 0.4f
    }

    private fun showSizeDialog() {
        val label = TextView(this).apply {
            setPadding(48, 40, 48, 8)
            textSize = 16f
        }
        val seek = SeekBar(this).apply {
            max = 60
            progress = drawing.strokeWidth.toInt().coerceIn(2, 60)
        }
        label.text = getString(R.string.size_value, seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                label.text = getString(R.string.size_value, p.coerceAtLeast(2))
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(seek)
        }
        AlertDialog.Builder(this)
            .setTitle(if (drawing.isEraser) R.string.eraser_size else R.string.pen_size)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                drawing.strokeWidth = seek.progress.coerceAtLeast(2).toFloat()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setMessage(R.string.confirm_clear)
            .setPositiveButton(R.string.clear) { _, _ ->
                drawing.clear()
                currentFile().delete()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Persistence (survives app kill / reboot) --------------------------

    private fun currentFile(): File = File(filesDir, "current.png")

    private fun persistDrawing() {
        val bmp = drawing.snapshot() ?: return
        runCatching {
            FileOutputStream(currentFile()).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun restoreDrawing() {
        val f = currentFile()
        if (!f.exists()) return
        runCatching { BitmapFactory.decodeFile(f.absolutePath) }
            .getOrNull()?.let { drawing.setBaselineBitmap(it) }
    }

    // --- Lock-screen "sticky note" ----------------------------------------

    private fun applyToLockScreen(note: Bitmap) {
        val ctx = applicationContext
        val (w, h) = screenSize()
        Thread {
            val lockImage = buildLockImage(ctx, note, w, h)
            val ok = runCatching {
                val wm = WallpaperManager.getInstance(ctx)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    wm.setBitmap(lockImage, null, true, WallpaperManager.FLAG_LOCK)
                } else {
                    @Suppress("DEPRECATION")
                    wm.setBitmap(lockImage)
                }
            }.isSuccess
            runOnUiThread {
                Toast.makeText(ctx, if (ok) R.string.lock_set else R.string.lock_failed, Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    /** Composite: current wallpaper (or a soft backdrop) + the note as a card. */
    private fun buildLockImage(ctx: android.content.Context, note: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)

        val wall = currentWallpaper(ctx)
        if (wall != null) {
            drawCoverCrop(c, wall, w, h)
        } else {
            val p = Paint().apply {
                shader = android.graphics.LinearGradient(
                    0f, 0f, 0f, h.toFloat(),
                    Color.rgb(38, 40, 54), Color.rgb(18, 18, 26),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        // Dim a touch so the note card stands out.
        c.drawColor(0x22000000)

        // Card geometry: keep the note's aspect ratio, cap the height.
        val margin = w * 0.10f
        val cardW = w - 2f * margin
        val aspect = note.height.toFloat() / note.width.toFloat()
        var finalH = cardW * aspect
        val maxH = h * 0.60f
        var finalW = cardW
        if (finalH > maxH) {
            finalH = maxH
            finalW = finalH / aspect
        }
        val left = (w - finalW) / 2f
        // Horizontally centred, but sitting in the lower-centre so the note
        // stays clear of the lock-screen clock at the top.
        val bottomMargin = h * 0.10f
        val minTop = h * 0.30f          // keep below the clock area
        val maxTop = h - bottomMargin - finalH
        val top = (h * 0.60f - finalH / 2f).coerceIn(minTop, maxTop.coerceAtLeast(minTop))
        val rect = RectF(left, top, left + finalW, top + finalH)
        val radius = w * 0.045f

        // Drop shadow.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = paperColor
            setShadowLayer(w * 0.03f, 0f, w * 0.012f, 0x66000000)
        }
        c.drawRoundRect(rect, radius, radius, shadow)

        // Clip to the rounded card and draw the note inside it.
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        c.drawBitmap(note, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restore()
        return out
    }

    private fun currentWallpaper(ctx: android.content.Context): Bitmap? = runCatching {
        val wpm = WallpaperManager.getInstance(ctx)
        val fromLock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            wpm.getWallpaperFile(WallpaperManager.FLAG_LOCK)?.use {
                BitmapFactory.decodeFileDescriptor(it.fileDescriptor)
            }
        } else null
        fromLock ?: (wpm.drawable as? BitmapDrawable)?.bitmap
    }.getOrNull()

    private fun drawCoverCrop(c: Canvas, src: Bitmap, w: Int, h: Int) {
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = src.width * scale
        val dh = src.height * scale
        val dst = RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        c.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = windowManager.currentWindowMetrics.bounds
        b.width() to b.height()
    } else {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        dm.widthPixels to dm.heightPixels
    }

    private fun max(a: Float, b: Float): Float = if (a > b) a else b

    // --- PDF export & share -----------------------------------------------

    private fun exportPdf(share: Boolean) {
        if (drawing.isEmpty()) {
            toast(R.string.nothing_to_save); return
        }
        val bmp = drawing.exportBitmap(paperColor)
        val doc = android.graphics.pdf.PdfDocument()
        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create()
        val page = doc.startPage(pageInfo)
        page.canvas.drawBitmap(bmp, 0f, 0f, null)
        doc.finishPage(page)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "LockNote_$stamp.pdf")
        val saved = runCatching { FileOutputStream(file).use { doc.writeTo(it) } }.isSuccess
        doc.close()
        if (!saved) { toast(R.string.pdf_failed); return }

        if (share) {
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name))
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(send, getString(R.string.share_via)))
        } else {
            toast(getString(R.string.pdf_saved, file.name))
        }
    }

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
