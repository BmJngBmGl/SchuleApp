package de.wunstorf.schulevault.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.wunstorf.schulevault.data.Termin
import kotlinx.datetime.Clock
import java.util.concurrent.TimeUnit

/**
 * Plant fuer einen einzelnen Termin die (bis zu) drei Erinnerungen ueber
 * WorkManager ein. Liegt ein Zeitpunkt bereits in der Vergangenheit (z. B.
 * weil der Termin selbst schon in weniger als 7 Tagen ist), wird nur diese
 * eine Stufe uebersprungen statt eines Fehlers - das ist gewuenschtes
 * Verhalten, kein Bug.
 */
object NotificationScheduler {

    fun planeErinnerungen(context: Context, termin: Termin) {
        val workManager = WorkManager.getInstance(context)
        val jetzt = Clock.System.now()

        termin.reminderTimes().forEach { reminder ->
            val verzoegerungMillis = reminder.instant.toEpochMilliseconds() - jetzt.toEpochMilliseconds()
            if (verzoegerungMillis <= 0) return@forEach // Zeitpunkt liegt in der Vergangenheit -> ueberspringen

            val eindeutigerWorkName = "reminder_${reminder.terminId}_${reminder.label.name}"

            val data = Data.Builder()
                .putString(ReminderWorker.KEY_TITEL, termin.titel)
                .putString(ReminderWorker.KEY_LABEL_TEXT, reminder.label.anzeigeText)
                .build()

            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(verzoegerungMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(TAG_ALLE_ERINNERUNGEN)
                .build()

            // REPLACE statt KEEP: wird ein Termin bearbeitet/geloescht und
            // neu geplant, ersetzt das automatisch alte, noch offene
            // Erinnerungen mit demselben eindeutigen Namen.
            workManager.enqueueUniqueWork(eindeutigerWorkName, ExistingWorkPolicy.REPLACE, request)
        }
    }

    /** Storniert alle drei Erinnerungsstufen eines Termins, z. B. beim Loeschen. */
    fun storniereErinnerungen(context: Context, terminId: String) {
        val workManager = WorkManager.getInstance(context)
        listOf("SIEBEN_TAGE", "DREI_TAGE", "VIERUNDZWANZIG_STUNDEN").forEach { stufe ->
            workManager.cancelUniqueWork("reminder_${terminId}_$stufe")
        }
    }

    const val TAG_ALLE_ERINNERUNGEN = "schulevault_reminder"
}
