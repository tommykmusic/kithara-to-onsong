# Kithara to OnSong

An unofficial Android app that imports a selected Kithara arrangement into OnSong as an editable, transposable ChordPro chart. It preserves Greek text, chord positions, and the key and meter when the arrangement provides them.

## Install

Use an Android 9 or newer device with [OnSong for Android](https://onsongapp.com/docs/platforms/android/) installed. Download **Kithara-to-OnSong.apk** from the [latest GitHub release](https://github.com/tommykmusic/kithara-to-onsong/releases/latest) and open it on your device. Android may ask you to allow installation through the browser or file manager you used to open it.

## Import a song

1. Open **Kithara to OnSong**. Search using **Εύρεση μέσω kithara.to** and open the arrangement you want. You can also share a Kithara song link to the app from your Android browser.
2. Tap **Add to OnSong**, then tap **Continue** in OnSong.
3. Find the chart in the **Kithara Inbox** book in your active OnSong library. You can edit or transpose it in OnSong.

The app includes the arrangement link in the chart and gives identical exports the same filename, even when Kithara changes the link. OnSong may prompt you if that filename has already been imported. If a song page has several arrangements, select the one you want before importing. If a key or meter is unavailable, the app leaves that field out.

## Data and chart rights

The app loads Kithara and the web resources its pages use. Chart conversion happens on your device. The app has no backend, account, or analytics of its own. It stores the exported chart in its private cache and grants OnSong temporary read access when you tap **Add to OnSong**. Old cached exports are removed on a later app launch.

The repository and APK distribute the software. Song lyrics, charts, and arrangements remain subject to their own rights and Kithara’s use notices. The project is independent of Kithara and OnSong.

## Build and test

Open this folder as a project in Android Studio with JDK 17 and Android SDK 35. Choose the `debug` variant to build a test APK. The included Gradle Wrapper pins the Gradle version and checks the download checksum.

- macOS or Linux: `sh ./gradlew assembleDebug`
- Windows: `.\gradlew.bat assembleDebug`
- Parser tests with Node.js 22 or later: `node --test extract-chart.test.mjs`

## Credits and license

Inspired by [dzogas’s Kithara Lyrics & Chords Scraper](https://github.com/dzogas/kithara-lyrics-chords-scraper). This Android project is distributed under the [MIT License](LICENSE). See [third-party notices](THIRD_PARTY_NOTICES.txt) for the upstream notice.
