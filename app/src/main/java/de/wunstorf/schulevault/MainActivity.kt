package de.wunstorf.schulevault

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import de.wunstorf.schulevault.ui.NavRoutes
import de.wunstorf.schulevault.ui.screens.EinstellungenScreen
import de.wunstorf.schulevault.ui.screens.FachDetailScreen
import de.wunstorf.schulevault.ui.screens.HausaufgabeEingabeScreen
import de.wunstorf.schulevault.ui.screens.KlausurEingabeScreen
import de.wunstorf.schulevault.ui.screens.LernnotizEingabeScreen
import de.wunstorf.schulevault.ui.screens.OrdnerAuswaehlenScreen
import de.wunstorf.schulevault.ui.screens.StundenplanScreen
import de.wunstorf.schulevault.ui.screens.SucheScreen
import de.wunstorf.schulevault.ui.screens.TerminEingabeScreen
import de.wunstorf.schulevault.ui.screens.UebersichtScreen
import de.wunstorf.schulevault.ui.theme.SchuleVaultTheme
import de.wunstorf.schulevault.update.UpdateInstaller

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Systemdialog zur Ordnerauswahl (Storage Access Framework). Der Nutzer
    // navigiert dort selbst zu seinem OneDrive/OneSync-synchronisierten
    // Vault-Ordner (z. B. unterhalb von "Dokumente") und bestaetigt.
    private val ordnerAuswahlLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            // Dauerhafte Lese-/Schreibrechte sichern, sonst verfaellt der
            // Zugriff spaetestens nach einem Geraete-Neustart wieder.
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.vaultOrdnerAusgewaehlt(uri)
        }
    }

    private val benachrichtigungsRechtLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Ergebnis wird nicht extra ausgewertet - ohne Recht bleiben
           Erinnerungen einfach stumm, der Rest der App funktioniert weiter. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            benachrichtigungsRechtLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SchuleVaultTheme {
                SchuleVaultApp(
                    viewModel = viewModel,
                    onOrdnerAuswaehlenGeklickt = { ordnerAuswahlLauncher.launch(null) }
                )
            }
        }
    }
}

