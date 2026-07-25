package com.example.locknote

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The drawing pad. Declared with showWhenLocked/turnScreenOn in the manifest so
 * it appears over the lock screen and wakes the display without an unlock.
 */
class LockNoteActivity : AppCompatActivity() {

    private lateinit var drawing: DrawingView
    private lateinit var btnPen: ImageButton
    private lateinit var btnEraser: ImageButton

    private val padBg = Color.rgb(22, 22, 26)

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
        drawing = findViewById(R.id.drawingView)
        btnPen = findViewById(R.id.btnPen)
        btnEraser = findViewById(R.id.btnEraser)

        btnPen.setOnClickListener { setEraser(false) }
        btnEraser.setOnClickListener { setEraser(true) }
        findViewById<ImageButton>(R.id.btnSize).setOnClickListener { showSizeDialog() }
        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { drawing.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { drawing.redo() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<ImageButton>(R.id.btnLock).setOnClickListener { setAsLockWallpaper() }
        findViewById<ImageButton>(R.id.btnPdf).setOnClickListener { exportPdf(share = false) }
        findViewById<ImageButton>(R.id.btnShare).setOnClickListener { exportPdf(share = true) }
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

        setEraser(false)
        restoreDrawing()
    }

    override fun onPause() {
        super.onPause()
        persistDrawing()
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
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
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
            FileOutputStream(currentFile()).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun restoreDrawing() {
        val f = currentFile()
        if (!f.exists()) return
        runCatching { BitmapFactory.decodeFile(f.absolutePath) }
            .getOrNull()?.let { drawing.setBaselineBitmap(it) }
    }

    // --- Lock-screen wallpaper --------------------------------------------

    private fun setAsLockWallpaper() {
        if (drawing.isEmpty()) {
            toast(R.string.nothing_to_save); return
        }
        val bmp = drawing.exportBitmap(padBg)
        val ok = runCatching {
            val wm = WallpaperManager.getInstance(this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_LOCK)
            } else {
                @Suppress("DEPRECATION")
                wm.setBitmap(bmp)
            }
        }.isSuccess
        toast(if (ok) R.string.lock_set else R.string.lock_failed)
    }

    // --- PDF export & share -----------------------------------------------

    private fun exportPdf(share: Boolean) {
        if (drawing.isEmpty()) {
            toast(R.string.nothing_to_save); return
        }
        val bmp = drawing.exportBitmap(padBg)
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, 1).create()
        val page = doc.startPage(pageInfo)
        page.canvas.drawBitmap(bmp, 0f, 0f, null)
        doc.finishPage(page)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "LockNote_$stamp.pdf")
        val saved = runCatching {
            FileOutputStream(file).use { doc.writeTo(it) }
        }.isSuccess
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

    // --- helpers -----------------------------------------------------------

    private fun toast(resId: Int) = Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
