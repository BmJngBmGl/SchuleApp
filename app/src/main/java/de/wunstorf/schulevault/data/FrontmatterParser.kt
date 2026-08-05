package de.wunstorf.schulevault.data

/**
 * Sehr bewusst KEIN vollstaendiger YAML-Parser: Obsidian-Frontmatter in
 * diesem Vault besteht ausschliesslich aus einfachen "schluessel: wert"-
 * Zeilen (teils mit Inline-Listen wie "tags: [a, b]"). Ein Mini-Parser
 * reicht dafuer voellig aus und spart eine schwere YAML-Abhaengigkeit.
 */
object FrontmatterParser {

    private const val DELIMITER = "---"

    /**
     * Zerlegt den Dateiinhalt in Frontmatter (als Map) und Body.
     * Hat die Datei keinen Frontmatter-Block, ist die Map leer und der
     * gesamte Text landet im Body.
     */
    fun parse(rawContent: String): Pair<Map<String, String>, String> {
        val lines = rawContent.lines()
        if (lines.isEmpty() || lines[0].trim() != DELIMITER) {
            return emptyMap<String, String>() to rawContent
        }

        val endIndex = lines.drop(1).indexOfFirst { it.trim() == DELIMITER }
        if (endIndex == -1) {
            // Kein schliessendes "---" gefunden - Datei ist vermutlich
            // beschaedigt oder hat kein echtes Frontmatter, defensiv als
            // reinen Body behandeln statt abzustuerzen.
            return emptyMap<String, String>() to rawContent
        }

        val frontmatterLines = lines.subList(1, endIndex + 1)
        val bodyLines = lines.drop(endIndex + 2)

        val map = mutableMapOf<String, String>()
        for (line in frontmatterLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.contains(":")) continue
            val key = trimmed.substringBefore(":").trim()
            val value = trimmed.substringAfter(":").trim()
            if (key.isNotEmpty()) map[key] = value
        }

        return map to bodyLines.joinToString("\n")
    }

    /**
     * Baut aus Frontmatter-Map + Body wieder eine schreibfertige Datei.
     * Die Reihenfolge der Map bestimmt die Zeilenreihenfolge im
     * Frontmatter - beim Aufrufer daher ein LinkedHashMap/geordnetes
     * Erzeugen verwenden, wenn eine bestimmte Reihenfolge gewuenscht ist.
     */
    fun serialize(frontmatter: Map<String, String>, body: String): String {
        val fmBlock = buildString {
            appendLine(DELIMITER)
            frontmatter.forEach { (key, value) -> appendLine("$key: $value") }
            appendLine(DELIMITER)
        }
        return fmBlock + "\n" + body.trimStart('\n')
    }

    /** Formatiert eine Liste als Obsidian-Inline-Liste, z. B. ["a","b"] -> "[a, b]". */
    fun formatList(items: List<String>): String =
        "[" + items.joinToString(", ") + "]"
}