@Composable
private fun SchuleVaultApp(
    viewModel: MainViewModel,
    onOrdnerAuswaehlenGeklickt: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val iservTermine by viewModel.iservTermine.collectAsState()
    val iservSyncLaeuft by viewModel.iservSyncLaeuft.collectAsState()
    val navController = rememberNavController()
    val context = LocalContext.current

    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.updateIgnorieren() },
            title = { Text("Update verfügbar") },
            text = { Text("SchuleVault ${info.versionName} ist verfügbar. Jetzt aktualisieren?") },
            confirmButton = {
                TextButton(onClick = {
                    UpdateInstaller.downloadUndInstallieren(context, info.downloadUrl)
                    viewModel.updateIgnorieren()
                }) { Text("Aktualisieren") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.updateIgnorieren() }) { Text("Später") }
            }
        )
    }

    if (uiState.vaultUri == null) {
        // Kein Vault-Ordner ausgewaehlt -> Onboarding-Bildschirm statt
        // NavHost, da ohne Ordner ohnehin keine der anderen Ansichten
        // sinnvoll etwas anzeigen koennte.
        OrdnerAuswaehlenScreen(
            onOrdnerAuswaehlenGeklickt = onOrdnerAuswaehlenGeklickt,
            fehlermeldung = uiState.fehlermeldung
        )
        return
    }

    NavHost(navController = navController, startDestination = NavRoutes.UEBERSICHT) {
        composable(NavRoutes.UEBERSICHT) {
            // Uebersicht + Stundenplan als zwei Seiten eines Pagers statt
            // eigener Nav-Route: von rechts nach links wischen zeigt den
            // Stundenplan, das Kalender-Icon oben springt alternativ dorthin.
            val pagerState = rememberPagerState(pageCount = { 2 })
            val pagerScope = rememberCoroutineScope()

            HorizontalPager(state = pagerState) { seite ->
                if (seite == 0) {
                    UebersichtScreen(
                        uiState = uiState,
                        onTerminEingabeClick = { navController.navigate(NavRoutes.TERMIN_EINGABE) },
                        onLernnotizEingabeClick = { navController.navigate(NavRoutes.LERNNOTIZ_EINGABE) },
                        onFachClick = { fach -> navController.navigate(NavRoutes.fachDetail(fach)) },
                        onOrdnerWechselnClick = onOrdnerAuswaehlenGeklickt,
                        onNeuLadenClick = { viewModel.ordnerNeuLaden() },
                        onSucheClick = { navController.navigate(NavRoutes.SUCHE) },
                        onTerminClick = { termin -> navController.navigate(NavRoutes.terminBearbeiten(termin.note.fileName)) },
                        onStundenplanClick = { pagerScope.launch { pagerState.animateScrollToPage(1) } },
                        onHausaufgabeEingabeClick = { navController.navigate(NavRoutes.HAUSAUFGABE_EINGABE) },
                        onHausaufgabeClick = { hausaufgabe ->
                            navController.navigate(NavRoutes.hausaufgabeBearbeiten(hausaufgabe.note.fileName))
                        },
                        onHausaufgabeErledigtToggle = { hausaufgabe, erledigt ->
                            viewModel.hausaufgabeErledigtSetzen(hausaufgabe, erledigt)
                        },
                        iservTermine = iservTermine,
                        onEinstellungenClick = { navController.navigate(NavRoutes.EINSTELLUNGEN) },
                        onKlausurEingabeClick = { navController.navigate(NavRoutes.KLAUSUR_EINGABE) },
                        onKlausurClick = { klausur ->
                            navController.navigate(NavRoutes.klausurBearbeiten(klausur.note.fileName))
                        }
                    )
                } else {
                    StundenplanScreen(
                        vaultUri = uiState.vaultUri,
                        onZurueck = { pagerScope.launch { pagerState.animateScrollToPage(0) } }
                    )
                }
            }
        }
        composable(NavRoutes.KLAUSUR_EINGABE) {
            KlausurEingabeScreen(
                verfuegbareFaecher = uiState.faecher,
                onSpeichern = { fach, titel, datum, relevanteThemen, callback ->
                    viewModel.neueKlausurAnlegen(fach, titel, datum, relevanteThemen, callback)
                },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(
            route = NavRoutes.KLAUSUR_BEARBEITEN,
            arguments = listOf(androidx.navigation.navArgument("dateiname") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val dateiname = backStackEntry.arguments?.getString("dateiname") ?: return@composable
            val klausur = uiState.klausuren.find { it.note.fileName == dateiname } ?: return@composable
            KlausurEingabeScreen(
                verfuegbareFaecher = uiState.faecher,
                bearbeitenKlausur = klausur,
                onSpeichern = { fach, titel, datum, relevanteThemen, callback ->
                    viewModel.klausurAktualisieren(klausur, fach, titel, datum, relevanteThemen, callback)
                },
                onLoeschen = { callback -> viewModel.klausurLoeschen(klausur, callback) },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.EINSTELLUNGEN) {
            EinstellungenScreen(
                iservTerminAnzahl = iservTermine.size,
                iservSyncLaeuft = iservSyncLaeuft,
                onSpeichern = { benutzername, passwort, kalenderListe ->
                    viewModel.iservEinstellungenSpeichern(benutzername, passwort, kalenderListe)
                },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.HAUSAUFGABE_EINGABE) {
            HausaufgabeEingabeScreen(
                verfuegbareFaecher = uiState.faecher,
                onSpeichern = { fach, titel, faelligAm, text, callback ->
                    viewModel.neueHausaufgabeAnlegen(fach, titel, faelligAm, text, callback)
                },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(
            route = NavRoutes.HAUSAUFGABE_BEARBEITEN,
            arguments = listOf(androidx.navigation.navArgument("dateiname") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val dateiname = backStackEntry.arguments?.getString("dateiname") ?: return@composable
            val hausaufgabe = uiState.hausaufgaben.find { it.note.fileName == dateiname } ?: return@composable
            HausaufgabeEingabeScreen(
                verfuegbareFaecher = uiState.faecher,
                bearbeitenHausaufgabe = hausaufgabe,
                onSpeichern = { fach, titel, faelligAm, text, callback ->
                    viewModel.hausaufgabeAktualisieren(hausaufgabe, fach, titel, faelligAm, text, callback)
                },
                onLoeschen = { callback -> viewModel.hausaufgabeLoeschen(hausaufgabe, callback) },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.TERMIN_EINGABE) {
            TerminEingabeScreen(
                onSpeichern = { titel, datum, istSchulisch, text, callback ->
                    viewModel.neuenTerminAnlegen(titel, datum, istSchulisch, text, callback)
                },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(
            route = NavRoutes.TERMIN_BEARBEITEN,
            arguments = listOf(androidx.navigation.navArgument("dateiname") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val dateiname = backStackEntry.arguments?.getString("dateiname") ?: return@composable
            val termin = uiState.termine.find { it.note.fileName == dateiname } ?: return@composable
            TerminEingabeScreen(
                bearbeitenTermin = termin,
                onSpeichern = { titel, datum, istSchulisch, text, callback ->
                    viewModel.terminAktualisieren(termin, titel, datum, istSchulisch, text, callback)
                },
                onLoeschen = { callback -> viewModel.terminLoeschen(termin, callback) },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.SUCHE) {
            val sucheErgebnisse by viewModel.sucheErgebnisse.collectAsState()
            SucheScreen(
                ergebnisse = sucheErgebnisse,
                onQueryChange = { query -> viewModel.sucheNotizen(query) },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(NavRoutes.LERNNOTIZ_EINGABE) {
            LernnotizEingabeScreen(
                verfuegbareFaecher = uiState.faecher,
                onSpeichern = { fach, titel, themen, text, callback ->
                    viewModel.neueLernnotizAnlegen(fach, titel, themen, text, callback)
                },
                onZurueck = { navController.popBackStack() }
            )
        }
        composable(
            route = NavRoutes.FACH_DETAIL,
            arguments = listOf(androidx.navigation.navArgument("fach") {
                type = androidx.navigation.NavType.StringType
            })
        ) { backStackEntry ->
            val fach = backStackEntry.arguments?.getString("fach") ?: return@composable
            FachDetailScreen(
                fach = fach,
                vaultUri = uiState.vaultUri,
                onZurueck = { navController.popBackStack() }
            )
        }
    }
}
