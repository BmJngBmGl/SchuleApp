package de.wunstorf.schulevault.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
 */
class VaultRepository(private val context: Context) {

    /**
     * Liefert alle direkten Unterordner des Vault-Roots (z. B.
     * "Mathematik", "Termine", "Chemie", ...) - entspricht den
     * Fachordnern/Sonderordnern in Obsidian.
     */
    fun listTopLevelFolders(rootUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        return root.listFiles().filter { it.isDirectory }.sortedBy { it.name }
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
    fun listNotesInFolder(rootUri: Uri, folderName: String): List<VaultNote> {
        val folder = findFolder(rootUri, folderName) ?: return emptyList()
        return folder.listFiles()
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
    fun loadAlleTermine(rootUri: Uri): List<Termin> {
        return listNotesInFolder(rootUri, "Termine").mapNotNull { note ->
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
    fun neuenTerminSpeichern(
        rootUri: Uri,
        titel: String,
        datum: LocalDate,
        istSchulisch: Boolean,
        notizText: String
    ): Boolean {
        val ordner = findFolder(rootUri, "Termine") ?: return false
        val datumFormatiert = "%02d-%02d-%04d".format(datum.dayOfMonth, datum.monthNumber, datum.year)
        val tags = mutableListOf("termin")
        if (istSchulisch) tags.add("schule")

        val frontmatter = linkedMapOf(
            "datum" to "\"$datumFormatiert\"",
            "tags" to FrontmatterParser.formatList(tags)
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)
        val dateiName = "${dateiNameAusTitel(titel)}.md"

        return schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /**
     * Legt eine neue Lernnotiz im gewaehlten Fachordner an, nach dem
     * bestehenden Vault-Format JJJJ-MM-TT_kurztitel.md.
     */
    fun neueLernnotizSpeichern(
        rootUri: Uri,
        fach: String,
        titel: String,
        themen: List<String>,
        notizText: String,
        heute: LocalDate
    ): Boolean {
        val ordner = findFolder(rootUri, fach) ?: return false
        val datumPraefix = "%04d-%02d-%02d".format(heute.year, heute.monthNumber, heute.dayOfMonth)

        val frontmatter = linkedMapOf(
            "fach" to fach,
            "datum" to "\"$datumPraefix\"",
            "themen" to FrontmatterParser.formatList(themen),
            "tags" to FrontmatterParser.formatList(listOf("schule"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)
        val dateiName = "${datumPraefix}_${dateiNameAusTitel(titel)}.md"

        return schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /**
     * Ueberschreibt einen bestehenden Termin. Bei Titeländerung wird die
     * Datei umbenannt (Vault-Konvention: Dateiname = Titel) - danach muss
     * der DocumentFile-Handle per findFile() neu geholt werden, da er nach
     * renameTo() laut Android-Doku als ungueltig gilt.
     */
    fun terminAktualisieren(
        rootUri: Uri,
        note: VaultNote,
        titel: String,
        datum: LocalDate,
        istSchulisch: Boolean,
        notizText: String
    ): Boolean {
        val ordner = findFolder(rootUri, "Termine") ?: return false
        var doc = ordner.findFile(note.fileName) ?: return false

        val neuerDateiName = "${dateiNameAusTitel(titel)}.md"
        if (doc.name != neuerDateiName) {
            if (!doc.renameTo(neuerDateiName)) return false
            doc = ordner.findFile(neuerDateiName) ?: return false
        }

        val datumFormatiert = "%02d-%02d-%04d".format(datum.dayOfMonth, datum.monthNumber, datum.year)
        val tags = mutableListOf("termin")
        if (istSchulisch) tags.add("schule")
        val frontmatter = linkedMapOf(
            "datum" to "\"$datumFormatiert\"",
            "tags" to FrontmatterParser.formatList(tags)
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)

        return try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Loescht einen Termin unwiderruflich. Erinnerungen muss der Aufrufer separat stornieren. */
    fun terminLoeschen(rootUri: Uri, note: VaultNote): Boolean {
        val ordner = findFolder(rootUri, "Termine") ?: return false
        val doc = ordner.findFile(note.fileName) ?: return false
        return try {
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
    fun sucheAlleNotizen(rootUri: Uri, query: String): List<VaultNote> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return listTopLevelFolders(rootUri)
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
    fun loadStundenplan(rootUri: Uri): Map<Wochentag, List<String>> {
        val ordner = findFolder(rootUri, "Organisation") ?: return emptyMap()
        val doc = ordner.findFile("Stundenplan.md") ?: return emptyMap()
        val raw = readTextContent(doc.uri) ?: return emptyMap()
        val (_, body) = FrontmatterParser.parse(raw)
        return StundenplanParser.parse(body)
    }

    /**
     * Liefert die Themen der zuletzt bearbeiteten Notiz eines Fachs (nach
     * "datum" sortiert). Notizen ohne gueltiges Datum (z. B. aeltere
     * Archiv-Notizen ohne datum-Feld) werden dabei uebersprungen, da sie
     * sich zeitlich nicht einordnen lassen.
     */
    fun letztesThemaFuerFach(rootUri: Uri, fach: String): String? {
        val neueste = listNotesInFolder(rootUri, fach)
            .mapNotNull { note -> note.datum?.let { parseLernnotizDatum(it) }?.let { it to note } }
            .maxByOrNull { (datum, _) -> datum }
            ?.second
            ?: return null
        return neueste.themen.takeIf { it.isNotEmpty() }?.joinToString(", ")
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
    fun loadAlleHausaufgaben(rootUri: Uri): List<Hausaufgabe> {
        return listNotesInFolder(rootUri, "Hausaufgaben").mapNotNull { note ->
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

    /** Legt eine neue Hausaufgabe an. Erzeugt den "Hausaufgaben"-Ordner beim allerersten Eintrag automatisch. */
    fun neueHausaufgabeSpeichern(
        rootUri: Uri,
        fach: String,
        titel: String,
        faelligAm: LocalDate,
        notizText: String
    ): Boolean {
        val ordner = findOrCreateFolder(rootUri, "Hausaufgaben") ?: return false
        val frontmatter = linkedMapOf(
            "fach" to fach,
            "faelligAm" to "\"${formatiereDatum(faelligAm)}\"",
            "erledigt" to "false",
            "tags" to FrontmatterParser.formatList(listOf("hausaufgabe"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)
        val dateiName = "${dateiNameAusTitel(titel)}.md"
        return schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /** Ueberschreibt eine bestehende Hausaufgabe (Inhalt + optional Umbenennung bei Titeländerung), gleiches Muster wie terminAktualisieren. */
    fun hausaufgabeAktualisieren(
        rootUri: Uri,
        note: VaultNote,
        fach: String,
        titel: String,
        faelligAm: LocalDate,
        erledigt: Boolean,
        notizText: String
    ): Boolean {
        val ordner = findFolder(rootUri, "Hausaufgaben") ?: return false
        var doc = ordner.findFile(note.fileName) ?: return false

        val neuerDateiName = "${dateiNameAusTitel(titel)}.md"
        if (doc.name != neuerDateiName) {
            if (!doc.renameTo(neuerDateiName)) return false
            doc = ordner.findFile(neuerDateiName) ?: return false
        }

        val frontmatter = linkedMapOf(
            "fach" to fach,
            "faelligAm" to "\"${formatiereDatum(faelligAm)}\"",
            "erledigt" to erledigt.toString(),
            "tags" to FrontmatterParser.formatList(listOf("hausaufgabe"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, notizText)

        return try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Loescht eine Hausaufgabe unwiderruflich. */
    fun hausaufgabeLoeschen(rootUri: Uri, note: VaultNote): Boolean {
        val ordner = findFolder(rootUri, "Hausaufgaben") ?: return false
        val doc = ordner.findFile(note.fileName) ?: return false
        return try {
            doc.delete()
        } catch (e: Exception) {
            false
        }
    }

    private fun formatiereDatum(datum: LocalDate): String =
        "%02d-%02d-%04d".format(datum.dayOfMonth, datum.monthNumber, datum.year)

    /**
     * Laedt alle Klausuren aus dem "Klausuren"-Ordner. Fuer jede Klausur wird
     * je relevantem Thema berechnet, ob es "abgehakt" ist - abgeleitet aus
     * den Lernnotizen desselben Fachs (nicht selbst gespeichert): ein Thema
     * gilt als verstanden, sobald irgendeine Lernnotiz dieses Thema (case-
     * insensitiv) fuehrt UND als verstanden markiert ist.
     */
    fun loadAlleKlausuren(rootUri: Uri): List<Klausur> {
        return listNotesInFolder(rootUri, "Klausuren").mapNotNull { note ->
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

    private fun klausurThemenStatus(rootUri: Uri, fach: String, relevanteThemen: List<String>): Map<String, Boolean> {
        val notizenDesFachs = listNotesInFolder(rootUri, fach)
        return relevanteThemen.associateWith { thema ->
            notizenDesFachs.any { notiz ->
                notiz.verstanden && notiz.themen.any { it.equals(thema, ignoreCase = true) }
            }
        }
    }

    /** Legt eine neue Klausur an. Erzeugt den "Klausuren"-Ordner beim allerersten Eintrag automatisch. */
    fun neueKlausurSpeichern(
        rootUri: Uri,
        fach: String,
        titel: String,
        datum: LocalDate,
        relevanteThemen: List<String>
    ): Boolean {
        val ordner = findOrCreateFolder(rootUri, "Klausuren") ?: return false
        val frontmatter = linkedMapOf(
            "fach" to fach,
            "datum" to "\"${formatiereDatum(datum)}\"",
            "themen" to FrontmatterParser.formatList(relevanteThemen),
            "tags" to FrontmatterParser.formatList(listOf("klausur", "schule"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, "")
        val dateiName = "${dateiNameAusTitel(titel)}.md"
        return schreibeNeueDatei(ordner, dateiName, inhalt)
    }

    /** Ueberschreibt eine bestehende Klausur (Inhalt + optional Umbenennung bei Titeländerung), gleiches Muster wie terminAktualisieren. */
    fun klausurAktualisieren(
        rootUri: Uri,
        note: VaultNote,
        fach: String,
        titel: String,
        datum: LocalDate,
        relevanteThemen: List<String>
    ): Boolean {
        val ordner = findFolder(rootUri, "Klausuren") ?: return false
        var doc = ordner.findFile(note.fileName) ?: return false

        val neuerDateiName = "${dateiNameAusTitel(titel)}.md"
        if (doc.name != neuerDateiName) {
            if (!doc.renameTo(neuerDateiName)) return false
            doc = ordner.findFile(neuerDateiName) ?: return false
        }

        val frontmatter = linkedMapOf(
            "fach" to fach,
            "datum" to "\"${formatiereDatum(datum)}\"",
            "themen" to FrontmatterParser.formatList(relevanteThemen),
            "tags" to FrontmatterParser.formatList(listOf("klausur", "schule"))
        )
        val inhalt = FrontmatterParser.serialize(frontmatter, note.body)

        return try {
            context.contentResolver.openOutputStream(doc.uri, "wt")?.use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { it.write(inhalt) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Loescht eine Klausur unwiderruflich. */
    fun klausurLoeschen(rootUri: Uri, note: VaultNote): Boolean {
        val ordner = findFolder(rootUri, "Klausuren") ?: return false
        val doc = ordner.findFile(note.fileName) ?: return false
        return try {
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
    fun notizVerstandenSetzen(rootUri: Uri, note: VaultNote, verstanden: Boolean): Boolean {
        val ordner = findFolder(rootUri, note.folderPath) ?: return false
        val doc = ordner.findFile(note.fileName) ?: return false

        val neueFrontmatter = LinkedHashMap(note.frontmatter)
        neueFrontmatter["verstanden"] = verstanden.toString()
        val inhalt = FrontmatterParser.serialize(neueFrontmatter, note.body)

        return try {
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
