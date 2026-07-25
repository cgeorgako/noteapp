package com.example.locknote

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Quick Settings tile. Reachable from the lock-screen shade — tapping it opens
 * the drawing pad directly over the lock screen, no unlock required.
 */
class NoteTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, LockNoteActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        // startActivityAndCollapse unlocks the shade and shows our activity;
        // because the activity is showWhenLocked, it appears over the keyguard.
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
