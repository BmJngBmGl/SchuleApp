package de.wunstorf.schulevault.ui

/** Zentrale Sammlung aller Navigations-Routennamen, tippsicherer als rohe Strings verstreut im Code. */
object NavRoutes {
    const val UEBERSICHT = "uebersicht"
    const val TERMIN_EINGABE = "termin_eingabe"
    const val LERNNOTIZ_EINGABE = "lernnotiz_eingabe"
    const val FACH_DETAIL = "fach_detail/{fach}?notizId={notizId}"
    const val TERMIN_BEARBEITEN = "termin_bearbeiten/{dateiname}"
    const val SUCHE = "suche"
    const val HAUSAUFGABE_EINGABE = "hausaufgabe_eingabe"
    const val HAUSAUFGABE_BEARBEITEN = "hausaufgabe_bearbeiten/{dateiname}"
    const val EINSTELLUNGEN = "einstellungen"
    const val KLAUSUR_EINGABE = "klausur_eingabe"
    const val KLAUSUR_BEARBEITEN = "klausur_bearbeiten/{dateiname}"

    fun fachDetail(fach: String, notizId: String? = null): String {
        val basis = "fach_detail/$fach"
        return if (notizId != null) "$basis?notizId=${android.net.Uri.encode(notizId)}" else basis
    }
    fun terminBearbeiten(dateiname: String) = "termin_bearbeiten/${android.net.Uri.encode(dateiname)}"
    fun hausaufgabeBearbeiten(dateiname: String) = "hausaufgabe_bearbeiten/${android.net.Uri.encode(dateiname)}"
    fun klausurBearbeiten(dateiname: String) = "klausur_bearbeiten/${android.net.Uri.encode(dateiname)}"
}
