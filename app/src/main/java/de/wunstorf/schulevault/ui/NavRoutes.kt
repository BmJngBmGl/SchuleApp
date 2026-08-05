package de.wunstorf.schulevault.ui

/** Zentrale Sammlung aller Navigations-Routennamen, tippsicherer als rohe Strings verstreut im Code. */
object NavRoutes {
    const val UEBERSICHT = "uebersicht"
    const val TERMIN_EINGABE = "termin_eingabe"
    const val LERNNOTIZ_EINGABE = "lernnotiz_eingabe"
    const val FACH_DETAIL = "fach_detail/{fach}"
    const val TERMIN_BEARBEITEN = "termin_bearbeiten/{dateiname}"
    const val SUCHE = "suche"

    fun fachDetail(fach: String) = "fach_detail/$fach"
    fun terminBearbeiten(dateiname: String) = "termin_bearbeiten/${android.net.Uri.encode(dateiname)}"
}
