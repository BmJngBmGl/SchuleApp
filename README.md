# SchuleVault

Android-App (Kotlin + Jetpack Compose) für deinen Obsidian-Vault
(`SCHULE-vault`), synchronisiert via OneDrive/OneSync. Futuristisches
Dark-Theme.

## Funktionsüberblick

- **Übersicht**: kommende Termine (farblich nach Dringlichkeit, standardmäßig
  auf 2 begrenzt mit "Alle anzeigen"-Button), IServ-Termine, offene
  Hausaufgaben, anstehende Klausuren (mit Themen-Fortschritt) sowie eine
  Fächer-Liste
- **Termin-Eingabe**: neue Termine anlegen, bearbeiten und löschen, inklusive
  automatischer Push-Erinnerungen (7 Tage / 3 Tage / 24 Stunden vorher)
- **Lernnotiz-Eingabe**: legt eine neue Notiz im gewählten Fachordner an
- **Fach-Detailansicht**: listet alle Notizen eines Fachordners, Body
  ein-/ausklappbar, "Verstanden"-Häkchen pro Notiz
- **Hausaufgaben**: eigene Kategorie mit Fälligkeitsdatum und
  Erledigt-Checkbox (kein Erinnerungs-Countdown wie bei Terminen)
- **Klausuren**: relevante Themen pro Klausur; der Fortschritt wird nicht
  separat gepflegt, sondern aus den "Verstanden"-markierten Lernnotizen des
  jeweiligen Fachs abgeleitet
- **Stundenplan**: zeigt pro Wochentag die nächsten 3 unterschiedlichen
  Fächer plus das dort zuletzt bearbeitete Thema
- **IServ-Kalender**: beliebig viele ICS-Kalender-Abos (Benutzername/Passwort
  per HTTP-Basic-Auth) werden read-only in der Übersicht eingeblendet
- **Volltextsuche** über alle Notizen (Titel, Body, Themen)
- **Automatische Update-Prüfung** beim Start gegen die GitHub Releases dieses
  Repos, mit Download+Installation nach Bestätigung
- **Erinnerungen**: WorkManager plant pro Termin automatisch drei
  Push-Benachrichtigungen (jeweils um 8:00 Uhr lokal)

## Dateizugriff

Die App greift NICHT über einen fest einprogrammierten Pfad auf deinen
Vault zu (moderne Android-Versionen verbieten das ohnehin – Scoped
Storage). Stattdessen wählst du beim ersten Start einmalig deinen
synchronisierten Vault-Ordner über den System-Dateidialog aus (Storage
Access Framework). Die Berechtigung bleibt danach dauerhaft gespeichert.

## Vault-Formate

Alle von der App gelesenen/geschriebenen Dateien sind normale Markdown-Notizen
mit simplem `schlüssel: wert`-Frontmatter (kein vollwertiges YAML nötig).
Wichtig: **Termine/Hausaufgaben/Klausuren nutzen `TT-MM-JJJJ`, Lernnotizen
dagegen `JJJJ-MM-TT`** - unterschiedliche Konventionen, die App unterscheidet
das automatisch nach Notiz-Typ.

**Termin** – `Termine/<Titel>.md`
```
---
datum: "TT-MM-JJJJ"
tags: [termin, schule]
---
```
(`schule` im Tag nur, wenn der Termin als schulisch markiert wurde)

**Lernnotiz** – `<Fach>/JJJJ-MM-TT_<Titel>.md`
```
---
fach: <Fach>
datum: "JJJJ-MM-TT"
themen: [Thema A, Thema B]
tags: [schule]
verstanden: "true"
---
```
(`verstanden` ist optional, wird über die Checkbox in der Fach-Detailansicht
gesetzt und steuert den Themen-Fortschritt bei Klausuren)

**Hausaufgabe** – `Hausaufgaben/<Titel>.md`
```
---
fach: <Fach>
faelligAm: "TT-MM-JJJJ"
erledigt: "false"
tags: [hausaufgabe]
---
```

**Klausur** – `Klausuren/<Titel>.md`
```
---
fach: <Fach>
datum: "TT-MM-JJJJ"
themen: [Thema A, Thema B]
tags: [klausur, schule]
---
```

**Stundenplan** – `Organisation/Stundenplan.md` (einzige Datei, keine
Frontmatter-Pflichtfelder, Fächer als nummerierte Liste unter einer
Wochentags-Überschrift)
```
---
tags: [stundenplan]
---

## Montag
1. Mathematik
2. Deutsch
3. Sport
```

## Erste Einrichtung auf dem Handy

Beim ersten Start fragt die App nach deinem Vault-Ordner (im
Systemdialog zu deinem OneDrive/OneSync-Dokumente-Ordner navigieren,
den `SCHULE-vault`-Ordner selbst auswählen, nicht einen übergeordneten
Ordner). Danach lädt die App automatisch alle Fächer + Termine. IServ-Kalender
und Stundenplan sind optional und werden erst nach Einrichtung (Zahnrad-Icon
bzw. `Organisation/Stundenplan.md`) angezeigt.

## Mögliche Erweiterungen

- Erinnerungen auch für IServ-Termine (aktuell rein lesend)
- Uhrzeiten im Stundenplan statt nur Reihenfolge
