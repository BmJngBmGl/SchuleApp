package de.wunstorf.schulevault.data

import kotlinx.datetime.LocalDate

/** Ein aus einem IServ-ICS-Kalender gelesener Termin (rein lesend, kein Vault-Bezug). */
data class IServTermin(
    val uid: String,
    val titel: String,
    val datum: LocalDate,
    val kalenderLabel: String
)
