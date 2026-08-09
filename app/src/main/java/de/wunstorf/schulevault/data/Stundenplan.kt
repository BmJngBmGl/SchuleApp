package de.wunstorf.schulevault.data

enum class Wochentag(val anzeigeText: String) {
    MONTAG("Montag"),
    DIENSTAG("Dienstag"),
    MITTWOCH("Mittwoch"),
    DONNERSTAG("Donnerstag"),
    FREITAG("Freitag")
}

/**
 * Ein Fach im Stundenplan eines Tages, zusammen mit dem zuletzt dort
 * bearbeiteten Thema. notizId (VaultNote.documentId der zugehoerigen
 * neuesten Notiz) erlaubt einen direkten Klick-Sprung dorthin - null, wenn
 * es noch keine Notiz zu diesem Fach gibt.
 */
data class StundenplanEintrag(
    val fach: String,
    val letztesThema: String?,
    val notizId: String?
)
