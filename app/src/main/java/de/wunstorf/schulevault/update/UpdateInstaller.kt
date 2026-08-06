package de.wunstorf.schulevault.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

/**
 * Laedt eine Release-APK per DownloadManager herunter und startet danach den
 * System-Installationsdialog. Fehlt die "Unbekannte Apps installieren"-
 * Berechtigung, zeigt Android das automatisch selbst an - keine manuelle
 * canRequestPackageInstalls()-Vorpruefung noetig.
 */
object UpdateInstaller {

    private const val DATEINAME = "schulevault-update.apk"

    fun downloadUndInstallieren(context: Context, downloadUrl: String) {
        val appContext = context.applicationContext
        val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val zielDatei = File(appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DATEINAME)
        if (zielDatei.exists()) zielDatei.delete()

        val request = DownloadManager.Request(Uri.parse(downloadUrl))
            .setTitle("SchuleVault-Update")
            .setDestinationUri(Uri.fromFile(zielDatei))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val abgeschlosseneId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (abgeschlosseneId == downloadId) {
                    receiverContext.unregisterReceiver(this)
                    installiere(receiverContext, zielDatei)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun installiere(context: Context, apkDatei: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkDatei
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(installIntent)
    }
}
