package de.wunstorf.schulevault.iserv

import de.wunstorf.schulevault.data.IServTermin
import kotlinx.datetime.LocalDate
import java.util.UUID

/**
 * Minimaler ICS/VEVENT-Parser - bewusst kein vollstaendiger iCalendar-Parser
 * (RFC 5545 ist riesig), sondern nur das Minimum, um Titel+Datum aus
 * VEVENT-Bloecken zu lesen. Gleiche "keine schwere Format-Bibliothek noetig"-
 * Philosophie wie FrontmatterParser.
 */
object IcsParser {

    private val datumRegex = Regex("""(\d{4})(\d{2})(\d{2})""")

    fun parse(icsText: String, kalenderLabel: String): List<IServTermin> {
        val zeilen = entfalteZeilen(icsText)
        val ergebnisse = mutableListOf<IServTermin>()

        var inEvent = false
        var summary: String? = null
        var datum: LocalDate? = null
        var uid: String? = null

        for (zeile in zeilen) {
            when {
                zeile.startsWith("BEGIN:VEVENT") -> {
                    inEvent = true
                    summary = null
                    datum = null
                    uid = null
                }
                zeile.startsWith("END:VEVENT") -> {
                    if (inEvent && summary != null && datum != null) {
                        ergebnisse.add(
                            IServTermin(
                                uid = uid ?: UUID.randomUUID().toString(),
                                titel = summary,
                                datum = datum,
                                kalenderLabel = kalenderLabel
                            )
                        )
                    }
                    inEvent = false
                }
                inEvent && zeile.startsWith("SUMMARY") -> {
                    summary = zeile.substringAfter(":").trim().ifEmpty { null }
                }
                inEvent && zeile.startsWith("DTSTART") -> {
                    val wert = zeile.substringAfter(":")
                    datum = datumRegex.find(wert)?.let { match ->
                        try {
                            LocalDate(
                                match.groupValues[1].toInt(),
                                match.groupValues[2].toInt(),
                                match.groupValues[3].toInt()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
                inEvent && zeile.startsWith("UID") -> {
                    uid = zeile.substringAfter(":").trim()
                }
            }
        }

        return ergebnisse
    }

    /** Entfaltet RFC5545-Zeilenfortsetzungen: Zeilen, die mit Leerzeichen/Tab beginnen, gehoeren zur vorherigen Zeile. */
    private fun entfalteZeilen(icsText: String): List<String> {
        val rohzeilen = icsText.replace("\r\n", "\n").split("\n")
        val entfaltet = mutableListOf<String>()
        for (zeile in rohzeilen) {
            if ((zeile.startsWith(" ") || zeile.startsWith("\t")) && entfaltet.isNotEmpty()) {
                entfaltet[entfaltet.size - 1] = entfaltet.last() + zeile.substring(1)
            } else {
                entfaltet.add(zeile)
            }
        }
        return entfaltet
    }
}
