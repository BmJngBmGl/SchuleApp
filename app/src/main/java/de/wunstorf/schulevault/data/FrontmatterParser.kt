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
        var index = 0
        while (index < frontmatterLines.size) {
            val trimmed = frontmatterLines[index].trim()
            if (trimmed.isEmpty() || !trimmed.contains(":")) {
                index++
                continue
            }
            val key = trimmed.substringBefore(":").trim()
            val value = trimmed.substringAfter(":").trim()
            if (key.isEmpty()) {
                index++
                continue
            }
            if (value.isEmpty()) {
                // Moegliche YAML-Blockliste statt Inline-Liste ("key:"
                // gefolgt von "  - item"-Zeilen) - Obsidian formatiert
                // Listen bei jeder programmatischen Frontmatter-Aenderung
                // (z. B. ueber die MCP-Anbindung) so um, nicht nur beim
                // manuellen Bearbeiten. Wird zur selben "[a, b]"-Inline-
                // Darstellung zusammengefasst, die parseInlineList erwartet,
                // damit beide Schreibweisen gleichwertig funktionieren.
                val items = mutableListOf<String>()
                var lookahead = index + 1
                while (lookahead < frontmatterLines.size) {
                    val naechste = frontmatterLines[lookahead].trim()
                    if (!naechste.startsWith("- ") && naechste != "-") break
                    items.add(naechste.removePrefix("-").trim())
                    lookahead++
                }
                if (items.isNotEmpty()) {
                    map[key] = "[" + items.joinToString(", ") + "]"
                    index = lookahead
                    continue
                }
            }
            map[key] = value
            index++
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
