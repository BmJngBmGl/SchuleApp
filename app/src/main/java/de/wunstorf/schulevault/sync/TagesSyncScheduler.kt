package de.wunstorf.schulevault.sync

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.wunstorf.schulevault.data.WebUntisPreferences
import de.wunstorf.schulevault.data.Wochentag
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Die "Uhr" hinter dem taeglichen Hintergrund-Sync: plant statt eines
 * fixen PeriodicWorkRequest (das nicht auf zwei variable Tageszeiten
 * ausgerichtet werden kann) jeweils EINEN OneTimeWorkRequest fuer den
 * naechsten von zwei taeglichen Zeitpunkten - 8:00 und den zuletzt aus
 * WebUntis erfassten Schulschluss (Default 13:45, siehe
 * WebUntisPreferences.schulschlussZeiten). TagesSyncWorker plant nach jedem
 * Lauf selbst den naechsten - dieselbe selbst-verlaengernde Kette wie bei
 * den Termin-Erinnerungen (NotificationScheduler), ueberlebt also auch
 * Geraete-Neustarts von selbst (WorkManager persistiert eingeplante Arbeit).
 */
object TagesSyncScheduler {

    const val EINDEUTIGER_WORK_NAME = "tages_sync"
    const val DEFAULT_SCHULSCHLUSS = "13:45"

    /** Beim App-Start: nur einplanen, falls noch nichts vorgemerkt ist - der Worker haengt sich danach selbst weiter ein. */
    suspend fun sicherstellenGeplant(context: Context) {
        planeNaechstenLauf(context, ExistingWorkPolicy.KEEP)
    }

    suspend fun planeNaechstenLauf(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val schulschlussZeiten = WebUntisPreferences(context).schulschlussZeiten.first()
        val zone = TimeZone.currentSystemDefault()
        val jetzt = Clock.System.now()

        val naechste8Uhr = naechsterZeitpunkt(jetzt, zone, 8, 0)
        val naechsterSchulschluss = naechsterSchulschluss(jetzt, zone, schulschlussZeiten)
        val naechsterLauf = minOf(naechste8Uhr, naechsterSchulschluss)

        val verzoegerungMillis = (naechsterLauf.toEpochMilliseconds() - jetzt.toEpochMilliseconds())
            .coerceAtLeast(1000L)

        val request = OneTimeWorkRequestBuilder<TagesSyncWorker>()
            .setInitialDelay(verzoegerungMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(EINDEUTIGER_WORK_NAME, policy, request)
    }

    private fun naechsterZeitpunkt(jetzt: Instant, zone: TimeZone, stunde: Int, minute: Int): Instant {
        var datum = jetzt.toLocalDateTime(zone).date
        var kandidat = LocalDateTime(datum.year, datum.monthNumber, datum.dayOfMonth, stunde, minute).toInstant(zone)
        if (kandidat <= jetzt) {
            datum = datum.plus(DatePeriod(days = 1))
            kandidat = LocalDateTime(datum.year, datum.monthNumber, datum.dayOfMonth, stunde, minute).toInstant(zone)
        }
        return kandidat
    }

    /** Sucht ab heute den naechsten Schultag (Mo-Fr) mit einer noch in der Zukunft liegenden Schulschluss-Zeit. */
    private fun naechsterSchulschluss(
        jetzt: Instant,
        zone: TimeZone,
        schulschlussZeiten: Map<Wochentag, String>
    ): Instant {
        var datum = jetzt.toLocalDateTime(zone).date
        repeat(8) {
            val wochentag = wochentagVon(datum.dayOfWeek)
            if (wochentag != null) {
                val zeit = schulschlussZeiten[wochentag] ?: DEFAULT_SCHULSCHLUSS
                val teile = zeit.split(":")
                val stunde = teile.getOrNull(0)?.toIntOrNull() ?: 13
                val minute = teile.getOrNull(1)?.toIntOrNull() ?: 45
                val kandidat = LocalDateTime(datum.year, datum.monthNumber, datum.dayOfMonth, stunde, minute).toInstant(zone)
                if (kandidat > jetzt) return kandidat
            }
            datum = datum.plus(DatePeriod(days = 1))
        }
        // Unerreichbar (jeder 8-Tage-Zeitraum enthaelt einen Schultag) - rein defensiver Fallback.
        return naechsterZeitpunkt(jetzt, zone, 13, 45)
    }

    private fun wochentagVon(tag: DayOfWeek): Wochentag? = when (tag) {
        DayOfWeek.MONDAY -> Wochentag.MONTAG
        DayOfWeek.TUESDAY -> Wochentag.DIENSTAG
        DayOfWeek.WEDNESDAY -> Wochentag.MITTWOCH
        DayOfWeek.THURSDAY -> Wochentag.DONNERSTAG
        DayOfWeek.FRIDAY -> Wochentag.FREITAG
        else -> null
    }
}
