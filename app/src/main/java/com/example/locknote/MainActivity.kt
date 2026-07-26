package com.example.locknote

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
        findViewById<Button>(R.id.btnAddTile).setOnClickListener { addQuickTile() }
        findViewById<Button>(R.id.btnLockSetup).setOnClickListener { showLockScreenSetup() }
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            NoteService.start(this)
        }
        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            NoteService.stop(this)
        }
    }

    /** One-tap "add tile" system dialog (Android 13+), else guided instructions. */
    private fun addQuickTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this, NoteTileService::class.java),
                    getString(R.string.tile_label),
                    Icon.createWithResource(this, R.drawable.ic_note),
                    { it.run() },
                    { /* result code ignored */ }
                )
            }.onFailure {
                Toast.makeText(this, R.string.add_tile_manual, Toast.LENGTH_LONG).show()
            }
        } else {
            AlertDialog.Builder(this)
                .setTitle(R.string.add_tile)
                .setMessage(R.string.add_tile_manual)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    /** No API can add a lock-screen shortcut; guide the user and open Settings. */
    private fun showLockScreenSetup() {
        AlertDialog.Builder(this)
            .setTitle(R.string.lock_setup)
            .setMessage(R.string.lock_setup_steps)
            .setPositiveButton(R.string.open_settings) { _, _ -> openLockSettings() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openLockSettings() {
        // Try known lock-screen settings actions, fall back to the app's settings.
        val candidates = listOf(
            "com.samsung.android.settings.LOCKSCREEN_SETTINGS",
            "android.settings.LOCK_SCREEN_SETTINGS",
            Settings.ACTION_SECURITY_SETTINGS
        )
        for (action in candidates) {
            val intent = Intent(action)
            if (intent.resolveActivity(packageManager) != null) {
                runCatching { startActivity(intent); return }
            }
        }
        runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure { Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show() }
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
