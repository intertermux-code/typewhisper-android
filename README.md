# TypeWhisper for Android (unofficial port)

Dictation for Android that puts text where you are typing: hold a button, talk,
and the words land in the app you were already in.

It is a native Kotlin port of [TypeWhisper for Mac](https://github.com/TypeWhisper/typewhisper-mac).
Not affiliated with them — see [Attribution](#attribution).

## Completely free, no keys needed

The default engine runs entirely on your phone — no account, no API key, no
subscription. Install the APK, open the app, and download the speech model once
over Wi-Fi (~32 MB). After that, dictation works fully offline, and your audio
never leaves the device. Paid APIs (Groq, OpenAI) and the Mac relay are optional
extras in Settings — setup finishes without any of them.

## What it does

- **Runs fully on the phone.** whisper.cpp is compiled into the APK. The default
  engine needs no account, no key, and no network once its model is downloaded;
  the audio never leaves the device unless you point it at one of the remote engines.
- **A voice keyboard.** An input method you can select in any app, with a mic key
  that records and inserts the transcript straight into the field you were typing
  in — no clipboard round trip.
- **A bubble you can tap from anywhere.** An overlay mic (needs the "display over
  other apps" permission), a Quick Settings tile, and a notification. Where there
  is no text field to write into, the transcript goes to the clipboard and the
  notification invites you to tap to paste.
- **Transcribe audio files.** Pick a recording, get text, without playing it back
  in real time.
- **Profiles.** Save an engine, a language, and the number-normalization switch
  together, and switch between them when you move from the desk to the car.
- **Post-processing that fixes what speech-to-text gets wrong.** Number
  normalization (spoken "twenty twenty six" to `2026`, in several languages) and
  your own substitution list for the words the model keeps mishearing.
- **Use the Mac as the engine.** If TypeWhisper is running on a Mac and you want
  its engines or its hardware, a small relay script bridges the phone to it over
  your network. See [`relay/README.md`](relay/README.md).
- **History and usage stats**, kept on-device in a Room database. API keys are
  kept in the encrypted preference store and are never written to logcat.

## Install

**APK:** grab the [latest release APK](https://github.com/intertermux-code/typewhisper-android/releases) and install it.
The app needs Android 8.0 (API 26) or newer. There's no signing config in the
repo — what's published is debug-built, and it isn't on the Play Store.

**From source:**

```bash
git clone https://github.com/intertermux-code/typewhisper-android.git
cd typewhisper-android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or skip the install step with `./gradlew :app:installDebug` against a connected
device.

Toolchain: JDK 17, Android SDK 34, Gradle 8.7 (wrapper included). Building the
on-device engine also needs NDK 26.3.11579264 and CMake 3.31.5 — both installable
through Android Studio's SDK Manager. Pins live in `gradle/libs.versions.toml`.
`local.properties` is not in the repo; that's the one file you have to write.

### First run

1. Open the app — the on-device engine is already selected.
2. On the Engine page, tap "Download the tiny-q5_1 model" (~32 MB, once, over Wi-Fi).
3. Hold the mic button and talk. No account, no key, and after the download no
   network either.

## Engines

Pick one in Settings. Rows that aren't ready show what they're still waiting on
and stay disabled until you fill it in.

**On-device (whisper.cpp)** — the default. Vendored whisper.cpp v1.7.4, CPU only,
arm64-v8a and x86_64. Models are downloaded on demand from
[Hugging Face](https://huggingface.co/ggerganov/whisper.cpp) (`tiny-q5_1` to start,
`base-q5_1` if you want better accuracy and can spare the RAM). Slowest to first
token of the bunch, and the only one that works on a plane.

**TypeWhisper on Mac** — talks to the Mac app through the relay script. Two things
have to be true: TypeWhisper's HTTP server has to be enabled on the Mac, and
`relay/tw_relay.py` has to be running somewhere the phone can reach. Then you type
that address into Settings. The app ships with no Mac hardcoded, because it can't
know yours.

**Groq / OpenAI / custom endpoint** — cloud transcription. These are inert until you
paste a key (and for the custom engine, a base URL). Groq points at
`whisper-large-v3-turbo` on an OpenAI-compatible `/audio/transcriptions` endpoint;
OpenAI uses `gpt-4o-transcribe`; the custom engine will talk to anything that
follows the same request shape and takes an optional bearer token.

**Mic input** — by default Android picks the mic. Settings can list the connected
input devices (headset, USB, Bluetooth) and pin one of them instead.

## Layout

| Module | What's in it |
| --- | --- |
| `:engine-api` | The engine contract: `AudioData`, `EnginePlugin`, `EngineException`. Pure Kotlin/JVM, no Android imports. |
| `:core` | Number normalization and the transcript rewriting rules. |
| `:capture` | `AudioRecord` capture, sample-rate negotiation, resampling to the 16 kHz mono the contract asks for. |
| `:storage` | Room for history and stats; encrypted store for keys. |
| `:engines:whispercpp` | On-device engine, with the vendored native sources. |
| `:engines:cloud` | One plugin for any OpenAI-compatible endpoint. |
| `:engines:mac` | The Mac harness client. |
| `:ime` | The input method. |
| `:app` | Compose UI, the recording service, the bubble, the tile. |

## Attribution

Unofficial port, not affiliated with TypeWhisper. Inspired by TypeWhisper for Mac
(GPLv3). If you're the upstream maintainer and want this under TypeWhisper, get in
touch.

whisper.cpp and ggml are MIT licensed and vendored under
`engines/whispercpp/src/main/cpp`; see the `NOTICE.txt` there.

## License

GPLv3 — see [LICENSE](LICENSE).
