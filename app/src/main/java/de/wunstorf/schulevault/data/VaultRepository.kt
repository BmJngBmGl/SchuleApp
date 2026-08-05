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
