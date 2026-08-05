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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.wunstorf.schulevault.ui.NavRoutes
import de.wunstorf.schulevault.ui.screens.FachDetailScreen
import de.wunstorf.schulevault.ui.screens.LernnotizEingabeScreen
import de.wunstorf.schulevault.ui.screens.OrdnerAuswaehlenScreen
import de.wunstorf.schulevault.ui.screens.TerminEingabeScreen
import de.wunstorf.schulevault.ui.screens.UebersichtScreen
import de.wunstorf.schulevault.ui.theme.SchuleVaultTheme

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
    val navController = rememberNavController()

    if (uiState.vaultUri == null) {
        // Kein Vault-Ordner ausgewaehlt -> Onboarding-Bildschirm statt
        // NavHost, da ohne Ordner ohnehin keine der anderen Ansichten
        // sinnvoll etwas anzeigen koennte.
        OrdnerAuswaehlenScreen(onOrdnerAuswaehlenGeklickt = onOrdnerAuswaehlenGeklickt)
        return
    }

    NavHost(navController = navController, startDestination = NavRoutes.UEBERSICHT) {
        composable(NavRoutes.UEBERSICHT) {
            UebersichtScreen(
                uiState = uiState,
                onTerminEingabeClick = { navController.navigate(NavRoutes.TERMIN_EINGABE) },
                onLernnotizEingabeClick = { navController.navigate(NavRoutes.LERNNOTIZ_EINGABE) },
                onFachClick = { fach -> navController.navigate(NavRoutes.fachDetail(fach)) },
                onOrdnerWechselnClick = onOrdnerAuswaehlenGeklickt,
                onNeuLadenClick = { viewModel.ordnerNeuLaden() }
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
