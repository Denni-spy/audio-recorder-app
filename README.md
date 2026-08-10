# Audio Recorder App

Fallstudie im Modul *Hybride App-Entwicklung*. Hybride Android-App auf Basis von
**Ionic Capacitor** zum Aufnehmen, Abspielen, Teilen und Verwalten von Audiodateien.

## Verwendete Plugins

| Plugin | Zweck |
| --- | --- |
| `@capawesome-team/capacitor-audio-recorder` | Aufnahme inkl. Pause/Fortsetzen und Berechtigungen |
| `@capawesome-team/capacitor-audio-player` | Wiedergabe, Position/Dauer, `stop`-Listener |
| `@capawesome/capacitor-file-picker` | `copyFile(...)` zum Übernehmen der Aufnahme aus dem Cache |
| `@capacitor/filesystem` | `getUri(...)`, `rename(...)`, Auflisten und Löschen der Dateien |
| `@capacitor/share` | Teilen einer Aufnahme über den System-Dialog |

## Voraussetzungen

* Node.js 20+ und npm
* Android Studio mit Android SDK und **JDK 21**

### Warum genau JDK 21?

Capacitor 8 kompiliert gegen Java 21 und liefert Gradle 8.14.3 mit. Beide Grenzen müssen
eingehalten werden:

| JDK | Ergebnis |
| --- | --- |
| 17 | `error: invalid source release: 21` |
| 21 | funktioniert |
| 25 (in Android Studio gebündelte JBR) | `Unsupported class file major version 69` beim Gradle-Sync |

In Android Studio deshalb unter *Settings → Build, Execution, Deployment → Build Tools →
Gradle → Gradle JDK* ein JDK 21 auswählen (bei Bedarf über „Download JDK…" beziehen).
Auf der Kommandozeile entsprechend `JAVA_HOME` auf ein JDK 21 setzen.
* Ein **physisches Android-Gerät mit Mikrofon** (der Emulator eignet sich nur eingeschränkt)
* Eine gültige **Capawesome-Insiders-Lizenz** für die beiden Audio-Plugins

## Einrichtung

Die beiden Audio-Plugins liegen in einer privaten npm-Registry. Der Lizenzschlüssel dient
gleichzeitig als Auth-Token und ist **bewusst nicht Teil dieses Repositories**. Er muss
einmalig lokal hinterlegt werden:

```bash
npm config set @capawesome-team:registry https://npm.registry.capawesome.io
npm config set //npm.registry.capawesome.io/:_authToken <LIZENZSCHLUESSEL>
```

Anschließend:

```bash
npm install
```

## Build und Start

```bash
npm run sync
```

`npm run sync` baut die Web-App (`vite build` nach `dist/`) und überträgt sie mit
`cap sync` in das Android-Projekt. Danach die App starten:

```bash
npx cap open android
```

Alternativ direkt ein Debug-APK bauen:

```bash
cd android && ./gradlew assembleDebug
```

## Projektstruktur

```
index.html                 Grundgerüst der Oberfläche
src/main.js                Gesamte App-Logik (Aufnahme, Wiedergabe, Dateiverwaltung)
src/style.css              Styling
capacitor.config.json      Capacitor-Konfiguration (webDir = dist)
android/                   Von Capacitor generiertes Android-Projekt
```

## Ablauf einer Aufnahme

1. `AudioRecorder.startRecording()` schreibt die Aufnahme zunächst in den Cache.
2. `stopRecording()` liefert die URI der Cache-Datei.
3. `Filesystem.getUri(...)` erzeugt die Ziel-URI im persistenten Verzeichnis
   `Directory.Data/recordings/`.
4. `FilePicker.copyFile(...)` kopiert die Datei dorthin (die Methode erwartet URIs).
5. `Filesystem.rename(...)` setzt den endgültigen Dateinamen
   (`Aufnahme_JJJJ-MM-TT_HH-MM-SS.<ext>`).
6. Die Liste wird über `Filesystem.readdir(...)` neu geladen – die Aufnahme ist sofort sichtbar.

Da die Dateien in `Directory.Data` liegen, bleiben sie nach einem Neustart der App erhalten.

## Erfüllte Grundanforderungen

* [x] Unterstützt die Plattform Android
* [x] Alle benötigten Berechtigungen werden von der App angefordert (`RECORD_AUDIO`)
* [x] Listenansicht zeigt alle Aufnahmen mit Dateinamen
* [x] Button in der Listenansicht zum Erstellen einer neuen Aufnahme
* [x] Aufnahmen können pausiert und fortgesetzt werden
* [x] Aufnahmen sind nach dem Stopp sofort in der Listenansicht sichtbar
* [x] Aufnahmen können über die Listenansicht abgespielt werden
* [x] Aufnahmen können über die Listenansicht geteilt werden
* [x] Aufnahmen können über die Listenansicht gelöscht werden
* [x] Aufnahmen werden persistiert und nach einem Neustart weiterhin aufgelistet
* [x] Das Abspielen einer Aufnahme kann gestoppt werden
