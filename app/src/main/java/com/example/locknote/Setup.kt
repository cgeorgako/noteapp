package com.example.locknote

import android.app.Activity
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Shared helpers for wiring up Quick Settings / lock-screen access. */
object Setup {

    /** One-tap "add tile" system dialog (Android 13+), else guided instructions. */
    fun addQuickTile(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                activity.getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(activity, NoteTileService::class.java),
                    activity.getString(R.string.tile_label),
                    Icon.createWithResource(activity, R.drawable.ic_note),
                    { it.run() },
                    { /* result code ignored */ }
                )
            }.onFailure {
                Toast.makeText(activity, R.string.add_tile_manual, Toast.LENGTH_LONG).show()
            }
        } else {
            AlertDialog.Builder(activity)
                .setTitle(R.string.add_tile)
                .setMessage(R.string.add_tile_manual)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    /** No API can add a lock-screen shortcut; guide the user and open Settings. */
    fun showLockScreenSetup(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.lock_setup)
            .setMessage(R.string.lock_setup_steps)
            .setPositiveButton(R.string.open_settings) { _, _ -> openLockSettings(activity) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun openLockSettings(activity: Activity) {
        val candidates = listOf(
            "com.samsung.android.settings.LOCKSCREEN_SETTINGS",
            "android.settings.LOCK_SCREEN_SETTINGS",
            Settings.ACTION_SECURITY_SETTINGS
        )
        for (action in candidates) {
            val intent = Intent(action)
            if (intent.resolveActivity(activity.packageManager) != null) {
                if (runCatching { activity.startActivity(intent) }.isSuccess) return
            }
        }
        runCatching { activity.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure { Toast.makeText(activity, R.string.settings_unavailable, Toast.LENGTH_SHORT).show() }
    }
}
