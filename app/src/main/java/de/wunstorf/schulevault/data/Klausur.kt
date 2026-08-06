package de.wunstorf.schulevault.data

import kotlinx.datetime.LocalDate

/**
 * Eine Klausur mit den dafuer relevanten Themen. Merkt sich absichtlich
 * KEINEN eigenen Erledigt-Status pro Thema - "themenStatus" wird beim Laden
 * aus den Lernnotizen desselben Fachs abgeleitet (siehe
 * VaultRepository.loadAlleKlausuren): ein Thema gilt als abgehakt, sobald
 * irgendeine Lernnotiz dieses Thema fuehrt und als "verstanden" markiert ist.
 * Einzige Quelle der Wahrheit bleiben die Notizen selbst.
 */
data class Klausur(
    val note: VaultNote,
    val fach: String,
    val titel: String,
    val datum: LocalDate,
    val relevanteThemen: List<String>,
    val themenStatus: Map<String, Boolean>
)
