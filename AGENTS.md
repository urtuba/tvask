# Setting up tvask: a guide for AI coding agents

You are helping a person set up tvask on their own Android TV.
Every TV is a little different, so check before you change anything.
Work step by step and tell the person what you found.

## Ground rules

- Show each command before you run it, and say what it does.
- adb gives you shell rights on the person's TV. Change only what a step needs.
- Never print, log or commit API keys.
  Read them from the person's secret store, or let the person paste them into a command they run.
- Do not change `voice_recognition_service`. It must stay on Google's recognizer.
- Do not disable or uninstall system packages unless the person asks.
- Keep a short list of what you changed, so it can be undone.

## 1. Check the TV

Ask for the TV's IP address, then:

```
adb connect <tv-ip>:5555
adb shell getprop ro.build.version.release        # 11 or newer
adb shell getprop ro.product.cpu.abilist
adb shell pm list packages -e | grep com.google.android.katniss
adb shell dumpsys bluetooth_manager | grep -iE 'Bonded devices' -A10
```

What you need:

- Android 11 or newer.
- `com.google.android.katniss` installed and enabled. It is Google's TV search app, and tvask uses its speech recognizer.
- A voice remote bonded over Bluetooth LE.
  If the remote works only by IR, voice cannot reach the TV.
  Ask the person to pair it again: Settings, Remotes and accessories, then the pairing combination shown on screen.

If a check fails, stop and explain it. tvask will not work without these.

## 2. Install

Use the APK from the latest GitHub release, or build it with `./build.sh`.

```
adb install tvask.apk
adb shell pm grant io.github.urtuba.tvask android.permission.RECORD_AUDIO
adb shell appops set io.github.urtuba.tvask GET_USAGE_STATS allow
adb shell cmd notification allow_listener io.github.urtuba.tvask/.MediaListener
adb shell settings put secure assist_screenshot_enabled 0
adb shell settings put secure voice_interaction_service io.github.urtuba.tvask/.AssistService
adb shell settings put secure assistant io.github.urtuba.tvask/.AssistService
```

What each one is for:

- `RECORD_AUDIO`: the speech recognizer is used on behalf of tvask.
- `GET_USAGE_STATS`: lets tvask list and close open apps.
- The notification listener: lets tvask read what is playing. It reads no notifications.
- `assist_screenshot_enabled 0`: Android would offer the assistant a screenshot. tvask never uses it, so turn it off.
- The two `settings put` lines make tvask the default assistant, so the mic key opens it.

## 3. Look for vendor quirks

Many vendors add a memory cleaner or a start blocker. Look for them:

```
adb shell appops get io.github.urtuba.tvask
adb shell pm list packages | grep -iE 'guard|clean|boost|security|manager'
adb logcat -d | grep -iE 'kill.*tvask|forbid|block.*tvask' | tail
```

Known cases:

- TCL: a custom app-op blocks background starts. Run
  `adb shell appops set io.github.urtuba.tvask APP_AUTO_START allow`.
  Its memory cleaner may still stop tvask when idle. Android starts it again on the next mic key press.
- TCL: the window manager sends "close system dialogs" on key presses. tvask already ignores it.

If you find a new quirk, tell the person and suggest a TV report as a GitHub issue.

## 4. Keys and settings

tvask needs an API key for an OpenAI compatible chat model and one for the Brave Search API.
The default model is DeepSeek `deepseek-flash`.

```
adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
    -a io.github.urtuba.tvask.SET --es name chat_key --es value "<key>"
adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
    -a io.github.urtuba.tvask.SET --es name brave_key --es value "<key>"
adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
    -a io.github.urtuba.tvask.SHOW
```

`SHOW` prints settings and only the length of each key.
The README lists every setting.

For a browser, list what can open web links:

```
adb shell pm query-activities --brief -a android.intent.action.VIEW -d https://example.com
```

Ignore `com.google.android.tv.frameworkpackagestubs`. It only shows an error.
If there is no real browser, suggest TV Bro from https://github.com/truefedex/tv-bro, then set it:

```
adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
    -a io.github.urtuba.tvask.SET --es name browser --es value com.phlox.tvwebbrowser
```

## 5. Verify

```
adb shell input keyevent KEYCODE_ASSIST
adb shell dumpsys voiceinteraction | grep mShown     # mShown=true
adb shell input keyevent KEYCODE_BACK
```

`KEYCODE_ASSIST` does not use the remote, so it listens to the TV's own microphone.
On most TVs that hears nothing, and that is fine here.

Then ask the person to press the mic key on the remote and say "hello".
Read the log:

```
adb shell cat /sdcard/Android/data/io.github.urtuba.tvask/files/transcripts.txt
```

- `HEARD` lines: the recognizer got the words.
- `ANSWER` or `ACTION` lines: the model answered or acted.
- `ERROR 7`, or no `HEARD` line at all: no audio arrived. Check the remote's Bluetooth bond first.
- `FAIL` lines: usually a missing key or no network.

## 6. When the TV needs code changes

Settings and adb commands cover most TVs.
If this TV needs a code change, suggest a fork:

- Search links per app are in `android/src/io/github/urtuba/tvask/Actions.java`.
- Panel behavior is in `AssistSession.java`.
- The model's tools and prompt are in `Brain.java`.

Keep TV specific changes in the fork.
Send back fixes that help every TV as a pull request.

## Undo

```
adb shell settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
adb shell settings put secure assistant com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
adb shell cmd notification disallow_listener io.github.urtuba.tvask/.MediaListener
adb uninstall io.github.urtuba.tvask
```
