# MK Robot Assistant

A voice-controlled Android assistant with Kinyarwanda-first commands, a
futuristic dark robot-face UI, offline phone-control actions, and a
pluggable AI provider for questions that need real intelligence.

Package name: `com.mkrobot.assistant`

## Why there's no `.apk` in this download

This project was generated in a sandboxed environment with **no internet
access and no Android SDK installed**. Building an APK requires downloading
the Android SDK platform + build tools and resolving Gradle dependencies
from Google's Maven repository — none of which is reachable from here. So
what you're getting is the complete, real source project, not a stub.

Building it yourself takes about two minutes and one command.

## How to build the APK

**Option A — Android Studio (easiest)**
1. Open Android Studio → `Open` → select the unzipped `MKRobotAssistant` folder.
2. Let Gradle sync (first sync downloads the SDK bits it needs).
3. `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`.
4. The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

**Option B — command line**, with Android SDK + Java 17 installed and
`ANDROID_HOME` set:
```bash
cd MKRobotAssistant
gradle wrapper --gradle-version 8.7   # regenerates gradlew (not shipped, see below)
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

> Note: the `gradlew` / `gradlew.bat` wrapper scripts and the wrapper JAR
> aren't included in this ZIP (they're binary/download-fetched files this
> sandbox couldn't produce without network access). Run `gradle wrapper`
> once (any local Gradle install works) to regenerate them, or just open
> the project in Android Studio, which handles this automatically.

## Setting the AI API key

No key is hard-coded anywhere in the source. On first launch, open the
gear icon (Settings) and paste your API key — it's stored using
`EncryptedSharedPreferences` (AES-256) and only ever leaves the device as
an `Authorization`/`x-api-key` header sent straight to the endpoint you
configure (Anthropic's Messages API by default, see
`SecurePrefs.DEFAULT_ENDPOINT`).

## What's implemented

- **Voice loop**: `SpeechRecognizer` → `CommandParser` → action → spoken
  reply via `TextToSpeech`. RW/EN toggle in the top-left.
- **Offline commands** (no network needed): open app, go back, go home,
  open camera, open Settings, flashlight, what time/date is it.
- **Confirmation-gated commands**: phone calls, text messages, and
  Bluetooth toggling all show a Yego/Oya dialog first (`Command.kt` /
  `MainActivity.askConfirmation`).
- **App launcher**: `AppLauncher.kt` scans installed apps and matches both
  real labels and common Kinyarwanda aliases (wasap → WhatsApp, yutubu →
  YouTube, etc).
- **AI fallback**: anything `CommandParser` doesn't recognize locally is
  sent to `HttpAiProvider`, a ~60-line HTTP client built on nothing but
  `HttpURLConnection` and `org.json` — no OkHttp/Retrofit, to keep the APK
  and memory footprint small on low-end phones.
- **Robot UI**: dark theme, animated vector robot face (idle/listening
  states), big mic button, status line that mirrors your spec exactly
  (Ntegereje… / Ndumva… / Ndabitekerezaho… / Ndimo gukora… / Byarangiye.).

## Known limitations (be upfront with yourself before shipping this)

- Android's on-device speech recognizer has patchy-to-nonexistent
  Kinyarwanda language-pack support depending on phone/OEM; the code
  requests `rw-RW` and lets Android fall back to its default if that's not
  installed. Real-world accuracy will depend heavily on the device.
- `CommandParser` is a rule-based phrase matcher, not an NLU model — it
  covers the commands you listed plus a few natural variants, but won't
  generalize far beyond them. Extending it is just adding entries to the
  trigger lists in `CommandParser.kt`.
- Bluetooth cannot be silently toggled by any app on Android 13+; the
  assistant opens the Bluetooth settings panel instead, which is the only
  thing the platform allows.
