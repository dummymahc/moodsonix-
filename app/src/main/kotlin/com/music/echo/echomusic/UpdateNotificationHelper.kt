package iad1tya.echo.music.echomusic

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import iad1tya.echo.music.R

object UpdateNotificationHelper {
    private const val CHANNEL_ID = "updates"
    private const val NOTIFICATION_ID = 1001

    fun showUpdateNotification(context: Context, versionName: String) {
        // Notification blocked: this function intentionally does nothing so update
        // notifications / "new version available" messages are suppressed.
        // It was changed to a no-op to "block that message" as requested.
        return
    }
}
