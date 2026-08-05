package de.wunstorf.schulevault.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.wunstorf.schulevault.MainActivity
import de.wunstorf.schulevault.R

/**
 * Fuehrt genau EINE geplante Erinnerung aus. Wird von NotificationScheduler
 * als OneTimeWorkRequest mit passendem initialDelay eingeplant - je Termin
 * und Erinnerungsstufe (7T/3T/24h) ein eigener Worker-Aufruf.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_TITEL = "titel"
        const val KEY_LABEL_TEXT = "label_text"
        const val CHANNEL_ID = "termin_erinnerungen"
    }

    override suspend fun doWork(): Result {
        val titel = inputData.getString(KEY_TITEL) ?: return Result.failure()
        val labelText = inputData.getString(KEY_LABEL_TEXT) ?: ""

        sicherstellenChannelExistiert()
        zeigeBenachrichtigung(titel, labelText)
        return Result.success()
    }

    private fun sicherstellenChannelExistiert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Termin-Erinnerungen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Erinnerungen 7 Tage, 3 Tage und 24 Stunden vor einem Termin"
            }
            val manager = applicationContext.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun zeigeBenachrichtigung(titel: String, labelText: String) {
        val openAppIntent = android.content.Intent(applicationContext, MainActivity::class.java)
        val pendingIntent = androidx.core.app.TaskStackBuilder.create(applicationContext).run {
            addNextIntentWithParentStack(openAppIntent)
            getPendingIntent(
                titel.hashCode(),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titel)
            .setContentText("Termin $labelText")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Ab Android 13 (API 33) ist POST_NOTIFICATIONS eine Laufzeit-
        // Berechtigung - ohne sie darf notify() gar nicht erst aufgerufen
        // werden (sonst SecurityException).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.NotificationManagerCompat.from(applicationContext)
                .notify(titel.hashCode() + labelText.hashCode(), notification)
        }
    }
}
