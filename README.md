# Nadya: History Practice

A daily history practice app with Nadya, an animated matryoshka doll. It covers four units of history: Changes in medicine c1848–c1948, Germany 1918–45, Russia 1905–24 and the USSR 1924–53. Short daily lessons, streaks, spaced repetition and a timeline of 284 key events. Practise one unit, or all four mixed together.

## Features

- **Daily challenge:** 8 questions in about 3 minutes, in four kinds: which event, why it mattered, which year, and putting events in order.
- **Spaced repetition:** due reviews come first. An event counts as learned after three correct answers on different days.
- **Nadya:** her mood follows the streak (happy, waiting, worried, sad, very sad). She laughs when tapped and sets off fireworks for right answers.
- **Timeline:** every event in order, opened at the event just answered. Includes the **Timeline Mini Test**, where you place events among their neighbours.
- **Celebrations:** Nadya cheers right answers, is kind about mistakes, peeks in at halfway, and celebrates combos (3, 5 and 8 in a row, with bonus XP), the end of each challenge and the streak.
- **Doll collection:** each unit has its own matryoshka, who grows as you learn its events: carved, painted, decorated (with a keepsake: Nightingale's lamp, a white rose, bread and wheat, a little tractor), then golden. When all four are golden, they nest together inside Nadya.
- **First-time tour** of the home screen.
- **Android app:** daily reminders at a chosen time, a home-screen widget showing Nadya's mood, and automatic updates of content and screens.
- **Progress transfer:** a copy-and-paste code moves progress between the web and Android versions. Progress is stored only on the device; there are no accounts and no data is collected.

## Install

- **Android:** download [nadya.apk](https://github.com/andy-butcher-dod/nadya/releases/latest/download/nadya.apk) on the phone, open it and allow installs from Chrome.
- **iPhone:** open https://andy-butcher-dod.github.io/nadya/ in Safari, tap Share, then Add to Home Screen.
- **Any browser:** https://andy-butcher-dod.github.io/nadya/

## How it's built

| Part | Where |
| --- | --- |
| The app (all screens, questions and event data) | `index.html`, published by GitHub Pages |
| Web install files | `manifest.webmanifest`, `sw.js`, icons |
| Android shell (reminders, widget, updates) | `android/`, a Java app around the web app |
| Android build | `.github/workflows/android.yml` builds a signed APK and AAB on every change and publishes them as the `android-app` release |

The Android app downloads newer versions of `index.html` from GitHub Pages by itself, so only changes inside `android/` need a new APK. Signing uses the `KEYSTORE_B64` and `KEYSTORE_PASSWORD` repository secrets.

The `version-1.7` branch keeps version 1.7 as a fallback.
