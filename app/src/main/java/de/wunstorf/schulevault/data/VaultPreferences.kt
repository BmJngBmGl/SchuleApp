package de.wunstorf.schulevault.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "schulevault_settings")

/**
 * Merkt sich die einmal ausgewaehlte SAF-Baum-URI des Vault-Ordners, damit
 * der Nutzer nicht bei jedem App-Start erneut den Ordner auswaehlen muss.
 * Die persistierbare Lese-/Schreibberechtigung selbst wird separat in
 * MainActivity via contentResolver.takePersistableUriPermission() erteilt.
 */
class VaultPreferences(private val context: Context) {

    private val vaultUriKey = stringPreferencesKey("vault_uri")

    val vaultUri: Flow<String?> = context.dataStore.data.map { it[vaultUriKey] }

    suspend fun setVaultUri(uri: String) {
        context.dataStore.edit { it[vaultUriKey] = uri }
    }

    suspend fun clearVaultUri() {
        context.dataStore.edit { it.remove(vaultUriKey) }
    }
}
