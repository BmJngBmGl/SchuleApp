package de.wunstorf.schulevault.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import de.wunstorf.schulevault.webuntis.WebUntisHausaufgabe
import kotlinx.datetime.LocalDate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Kapselt saemtlichen Dateizugriff auf den (per SAF ausgewaehlten)
 * Vault-Ordner. Bewusst OHNE hartcodierten Pfad wie
 * "/storage/emulated/0/Documents/..." - moderne Android-Versionen
 * verbieten direkten Dateisystemzugriff auf fremde App-/Sync-Ordner
 * (Scoped Storage), daher laeuft alles ueber die vom Nutzer einmalig
 * erteilte SAF-Baumberechtigung (ACTION_OPEN_DOCUMENT_TREE).
 *
 * Alle oeffentlichen Funktionen sind "suspend" und wechseln intern auf
 * Dispatchers.IO - DocumentFile/ContentResolver-Zugriffe sind blockierendes
 * I/O und duerfen nicht auf dem Hauptthread laufen (das fuehrte vorher zu
 * spuerbaren Rucklern/Haengern beim Oeffnen von Screens wie dem Stundenplan).
 */
class VaultRepository(private val context: Context) {

    /**
     * Liefert alle direkten Unterordner des Vault-Roots (z. B.
     * "Mathematik", "Termine", "Chemie", ...) - entspricht den
     * Fachordnern/Sonderordnern in Obsidian.
     */
    suspend fun listTopLevelFolders(rootUri: Uri): List<DocumentFile> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return@withContext emptyList()
        root.listFiles().filter { it.isDirectory }.sortedBy { it.name }
    }

    private fun findFolder(rootUri: Uri, folderName: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        return root.findFile(folderName)?.takeIf { it.isDirectory }
    }

    /** Wie findFolder, legt den Ordner aber an, falls er noch nicht existiert (z. B. "Hausaufgaben" beim allerersten Eintrag). */
    private fun findOrCreateFolder(rootUri: Uri, folderName: String): DocumentFile? {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        return root.findFile(folderName)?.takeIf { it.isDirectory } ?: root.createDirectory(folderName)
    }

    /** Liest alle .md-Dateien eines Unterordners und parst sie zu VaultNote. */
    suspend fun listNotesInFolder(rootUri: Uri, folderName: String): List<VaultNote> = withContext(Dispatchers.IO) {
        val folder = findFolder(rootUri, folderName) ?: return@withContext emptyList()
        folder.listFiles()
            .filter { it.isFile && it.name?.endsWith(".md") == true }
            .mapNotNull { doc -> readNote(doc, folderName) }
    }

    private fun readNote(doc: DocumentFile, folderPath: String): VaultNote? {
        val name = doc.name ?: return null
        val raw = readTextContent(doc.uri) ?: return null
        val (frontmatter, body) = FrontmatterParser.parse(raw)
        return VaultNote(
            documentId = doc.uri.toString(),
            folderPath = folderPath,
            fileName = name,
            frontmatter = frontmatter,
            body = body
        )
    }

    private fun readTextContent(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Laedt alle Termine aus dem "Termine"-Ordner. Dateien, die kein
     * gueltiges "datum"-Frontmatterfeld haben (z. B. die
     * Ferientermine-Sammelnotiz mit ihren Tabellen statt Einzeldatum),
     * werden uebersprungen - die App verwaltet nur EINZELTERMINE mit
     * genau einem Datum, keine Tabellen-Sammelnotizen.
     */
    suspend fun loadAlleTermine(rootUri: Uri): List<Termin> = withContext(Dispatchers.IO) {
        listNotesInFolder(rootUri, "Termine").mapNotNull { note ->
            val datumRoh = note.datum ?: return@mapNotNull null
            val datum = parseDatum(datumRoh) ?: return@mapNotNull null
            Termin(
                note = note,
                titel = note.title,
                datum = datum,
                istSchulisch = note.tags.contains("schule"),
                notizText = note.body.trim()
            )
        }.sortedBy { it.datum }
    }

    /** Erwartetes Format laut Vault-Konvention: "TT-MM-JJJJ". */
    private fun parseDatum(raw: String): LocalDate? {
        val bereinigt = raw.trim().trim('"')
        val teile = bereinigt.split("-", ".")
        if (teile.size != 3) return null
        return try {
            // TT-MM-JJJJ (Bindestrich, Vault-Konvention) oder TT.MM.JJJJ
            // (falls jemand das Trenn-zeichen manuell abweichend eingibt)
            val tag = teile[0].toInt()
            val monat = teile[1].toInt()
            val jahr = teile[2].toInt()
            LocalDate(jahr, monat, tag)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Legt einen neuen Termin als .md-Datei im Termine-Ordner an.
     * Format entspricht exakt der bestehenden Vault-Konvention:
     * Frontmatter mit datum: "TT-MM-JJJJ", Tags #termin (+ #schule optional).
     */
    suspend fun neuenTerminSpeichern(
        rootUri: Uri,
        titel: String,
        datum: LocalDate,
        istSchulisch: Boolean,
        notizText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Termine") ?: return@withContext false
        val datumFormatiert = "%02d-%02d-%04d".format(datum.dayOfMonth, datum.monthNumber, datum.year)
        val tags = mutableListOf("termin")
        if (istSchulisch) tags.add("schule")

        val frontmatter = linkedMapOf(
            "datum" to "\"$datumFormatiert\"",
            "tags" to FrontmatterParser.formatList(tags)
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)
        val dateiName = "${dateiNameAusTitel(titel)}.md"

        schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /**
     * Legt eine neue Lernnotiz im gewaehlten Fachordner an, nach dem
     * bestehenden Vault-Format JJJJ-MM-TT_kurztitel.md.
     */
    suspend fun neueLernnotizSpeichern(
        rootUri: Uri,
        fach: String,
        titel: String,
        themen: List<String>,
        notizText: String,
        heute: LocalDate
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, fach) ?: return@withContext false
        val datumPraefix = "%04d-%02d-%02d".format(heute.year, heute.monthNumber, heute.dayOfMonth)

        val frontmatter = linkedMapOf(
            "fach" to fach,
            "datum" to "\"$datumPraefix\"",
            "themen" to FrontmatterParser.formatList(themen),
            "tags" to FrontmatterParser.formatList(listOf("schule"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)
        val dateiName = "${datumPraefix}_${dateiNameAusTitel(titel)}.md"

        schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /**
     * Ueberschreibt einen bestehenden Termin. Bei Titeländerung wird die
     * Datei umbenannt (Vault-Konvention: Dateiname = Titel) - danach muss
     * der DocumentFile-Handle per findFile() neu geholt werden, da er nach
     * renameTo() laut Android-Doku als ungueltig gilt.
     */
    suspend fun terminAktualisieren(
        rootUri: Uri,
        note: VaultNote,
        titel: String,
        datum: LocalDate,
        istSchulisch: Boolean,
        notizText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Termine") ?: return@withContext false
        var doc = ordner.findFile(note.fileName) ?: return@withContext false

        val neuerDateiName = "${dateiNameAusTitel(titel)}.md"
        if (doc.name != neuerDateiName) {
            if (!doc.renameTo(neuerDateiName)) return@withContext false
            doc = ordner.findFile(neuerDateiName) ?: return@withContext false
        }

        val datumFormatiert = "%02d-%02d-%04d".format(datum.dayOfMonth, datum.monthNumber, datum.year)
        val tags = mutableListOf("termin")
        if (istSchulisch) tags.add("schule")
        val frontmatter = linkedMapOf(
            "datum" to "\"$datumFormatiert\"",
            "tags" to FrontmatterParser.formatList(tags)
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)

        try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Loescht einen Termin unwiderruflich. Erinnerungen muss der Aufrufer separat stornieren. */
    suspend fun terminLoeschen(rootUri: Uri, note: VaultNote): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Termine") ?: return@withContext false
        val doc = ordner.findFile(note.fileName) ?: return@withContext false
        try {
            doc.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Durchsucht alle Notizen ueber saemtliche Vault-Ordner hinweg (inkl.
     * "Termine") case-insensitiv nach Titel, Body und Themen. Bewusst ohne
     * Index/Cache - bei den ueblichen Vault-Groessen fuer ein Schuljahr
     * reicht ein einfacher linearer Scan voellig aus.
     */
    suspend fun sucheAlleNotizen(rootUri: Uri, query: String): List<VaultNote> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        listTopLevelFolders(rootUri)
            .mapNotNull { it.name }
            .flatMap { listNotesInFolder(rootUri, it) }
            .filter { note ->
                note.title.lowercase().contains(q) ||
                    note.body.lowercase().contains(q) ||
                    note.themen.any { it.lowercase().contains(q) }
            }
            .sortedBy { it.title }
    }

    /** Liest Organisation/Stundenplan.md und liefert je Wochentag die Faecher in Stundenreihenfolge. */
    suspend fun loadStundenplan(rootUri: Uri): Map<Wochentag, List<String>> = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Organisation") ?: return@withContext emptyMap()
        val doc = ordner.findFile("Stundenplan.md") ?: return@withContext emptyMap()
        val raw = readTextContent(doc.uri) ?: return@withContext emptyMap()
        val (_, body) = FrontmatterParser.parse(raw)
        StundenplanParser.parse(body)
    }

    /**
     * Schreibt Organisation/Stundenplan.md komplett neu (z. B. nach einem
     * WebUntis-Sync) - im selben Format, das StundenplanParser erwartet.
     * Ueberschreibt bestehenden Inhalt vollstaendig, das ist bei einem
     * "Synchronisieren"-Vorgang erwartetes Verhalten.
     */
    suspend fun speichereStundenplan(rootUri: Uri, plan: Map<Wochentag, List<String>>): Boolean =
        withContext(Dispatchers.IO) {
            val ordner = findOrCreateFolder(rootUri, "Organisation") ?: return@withContext false
            val doc = ordner.findFile("Stundenplan.md")
                ?: ordner.createFile("text/markdown", "Stundenplan.md")
                ?: return@withContext false

            val body = buildString {
                appendLine("# Stundenplan")
                appendLine()
                append("Automatisch aus WebUntis synchronisiert - manuelle Änderungen werden beim")
                appendLine(" nächsten Sync überschrieben.")
                Wochentag.entries.forEach { tag ->
                    val faecher = plan[tag]
                    if (!faecher.isNullOrEmpty()) {
                        appendLine()
                        appendLine("## ${tag.anzeigeText}")
                        faecher.forEachIndexed { index, fach -> appendLine("${index + 1}. $fach") }
                    }
                }
            }
            val frontmatter = linkedMapOf("tags" to FrontmatterParser.formatList(listOf("stundenplan")))
            val inhalt = FrontmatterParser.serialize(frontmatter, body)

            try {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Liefert die aktuellste Notiz eines Fachs - fuer die Themen-Anzeige UND
     * als Sprungziel bei Klick im Stundenplan. Bevorzugt die manuell im
     * Vault gepflegte "latest"-Markierung (siehe Vault-Formatierung.md,
     * pro Fach nur eine Notiz gleichzeitig "true") gegenueber einem reinen
     * Datumsvergleich - das funktioniert auch fuer Notizen ohne "datum"
     * oder wenn das Datum nicht die inhaltliche Aktualitaet widerspiegelt
     * (z. B. Referenz-/Leitfaden-Notizen). Ist keine Notiz als "latest"
     * markiert (z. B. in Faechern, die die Konvention noch nicht nutzen),
     * faellt die Funktion auf den bisherigen Datumsvergleich zurueck.
     */
    suspend fun letzteNotizFuerFach(rootUri: Uri, fach: String): VaultNote? = withContext(Dispatchers.IO) {
        val notizen = listNotesInFolder(rootUri, fach)
        notizen.firstOrNull { it.latest }
            ?: notizen
                .mapNotNull { note -> note.datum?.let { parseLernnotizDatum(it) }?.let { it to note } }
                .maxByOrNull { (datum, _) -> datum }
                ?.second
    }

    /** Erwartetes Format bei Lernnotizen laut Vault-Konvention: "JJJJ-MM-TT" (anders als bei Terminen). */
    private fun parseLernnotizDatum(raw: String): LocalDate? {
        val bereinigt = raw.trim().trim('"')
        val teile = bereinigt.split("-", ".")
        if (teile.size != 3) return null
        return try {
            val jahr = teile[0].toInt()
            val monat = teile[1].toInt()
            val tag = teile[2].toInt()
            LocalDate(jahr, monat, tag)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Laedt alle Hausaufgaben aus dem "Hausaufgaben"-Ordner. Bewusst getrennt
     * von Terminen: hier zaehlt kein Countdown mit Erinnerungen, sondern nur
     * ein erledigt/offen-Status. Notizen ohne gueltiges "faelligAm"-Feld
     * werden uebersprungen (gleiches Prinzip wie bei loadAlleTermine).
     */
    suspend fun loadAlleHausaufgaben(rootUri: Uri): List<Hausaufgabe> = withContext(Dispatchers.IO) {
        listNotesInFolder(rootUri, "Hausaufgaben").mapNotNull { note ->
            val faelligRoh = note.frontmatter["faelligAm"] ?: return@mapNotNull null
            val faelligAm = parseDatum(faelligRoh) ?: return@mapNotNull null
            Hausaufgabe(
                note = note,
                fach = note.fach ?: "",
                titel = note.title,
                faelligAm = faelligAm,
                erledigt = note.frontmatter["erledigt"]?.trim()?.trim('"') == "true",
                notizText = note.body.trim()
            )
        }.sortedBy { it.faelligAm }
    }

    /**
     * Legt eine neue Hausaufgabe an. Erzeugt den "Hausaufgaben"-Ordner beim
     * allerersten Eintrag automatisch. webuntisId ist optional und wird nur
     * bei automatisch aus WebUntis importierten Hausaufgaben gesetzt - siehe
     * webUntisHausaufgabenImportieren, das darueber Dubletten bei
     * wiederholtem Import erkennt.
     */
    suspend fun neueHausaufgabeSpeichern(
        rootUri: Uri,
        fach: String,
        titel: String,
        faelligAm: LocalDate,
        notizText: String,
        webuntisId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findOrCreateFolder(rootUri, "Hausaufgaben") ?: return@withContext false
        val frontmatter = linkedMapOf(
            "fach" to fach,
            "faelligAm" to "\"${formatiereDatum(faelligAm)}\"",
            "erledigt" to "false",
            "tags" to FrontmatterParser.formatList(listOf("hausaufgabe"))
        )
        if (webuntisId != null) frontmatter["webuntisId"] = "\"$webuntisId\""
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)
        val dateiName = "${dateiNameAusTitel(titel)}.md"
        schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /**
     * Importiert WebUntis-Hausaufgaben als neue Dateien im "Hausaufgaben"-
     * Ordner - Dubletten werden ueber das "webuntisId"-Frontmatterfeld
     * bestehender Notizen erkannt und uebersprungen, damit ein wiederholter
     * Import (z. B. taeglich ueber TagesSyncWorker) nicht jedes Mal
     * Kopien anlegt. Liefert die Anzahl tatsaechlich neu angelegter Dateien.
     */
    suspend fun webUntisHausaufgabenImportieren(
        rootUri: Uri,
        hausaufgaben: List<WebUntisHausaufgabe>
    ): Int = withContext(Dispatchers.IO) {
        if (hausaufgaben.isEmpty()) return@withContext 0
        val bekannteIds = listNotesInFolder(rootUri, "Hausaufgaben")
            .mapNotNull { it.frontmatter["webuntisId"]?.trim()?.trim('"') }
            .toSet()

        var importiert = 0
        hausaufgaben.forEach { hausaufgabe ->
            if (hausaufgabe.webuntisId in bekannteIds) return@forEach
            val titel = "${hausaufgabe.fach}: ${hausaufgabe.text}".take(80)
            val erfolgreich = neueHausaufgabeSpeichern(
                rootUri, hausaufgabe.fach, titel, hausaufgabe.faelligAm, hausaufgabe.text, hausaufgabe.webuntisId
            )
            if (erfolgreich) importiert++
        }
        importiert
    }

    /** Ueberschreibt eine bestehende Hausaufgabe (Inhalt + optional Umbenennung bei Titeländerung), gleiches Muster wie terminAktualisieren. */
    suspend fun hausaufgabeAktualisieren(
        rootUri: Uri,
        note: VaultNote,
        fach: String,
        titel: String,
        faelligAm: LocalDate,
        erledigt: Boolean,
        notizText: String
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Hausaufgaben") ?: return@withContext false
        var doc = ordner.findFile(note.fileName) ?: return@withContext false

        val neuerDateiName = "${dateiNameAusTitel(titel)}.md"
        if (doc.name != neuerDateiName) {
            if (!doc.renameTo(neuerDateiName)) return@withContext false
            doc = ordner.findFile(neuerDateiName) ?: return@withContext false
        }

        val frontmatter = linkedMapOf(
            "fach" to fach,
            "faelligAm" to "\"${formatiereDatum(faelligAm)}\"",
            "erledigt" to erledigt.toString(),
            "tags" to FrontmatterParser.formatList(listOf("hausaufgabe"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)

        try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Loescht eine Hausaufgabe unwiderruflich. */
    suspend fun hausaufgabeLoeschen(rootUri: Uri, note: VaultNote): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Hausaufgaben") ?: return@withContext false
        val doc = ordner.findFile(note.fileName) ?: return@withContext false
        try {
            doc.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun formatiereDatum(datum: LocalDate): String =
        "%02d-%02d-%04d".format(datum.dayOfMonth, datum.monthNumber, datum.year)

    /**
     * Laedt alle Klausuren aus dem "Klausuren"-Ordner - eingeschraenkt auf
     * Notizen mit dem "kl12"-Tag, da der Tracker nur die aktuelle Klassenstufe
     * zeigen soll (aeltere Klausuren aus frueheren Schuljahren bleiben im
     * Vault erhalten, tauchen im Tracker aber nicht mehr auf). Fuer jede
     * Klausur wird je relevantem Thema berechnet, ob es "abgehakt" ist -
     * abgeleitet aus den Lernnotizen desselben Fachs (nicht selbst
     * gespeichert): ein Thema gilt als verstanden, sobald irgendeine
     * Lernnotiz dieses Thema (case-insensitiv) fuehrt UND als verstanden
     * markiert ist.
     */
    suspend fun loadAlleKlausuren(rootUri: Uri): List<Klausur> = withContext(Dispatchers.IO) {
        listNotesInFolder(rootUri, "Klausuren").mapNotNull { note ->
            if (!note.tags.contains("kl12")) return@mapNotNull null
            val datumRoh = note.datum ?: return@mapNotNull null
            val datum = parseDatum(datumRoh) ?: return@mapNotNull null
            val fach = note.fach ?: return@mapNotNull null
            Klausur(
                note = note,
                fach = fach,
                titel = note.title,
                datum = datum,
                relevanteThemen = note.themen,
                themenStatus = klausurThemenStatus(rootUri, fach, note.themen)
            )
        }.sortedBy { it.datum }
    }

    private suspend fun klausurThemenStatus(rootUri: Uri, fach: String, relevanteThemen: List<String>): Map<String, Boolean> {
        val notizenDesFachs = listNotesInFolder(rootUri, fach)
        return relevanteThemen.associateWith { thema ->
            notizenDesFachs.any { notiz ->
                notiz.verstanden && notiz.themen.any { it.equals(thema, ignoreCase = true) }
            }
        }
    }

    /** Legt eine neue Klausur an. Erzeugt den "Klausuren"-Ordner beim allerersten Eintrag automatisch. */
    suspend fun neueKlausurSpeichern(
        rootUri: Uri,
        fach: String,
        titel: String,
        datum: LocalDate,
        relevanteThemen: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findOrCreateFolder(rootUri, "Klausuren") ?: return@withContext false
        val frontmatter = linkedMapOf(
            "fach" to fach,
            "datum" to "\"${formatiereDatum(datum)}\"",
            "themen" to FrontmatterParser.formatList(relevanteThemen),
            "tags" to FrontmatterParser.formatList(listOf("klausur", "schule", "kl12"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, "")
        val dateiName = "${dateiNameAusTitel(titel)}.md"
        schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /** Ueberschreibt eine bestehende Klausur (Inhalt + optional Umbenennung bei Titeländerung), gleiches Muster wie terminAktualisieren. */
    suspend fun klausurAktualisieren(
        rootUri: Uri,
        note: VaultNote,
        fach: String,
        titel: String,
        datum: LocalDate,
        relevanteThemen: List<String>
    ): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Klausuren") ?: return@withContext false
        var doc = ordner.findFile(note.fileName) ?: return@withContext false

        val neuerDateiName = "${dateiNameAusTitel(titel)}.md"
        if (doc.name != neuerDateiName) {
            if (!doc.renameTo(neuerDateiName)) return@withContext false
            doc = ordner.findFile(neuerDateiName) ?: return@withContext false
        }

        val frontmatter = linkedMapOf(
            "fach" to fach,
            "datum" to "\"${formatiereDatum(datum)}\"",
            "themen" to FrontmatterParser.formatList(relevanteThemen),
            "tags" to FrontmatterParser.formatList(listOf("klausur", "schule", "kl12"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, note.body)

        try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Loescht eine Klausur unwiderruflich. */
    suspend fun klausurLoeschen(rootUri: Uri, note: VaultNote): Boolean = withContext(Dispatchers.IO) {
        val ordner = findFolder(rootUri, "Klausuren") ?: return@withContext false
        val doc = ordner.findFile(note.fileName) ?: return@withContext false
        try {
            doc.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Setzt/loescht das "Verstanden"-Haekchen einer beliebigen Notiz (nicht
     * klausurspezifisch - funktioniert fuer jede Notiz in jedem Fachordner).
     * Bestehende Frontmatter-Felder bleiben erhalten, nur "verstanden" wird
     * ueberschrieben.
     */
    suspend fun notizVerstandenSetzen(rootUri: Uri, note: VaultNote, verstanden: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val ordner = findFolder(rootUri, note.folderPath) ?: return@withContext false
            val doc = ordner.findFile(note.fileName) ?: return@withContext false

            val neueFrontmatter = LinkedHashMap(note.frontmatter)
            neueFrontmatter["verstanden"] = verstanden.toString()
            val inhalt = FrontmatterParser.serialize(neueFrontmatter, note.body)

            try {
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
                }
                true
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Prueft, ob die dauerhafte SAF-Berechtigung fuer den Vault-Ordner noch
     * besteht. Kann z. B. nach Umbenennen/Verschieben des Ordners oder nach
     * einem Reset der App-Berechtigungen durch das System entzogen werden.
     * Kein Datei-I/O (nur ein In-Memory-Check) - bewusst NICHT suspend.
     */
    fun hatGueltigeBerechtigung(rootUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == rootUri && it.isReadPermission
        }

    private fun schreibeNeueDatei(ordner: DocumentFile, dateiName: String, inhalt: String): Boolean {
        return try {
            val neueDatei = ordner.createFile("text/markdown", dateiName) ?: return false
            context.contentResolver.openOutputStream(neueDatei.uri)?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** "Mein toller Titel!" -> "Mein-toller-Titel" - dateisystemtaugliche Kurzform. */
    private fun dateiNameAusTitel(titel: String): String =
        titel.trim()
            .replace(Regex("[^A-Za-z0-9äöüÄÖÜß ]"), "")
            .replace(Regex("\\s+"), "-")
            .take(60)
            .ifEmpty { "Notiz" }
}
