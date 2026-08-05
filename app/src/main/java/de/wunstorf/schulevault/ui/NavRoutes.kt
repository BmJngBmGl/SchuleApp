package de.wunstorf.schulevault.ui

/** Zentrale Sammlung aller Navigations-Routennamen, tippsicherer als rohe Strings verstreut im Code. */
object NavRoutes {
    const val UEBERSICHT = "uebersicht"
    const val TERMIN_EINGABE = "termin_eingabe"
    const val LERNNOTIZ_EINGABE = "lernnotiz_eingabe"
    const val FACH_DETAIL = "fach_detail/{fach}"

    fun fachDetail(fach: String) = "fach_detail/$fach"
}
