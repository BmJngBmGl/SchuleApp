package de.wunstorf.schulevault

import android.app.Application

/**
 * Aktuell bewusst schlank gehalten: WorkManager initialisiert sich ueber
 * seinen eingebauten ContentProvider automatisch selbst, es ist also keine
 * manuelle Konfiguration hier noetig. Die Klasse existiert als Erweiterungs-
 * punkt fuer spaeter (z. B. globales Error-Logging).
 */
class SchuleVaultApp : Application()
