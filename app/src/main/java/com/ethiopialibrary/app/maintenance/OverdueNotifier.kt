package com.ethiopialibrary.app.maintenance

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
import com.ethiopialibrary.app.MainActivity
import com.ethiopialibrary.app.R

/**
 * Posts the daily "overdue books" reminder. Pure Android glue (channel,
 * runtime permission, PendingIntent) kept out of MaintenanceManager so the
 * overdue-count logic stays unit-testable. Tapping the notification opens the
 * dashboard, where the overdue list already lives.
 */
object OverdueNotifier {
    const val CHANNEL_ID = "overdue"
    private const val NOTIFICATION_ID = 1001

    fun notifyOverdue(context: Context, count: Int) {
        if (count <= 0) return
        ensureChannel(context)

        // On Android 13+ posting requires a granted runtime permission; if the
        // staff haven't granted it yet, skip quietly rather than crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_overdue)
            .setContentTitle(context.getString(R.string.overdue_notif_title))
            .setContentText(context.getString(R.string.overdue_notif_text, count))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    // minSdk 26, so a channel always exists; creating it again is a no-op.
    private fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.overdue_notif_title),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
