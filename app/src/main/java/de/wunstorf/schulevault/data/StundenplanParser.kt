package de.wunstorf.schulevault.data

/**
 * Sehr bewusst kein YAML/Tabellen-Parser: der Stundenplan-Body besteht nur
 * aus "## Wochentag"-Ueberschriften mit einer nummerierten Fächerliste
 * darunter - ein einfacher zeilenbasierter Parser reicht dafuer, gleiche
 * Philosophie wie FrontmatterParser.
 */
object StundenplanParser {

    private val wochentagNachName: Map<String, Wochentag> =
        Wochentag.entries.associateBy { it.anzeigeText.lowercase() }

    /** Liefert je Wochentag die Faecher in Stundenreihenfolge (ggf. mit Wiederholungen). */
    fun parse(body: String): Map<Wochentag, List<String>> {
        val ergebnis = mutableMapOf<Wochentag, MutableList<String>>()
        var aktuellerTag: Wochentag? = null

        for (rohzeile in body.lines()) {
            val zeile = rohzeile.trim()
            if (zeile.startsWith("#")) {
                val ueberschrift = zeile.trimStart('#').trim().lowercase()
                aktuellerTag = wochentagNachName[ueberschrift]
                continue
            }
            if (aktuellerTag == null || zeile.isEmpty()) continue

            val fach = zeile.replace(Regex("""^\d+[.)]\s*"""), "").trim()
            if (fach.isNotEmpty()) {
                ergebnis.getOrPut(aktuellerTag) { mutableListOf() }.add(fach)
            }
        }

        return ergebnis
    }
}
