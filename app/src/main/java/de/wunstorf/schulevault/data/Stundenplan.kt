package de.wunstorf.schulevault.data

enum class Wochentag(val anzeigeText: String) {
    MONTAG("Montag"),
    DIENSTAG("Dienstag"),
    MITTWOCH("Mittwoch"),
    DONNERSTAG("Donnerstag"),
    FREITAG("Freitag")
}

/**
 * Ein Fach-Block im Stundenplan eines Tages, zusammen mit dem zuletzt dort
 * bearbeiteten Thema. notizId (VaultNote.documentId der zugehoerigen
 * neuesten Notiz) erlaubt einen direkten Klick-Sprung dorthin - null, wenn
 * es noch keine Notiz zu diesem Fach gibt. doppelstunde unterscheidet einen
 * Block aus zwei aufeinanderfolgenden Stunden desselben Fachs von einer
 * einzelnen Stunde - beides kann am selben Tag fuer dasselbe Fach vorkommen
 * (z. B. Mathe als Doppelstunde am Morgen UND als Einzelstunde am Nachmittag),
 * darf also nicht zu einem einzigen Eintrag zusammengefasst werden.
 */
data class StundenplanEintrag(
    val fach: String,
    val letztesThema: String?,
    val notizId: String?,
    val doppelstunde: Boolean
)
