package de.wunstorf.schulevault.data

enum class Wochentag(val anzeigeText: String) {
    MONTAG("Montag"),
    DIENSTAG("Dienstag"),
    MITTWOCH("Mittwoch"),
    DONNERSTAG("Donnerstag"),
    FREITAG("Freitag")
}

/** Ein Fach im Stundenplan eines Tages, zusammen mit dem zuletzt dort bearbeiteten Thema. */
data class StundenplanEintrag(
    val fach: String,
    val letztesThema: String?
)
