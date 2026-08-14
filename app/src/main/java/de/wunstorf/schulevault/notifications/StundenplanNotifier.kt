package de.wunstorf.schulevault.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import de.wunstorf.schulevault.MainActivity
import de.wunstorf.schulevault.R
import de.wunstorf.schulevault.data.Wochentag

/**
 * Push-Benachrichtigung, wenn sich der WebUntis-synchronisierte Stundenplan
 * gegenueber dem zuletzt gespeicherten Stand aendert (z. B. Vertretung,
 * neue/entfallene Stunde) - wird sowohl vom taeglichen Hintergrund-Sync
 * (TagesSyncWorker) als auch vom manuellen Sync in den Einstellungen
 * ausgeloest.
 */
object StundenplanNotifier {

    private const val CHANNEL_ID = "stundenplan_aenderungen"

    fun benachrichtigeBeiAenderung(context: Context, geaenderteTage: List<Wochentag>) {
        if (geaenderteTage.isEmpty()) return
        sicherstellenChannelExistiert(context)

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = TaskStackBuilder.create(context).run {
            addNextIntentWithParentStack(openAppIntent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Stundenplan aktualisiert")
            .setContentText("Geänderte Tage: ${geaenderteTage.joinToString(", ") { it.anzeigeText }}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context).notify(CHANNEL_ID.hashCode(), notification)
        }
    }

    private fun sicherstellenChannelExistiert(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stundenplan-Änderungen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigt, wenn sich dein synchronisierter Stundenplan ändert"
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
