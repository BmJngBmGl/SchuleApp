package de.wunstorf.schulevault.data

/** Ein einzelner IServ-Kalender-Abo-Link (z. B. "Eigener Kalender", "Klasse 11a"). */
data class IServKalenderConfig(
    val label: String,
    val icsUrl: String
)
