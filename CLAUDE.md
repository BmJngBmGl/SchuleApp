# SchuleVault - Projektkontext für Claude Code

Diese Datei wird von Claude Code beim Öffnen dieses Ordners automatisch
gelesen. Sie fasst zusammen, was in einer vorherigen Unterhaltung im
normalen Claude-Chat (claude.ai) bereits entstanden ist, damit hier nahtlos
weitergearbeitet werden kann - inklusive echtem `git push`.

## Auftrag (vom Nutzer)

Android-App, die den Obsidian-Vault des Nutzers (`SCHULE-vault`,
synchronisiert via OneDrive/OneSync in den Documents-Ordner des Handys)
liest und anzeigt. Zwei Eingabeformulare: eines für neue Termine, eines für
Notizen zum Lernstoff. Termine sollen Push-Benachrichtigungen 24 Stunden,
3 Tage und 7 Tage vorher auslösen. Futuristisches Theme, Dark Mode. Ergebnis
soll ins GitHub-Repo des Nutzers, Änderungen per `git push` (Versionshistorie
soll erhalten bleiben).

## Warum diese Datei existiert

Im claude.ai-Chat, in dem die App entstanden ist, gab es keinen
GitHub-Connector - Claude dort konnte den Code nur als ZIP bereitstellen,
nicht selbst pushen. Der Nutzer wechselt deshalb für die Weiterarbeit (inkl.
echtem Git-Zugriff) zu Claude Code.

## Architektur-Entscheidungen (bereits umgesetzt)

- **Kotlin + Jetpack Compose**, Material 3, minSdk 26 / targetSdk 35
- **Kein Light-Theme** - App ist bewusst nur Dark Mode (`ui/theme/`)
- **Dateizugriff über Storage Access Framework** (`ACTION_OPEN_DOCUMENT_TREE`),
  bewusst NICHT über einen hartcodierten Pfad - Scoped Storage auf modernen
  Android-Versionen verbietet direkten Zugriff auf fremde Sync-Ordner ohnehin.
  Der Nutzer wählt den Vault-Ordner einmalig aus (`MainActivity`), die
  Berechtigung wird dauerhaft gespeichert (`VaultPreferences`, DataStore).
- **Frontmatter-Parser** (`data/FrontmatterParser.kt`): bewusst kein
  vollwertiger YAML-Parser, da das bestehende Vault-Frontmatter nur simple
  `schlüssel: wert`-Zeilen nutzt.
- **Schreibformat für neue Dateien** orientiert sich exakt an der bestehenden
  Vault-Konvention des Nutzers. Die verbindliche Referenz dafür liegt NICHT
  hier, sondern direkt im Vault selbst: `Organisation/Vault-Formatierung.md`
  dokumentiert das exakte Frontmatter für Termine/Lernnotizen/Hausaufgaben/
  Klausuren (inkl. der unterschiedlichen Datumsformate `TT-MM-JJJJ` vs.
  `JJJJ-MM-TT`). Bei Änderungen am Schreibformat in `VaultRepository.kt`
  IMMER auch diese Vault-Datei aktualisieren, damit beide synchron bleiben.
- **Erinnerungen** über WorkManager (`notifications/`), nicht AlarmManager -
  kein `SCHEDULE_EXACT_ALARM` nötig, WorkManager übersteht Reboots von sich
  aus. Pro Termin werden bis zu 3 `OneTimeWorkRequest`s mit `initialDelay`
  eingeplant (7T/3T/24h vorher, jeweils 8:00 Uhr lokal).

## Bereits behobene Build-Fehler (nicht erneut einführen)

1. `kotlinOptions { jvmTarget = "17" }` in `android {}` ist deprecated →
   ersetzt durch `kotlin { compilerOptions { jvmTarget.set(...) } }` als
   eigener Top-Level-Block in `app/build.gradle.kts`.
2. `res/values/themes.xml` referenziert `Theme.Material3.DayNight.NoActionBar`
   - dieser Style kommt aus der klassischen View-basierten Material-
   Components-Bibliothek, NICHT aus `androidx.compose.material3`. Fix:
   `implementation("com.google.android.material:material:1.12.0")` wurde
   ergänzt (wird nur für dieses eine XML-Basistheme gebraucht, sonst ist die
   App komplett Compose).

## Bekannte offene Punkte / mögliche nächste Schritte

Alle Punkte aus der ursprünglichen Übergabe sind umgesetzt:

- Bearbeiten/Löschen bestehender Termine: `TerminEingabeScreen` hat jetzt einen
  optionalen `bearbeitenTermin`-Modus (Felder vorbefüllt, Speichern überschreibt/
  benennt die bestehende Datei um) sowie einen Löschen-Button mit Bestätigungsdialog.
  Klick auf eine Terminkarte in `UebersichtScreen` navigiert dorthin.
- Volltextsuche über alle Notizen: neuer `SucheScreen`, erreichbar über das
  Such-Icon in der `UebersichtScreen`-TopAppBar. `VaultRepository.sucheAlleNotizen`
  durchsucht Titel/Body/Themen über alle Ordner hinweg (inkl. "Termine").
- Anzeige des Notiz-Inhalts (Body): Notizkarten in `FachDetailScreen` und
  `SucheScreen` sind anklickbar und klappen den Body ein/aus (`GlowCard` hat dafür
  einen optionalen `onClick`-Parameter bekommen).
- Fehlerbehandlung bei entzogener SAF-Berechtigung:
  `VaultRepository.hatGueltigeBerechtigung` prüft `persistedUriPermissions` vor
  jedem Ladevorgang; bei Verlust wird die gespeicherte URI gelöscht und der
  Nutzer landet mit einer erklärenden Fehlermeldung zurück auf
  `OrdnerAuswaehlenScreen`.
- Gradle-Wrapper ist erzeugt und committed (Gradle 8.7, passend zu AGP 8.6.0).
- GitHub Actions: `.github/workflows/android-debug-build.yml` baut bei jedem
  Push/PR auf `main` die Debug-APK (JDK 17) und lädt sie als Artifact hoch.

## Git

Repo ist initialisiert und mit einem GitHub-Remote verbunden (Repo-URL siehe
`git remote -v`). Falls das hier neu passiert:

```bash
git init
git add .
git commit -m "Initial commit: SchuleVault App-Grundgerüst"
git branch -M main
git remote add origin <URL des Nutzer-Repos>
git push -u origin main
```
