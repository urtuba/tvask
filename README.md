# tvask

Ask your Android TV in your own words.
tvask takes over the mic key on your remote and will send what you say to an AI model you choose.

Status: early. Today tvask shows what you said. The AI part comes next.

## How it works

1. You press the mic key on the remote.
2. Android opens tvask, because tvask is the default assistant.
3. tvask asks Google's speech recognizer on the TV for the words.
   Only Google's remote service can read the remote's audio, so tvask does not record it itself.
4. Planned: tvask sends the words to an AI model with a few tools.
   The first tools are: open an app, search inside an app, and search the web.
5. Planned: tvask shows the answer and reads it aloud.

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
4. Press the mic key and say something.

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

tvask saves every result on the TV:

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
