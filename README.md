# tvask

Ask your Android TV in your own words.
tvask takes over the mic key on your remote and sends what you say to an AI model you choose.

Status: early. It answers questions, searches the web, opens apps and searches inside a few apps.

## How it works

1. You press the mic key on the remote.
2. Android opens tvask, because tvask is the default assistant.
3. tvask asks Google's speech recognizer on the TV for the words.
   Only Google's remote service can read the remote's audio, so tvask does not record it itself.
4. tvask sends the recognizer's top five guesses to an AI model with four tools:
   open an app, search inside an app, open a web page, and search the web.
   The guesses help the model fix words the recognizer got wrong.
5. tvask shows the answer and reads it aloud.
   For an app or a page, it says what it opens and fades out.

After a short pause tvask keeps listening for two more seconds, so you can go on.
It remembers the last six turns for five minutes, so follow-up questions work.

Search inside an app works for YouTube, YouTube Music and Prime Video.
Other apps only open, and tvask tells you to search with the remote.
Netflix is one of them: it ignored both search and title links on the tested TV.
In the browser, tvask opens pages and Google results.

We use [TV Bro](https://github.com/truefedex/tv-bro) as the browser.
It is open source, made for TV remotes, and its WebView build uses the TV's own Chromium engine.
Any browser that opens web links works.

The default AI model is DeepSeek (`deepseek-flash`).
Web search uses the Brave Search API.
You need an API key for each.

## Requirements

- Android TV or Google TV with Android 11 or newer.
- The Google app for Android TV (`com.google.android.katniss`), installed and enabled.
  tvask uses its speech recognizer.
- A voice remote paired over Bluetooth.
  An IR-only remote sends no audio.
- A computer with adb for the setup.

Tested on: TCL 50P735 with the RC902V remote.
If you try another TV, please open an issue and tell us how it went.

## Setup

1. On the TV, turn on Developer options and USB debugging.
   Find the TV's IP address in the network settings.
2. Install tvask and make it the default assistant:
   ```
   adb connect <tv-ip>:5555
   adb install tvask.apk
   adb shell pm grant io.github.urtuba.tvask android.permission.RECORD_AUDIO
   adb shell settings put secure voice_interaction_service io.github.urtuba.tvask/.AssistService
   adb shell settings put secure assistant io.github.urtuba.tvask/.AssistService
   ```
   Do not change `voice_recognition_service`.
   It must stay on Google's recognizer.
3. TCL TVs only. TCL blocks new apps from starting in the background:
   ```
   adb shell appops set io.github.urtuba.tvask APP_AUTO_START allow
   ```
4. Send your API keys to the TV:
   ```
   adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
       -a io.github.urtuba.tvask.SET --es name chat_key --es value <deepseek-key>
   adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
       -a io.github.urtuba.tvask.SET --es name brave_key --es value <brave-key>
   ```
5. Press the mic key and say something.

## Settings

Set a value with the `SET` command from step 4.
An empty value goes back to the default.

| Name | Default | Meaning |
|---|---|---|
| `chat_key` | none | API key of the AI provider |
| `brave_key` | none | Brave Search API key |
| `chat_url` | `https://api.deepseek.com` | Base URL of an OpenAI compatible API |
| `chat_model` | `deepseek-flash` | Model name |
| `chat_effort` | `low` | Sent as `reasoning_effort`. Low keeps answers fast. |
| `browser` | first browser found | Package name of the browser, for example `com.phlox.tvwebbrowser` |

Check what is set. Keys show only their length:

```
adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver -a io.github.urtuba.tvask.SHOW
```

The keys live in the app's private storage on the TV.
Only adb can set them, because the receiver needs the `DUMP` permission.
They never go into the APK.
A key on the command line ends up in your shell history.
On macOS you can read it from the Keychain instead, for example `"$(security find-generic-password -s deepseek-api -w)"`.

Support for OpenRouter is planned as a separate adapter.

## Privacy

- Your voice goes to Google, through Google's speech recognizer.
- The recognized text, your installed app list and the date go to the AI provider.
- Web search queries go to Brave.
- tvask keeps a log of what it heard and did on the TV. See Debugging.

## Go back to Google Assistant

```
adb shell settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
adb shell settings put secure assistant com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
```

## Setup with an AI assistant

TVs differ.
Vendors add memory cleaners, start blockers and their own key handling.
An AI coding assistant with adb access can check your TV and adapt the setup to it.
A guide for that will live in `docs/`.

The manual steps above still work without one.
An assistant with adb access has shell rights on your TV.
Read what it runs before you allow it.

## Debugging

tvask saves what it heard, its answers and its actions on the TV, with timings:

```
adb shell cat /sdcard/Android/data/io.github.urtuba.tvask/files/transcripts.txt
```

Logs:

```
adb logcat -s tvask
```

## Known quirks

- The recognizer works in one language: the TV's language.
  Words from another language in the same sentence often come out wrong.
- When tvask is the assistant, Google's remote service switches the remote to its legacy voice mode.
  In that mode the remote's audio reaches Google's recognizer through the normal recording path.
- TCL: the window manager sends "close system dialogs" on key presses.
  tvask ignores it. Otherwise the panel would close right after it opens.
- TCL: the memory cleaner may stop tvask while it is idle.
  Android starts it again on the next key press.

## Build

Needs JDK 17 and the Android SDK packages `platforms;android-30` and `build-tools;34.0.0`.

```
./build.sh
```

This makes a debug build in `build/tvask.apk`.
See the top of `build.sh` for a release build.

## License

MIT. See `LICENSE`.
