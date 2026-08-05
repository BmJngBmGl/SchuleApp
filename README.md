# SchuleVault

Android-App (Kotlin + Jetpack Compose) für deinen Obsidian-Vault
(`SCHULE-vault`), synchronisiert via OneDrive/OneSync. Futuristisches
Dark-Theme, zwei Eingabeformulare, Termin-Erinnerungen (7 Tage / 3 Tage /
24 Stunden vorher).

## Funktionsüberblick

- **Übersicht**: kommende Termine (farblich nach Dringlichkeit) + Fächer-Liste
- **Termin-Eingabe**: legt eine neue `.md`-Datei in `Termine/` an, im selben
  Frontmatter-Format wie deine bestehenden Notizen (`datum: "TT-MM-JJJJ"`,
  `tags: [termin, schule]`)
- **Lernnotiz-Eingabe**: legt eine neue Notiz im gewählten Fachordner an
  (`JJJJ-MM-TT_Titel.md`, Frontmatter mit `fach`/`themen`/`tags`)
- **Fach-Detailansicht**: listet alle Notizen eines Fachordners
- **Erinnerungen**: WorkManager plant pro Termin automatisch drei
  Push-Benachrichtigungen (jeweils um 8:00 Uhr lokal)

## Dateizugriff

Die App greift NICHT über einen fest einprogrammierten Pfad auf deinen
Vault zu (moderne Android-Versionen verbieten das ohnehin – Scoped
Storage). Stattdessen wählst du beim ersten Start einmalig deinen
synchronisierten Vault-Ordner über den System-Dateidialog aus (Storage
Access Framework). Die Berechtigung bleibt danach dauerhaft gespeichert.

## Bauen (Android Studio)

1. Android Studio öffnen → „Open" → diesen Ordner (`SchuleVault/`) auswählen
2. Android Studio fragt vermutlich nach dem Gradle-Wrapper (`gradlew`) -
   die Binärdatei dafür (`gradle-wrapper.jar`) ist bewusst nicht im Repo,
   da sie kein Textformat ist. Einfach "Use Gradle from: 'gradle-wrapper.properties' file"
   bestätigen bzw. Android Studios Vorschlag "Create Gradle Wrapper"
   annehmen - dauert einmalig ca. 10 Sekunden
3. Gradle-Sync abwarten (läuft automatisch)
4. Gerät/Emulator wählen → Run ▶️

Getestet gegen minSdk 26 (Android 8.0) / targetSdk 35, Kotlin 2.0.20,
Compose BOM 2024.09.03.

## Erste Einrichtung auf dem Handy

Beim ersten Start fragt die App nach deinem Vault-Ordner (im
Systemdialog zu deinem OneDrive/OneSync-Dokumente-Ordner navigieren,
den `SCHULE-vault`-Ordner selbst auswählen, nicht einen übergeordneten
Ordner). Danach lädt die App automatisch alle Fächer + Termine.

## Zu GitHub pushen

Claude hat hier in diesem Chat keinen GitHub-Zugriff und kann daher nicht
selbst pushen. Nach jeder Änderung, die du hier bekommst: Projektordner
über den bestehenden Git-Verlauf aktualisieren und selbst pushen, z. B.:

```bash
cd SchuleVault
git add .
git commit -m "Kurze Beschreibung der Änderung"
git push
```

Falls du automatisierte Pushes möchtest, ist **Claude Code**
(claude.ai/code bzw. die Desktop-App) der bessere Weg – das läuft lokal
bei dir mit echtem Terminal-/Git-Zugriff.

## Offene Punkte / mögliche Erweiterungen

- Bearbeiten/Löschen bestehender Termine (aktuell nur Anlegen)
- Volltextsuche über alle Notizen
- Anzeige des Notiz-Inhalts (Body) in der Fach-Detailansicht, aktuell nur
  Titel + Themen
- App-weite Fehlerbehandlung, falls die SAF-Berechtigung vom System
  entzogen wird (z. B. nach Vault-Ordner-Umbenennung)
