package de.wunstorf.schulevault.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * Ein einzelner Termin, wie er aus einer Datei in Termine/ geparst bzw.
 * ueber das Eingabeformular neu angelegt wird.
 *
 * @param datum Ereignisdatum (Beginn). Fuer eine reine Terminerinnerung
 *              zaehlt nur das Datum, keine Uhrzeit - Erinnerungen werden
 *              relativ zu Tagesbeginn (00:00 Lokalzeit) berechnet.
 * @param istSchulisch true, wenn der Tag #schule-Tag gesetzt ist - steuert
 *              nur die Anzeige (Badge-Farbe), keine Funktionalitaet.
 */
data class Termin(
    val note: VaultNote,
    val titel: String,
    val datum: LocalDate,
    val istSchulisch: Boolean,
    val notizText: String
) {
    /**
     * Liefert die drei geplanten Erinnerungszeitpunkte (7 Tage, 3 Tage,
     * 24 Stunden vor dem Termin), jeweils auf 8:00 Uhr lokal gelegt - eine
     * Erinnerung mitten in der Nacht waere nutzlos.
     */
    fun reminderTimes(zone: TimeZone = TimeZone.currentSystemDefault()): List<ReminderTime> {
        val terminStart = datum.atStartOfDayIn(zone)
        val offsets = listOf(
            ReminderLabel.SIEBEN_TAGE to 7.days,
            ReminderLabel.DREI_TAGE to 3.days,
            ReminderLabel.VIERUNDZWANZIG_STUNDEN to 1.days
        )
        return offsets.map { (label, offset) ->
            val rohZeitpunkt = terminStart.minus(offset)
            val amAchtUhr = LocalDateTime(
                rohZeitpunkt.toLocalDateTime(zone).date,
                LocalTime(hour = 8, minute = 0)
            ).toInstant(zone)
            ReminderTime(label = label, terminId = "${note.folderPath}/${note.fileName}", instant = amAchtUhr)
        }
    }
}

enum class ReminderLabel(val anzeigeText: String) {
    SIEBEN_TAGE("in 7 Tagen"),
    DREI_TAGE("in 3 Tagen"),
    VIERUNDZWANZIG_STUNDEN("in 24 Stunden")
}

data class ReminderTime(
    val label: ReminderLabel,
    /** Eindeutiger Schluessel des zugehoerigen Termins, fuer WorkManager-Tags. */
    val terminId: String,
    val instant: Instant
)
