package de.wunstorf.schulevault.data

/**
 * Repraesentiert eine einzelne Markdown-Datei aus dem Vault, aufgeteilt in
 * das per Frontmatter erkannte Metadaten-Set und den restlichen Inhalt.
 *
 * @param documentId  Android SAF Document-Id, wird fuer Lese-/Schreibzugriffe
 *                     ueber DocumentFile benoetigt.
 * @param folderPath  Pfad relativ zum Vault-Root, z. B. "Mathematik" oder
 *                     "Termine" - entspricht dem Fachordner in Obsidian.
 * @param fileName    Dateiname inkl. ".md"-Endung.
 * @param frontmatter Geparste Schluessel-Wert-Paare aus dem YAML-Frontmatter-Block.
 * @param body        Inhalt der Datei NACH dem Frontmatter-Block.
 */
data class VaultNote(
    val documentId: String,
    val folderPath: String,
    val fileName: String,
    val frontmatter: Map<String, String>,
    val body: String
) {
    val title: String
        get() = fileName.removeSuffix(".md")

    val fach: String?
        get() = frontmatter["fach"]

    /** themen/tags liegen im Frontmatter als "[a, b, c]" vor - hier aufgespalten. */
    val themen: List<String>
        get() = frontmatter["themen"]?.let { parseInlineList(it) } ?: emptyList()

    val tags: List<String>
        get() = frontmatter["tags"]?.let { parseInlineList(it) } ?: emptyList()

    val datum: String?
        get() = frontmatter["datum"]
}

/** Wandelt "[a, b, c]" bzw. "a, b, c" in eine Liste einzelner, getrimmter Eintraege um. */
fun parseInlineList(raw: String): List<String> =
    raw.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .map { it.trim().trim('"', '\'') }
        .filter { it.isNotEmpty() }
