package com.example.locknote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val requestNotif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            NoteService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NoteService.ensureChannel(this)
        ensureNotificationPermissionThenStart()

        findViewById<Button>(R.id.btnOpenPad).setOnClickListener {
            startActivity(Intent(this, LockNoteActivity::class.java))
        }
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            NoteService.start(this)
        }
        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            NoteService.stop(this)
        }
    }

    private fun ensureNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                NoteService.start(this)
            } else {
                requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            NoteService.start(this)
        }
    }
}
