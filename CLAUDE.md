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
  Vault-Konvention des Nutzers:
  - Termine → `Termine/<Titel>.md`, Frontmatter `datum: "TT-MM-JJJJ"`,
    `tags: [termin, schule?]`
  - Lernnotizen → `<Fach>/JJJJ-MM-TT_<Titel>.md`, Frontmatter
    `fach`/`datum`/`themen`/`tags`
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

- Bearbeiten/Löschen bestehender Termine (aktuell nur Anlegen möglich)
- Volltextsuche über alle Notizen
- Anzeige des Notiz-Inhalts (Body), aktuell in `FachDetailScreen` nur
  Titel + Themen sichtbar
- Fehlerbehandlung, falls die SAF-Berechtigung vom System entzogen wird
  (z. B. nach Umbenennung/Verschieben des Vault-Ordners)
- Gradle-Wrapper (`gradlew`) fehlt bewusst im ursprünglichen ZIP (Binärdatei);
  falls noch nicht geschehen, mit `gradle wrapper` erzeugen und committen,
  damit Builds außerhalb von Android Studio (z. B. CI) reproduzierbar sind
- Ggf. GitHub Actions für automatisierte Debug-Builds einrichten

## Git

Noch kein Repo initialisiert (Stand Übergabe). Falls das hier neu passiert:

```bash
git init
git add .
git commit -m "Initial commit: SchuleVault App-Grundgerüst"
git branch -M main
git remote add origin <URL des Nutzer-Repos>
git push -u origin main
```
