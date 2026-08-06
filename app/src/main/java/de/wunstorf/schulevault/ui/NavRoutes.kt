package de.wunstorf.schulevault.ui

/** Zentrale Sammlung aller Navigations-Routennamen, tippsicherer als rohe Strings verstreut im Code. */
object NavRoutes {
    const val UEBERSICHT = "uebersicht"
    const val TERMIN_EINGABE = "termin_eingabe"
    const val LERNNOTIZ_EINGABE = "lernnotiz_eingabe"
    const val FACH_DETAIL = "fach_detail/{fach}"
    const val TERMIN_BEARBEITEN = "termin_bearbeiten/{dateiname}"
    const val SUCHE = "suche"
    const val STUNDENPLAN = "stundenplan"
    const val HAUSAUFGABE_EINGABE = "hausaufgabe_eingabe"
    const val HAUSAUFGABE_BEARBEITEN = "hausaufgabe_bearbeiten/{dateiname}"

    fun fachDetail(fach: String) = "fach_detail/$fach"
    fun terminBearbeiten(dateiname: String) = "termin_bearbeiten/${android.net.Uri.encode(dateiname)}"
    fun hausaufgabeBearbeiten(dateiname: String) = "hausaufgabe_bearbeiten/${android.net.Uri.encode(dateiname)}"
}
