package com.example.locknote

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * The drawing pad. Declared with showWhenLocked/turnScreenOn in the manifest,
 * so it appears over the lock screen and wakes the display without an unlock.
 */
class LockNoteActivity : AppCompatActivity() {

    private lateinit var drawing: DrawingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Belt-and-suspenders for pre-O_MR1 and to keep the screen awake.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_lock_note)
        drawing = findViewById(R.id.drawingView)

        findViewById<ImageButton>(R.id.btnUndo).setOnClickListener { drawing.undo() }
        findViewById<ImageButton>(R.id.btnRedo).setOnClickListener { drawing.redo() }
        findViewById<ImageButton>(R.id.btnClear).setOnClickListener { drawing.clear() }
        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnSave).setOnClickListener { saveAndClose() }
    }

    private fun saveAndClose() {
        if (drawing.isEmpty()) {
            Toast.makeText(this, R.string.nothing_to_save, Toast.LENGTH_SHORT).show()
            return
        }
        val bmp = drawing.exportBitmap()
        NoteStore.save(this, bmp)
        Toast.makeText(this, R.string.note_saved, Toast.LENGTH_SHORT).show()
        drawing.clear()
        finish()
    }
}
