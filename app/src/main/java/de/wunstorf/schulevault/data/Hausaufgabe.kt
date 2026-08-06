package de.wunstorf.schulevault.data

import kotlinx.datetime.LocalDate

/**
 * Eine Hausaufgabe/Abgabe, wie sie aus einer Datei in Hausaufgaben/ geparst
 * bzw. ueber das Eingabeformular neu angelegt wird. Bewusst getrennt von
 * Termin: hier zaehlt kein Countdown mit Erinnerungen, sondern nur ein
 * erledigt/offen-Status bis zur Faelligkeit.
 */
data class Hausaufgabe(
    val note: VaultNote,
    val fach: String,
    val titel: String,
    val faelligAm: LocalDate,
    val erledigt: Boolean,
    val notizText: String
)
