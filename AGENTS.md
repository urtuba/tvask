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

## 1. Find and check the TV

The person may tell you the TV's brand and model.
Use it to pick the right TV, and to look for vendor quirks in step 3.
Do not ask for the IP address first. Find the TV on the network.

You need adb. If it is missing, ask to install Android platform-tools:
`brew install --cask android-platform-tools` on macOS, or https://developer.android.com/tools/releases/platform-tools.

```
adb devices -l
adb mdns services
```

`adb mdns services` lists devices that offer adb on the network, for example `_adb._tcp 192.168.1.2:5555`.
If the TV is not there, list the Android TVs on the network by name.
These commands run until you stop them, so stop them after a few seconds:

```
dns-sd -B _androidtvremote2._tcp           # macOS
avahi-browse -rt _androidtvremote2._tcp    # Linux
```

On macOS, get the address with `dns-sd -L "<name>" _androidtvremote2._tcp`, then `dns-sd -G v4 <host>`.
The network name is often not the model. A TCL 50P735 calls itself "Smart TV Pro".
If you find more than one TV, ask which one.
If nothing shows up, ask the person to read the address on the TV: Settings, Network and Internet, then their network.

```
adb connect <tv-ip>:5555
```

The TV asks whether to allow debugging. Ask the person to tick "Always allow" and press OK.
Then `adb devices` shows the TV as `device`.

- `Connection refused`: debugging is off. Ask the person to turn it on.
  On Google TV: Settings, System, About, then press OK on "Android TV OS build" seven times.
  Then Settings, System, Developer options, USB debugging.
  On older Android TV, About is under Device Preferences.
- Some newer TVs only offer "Wireless debugging" in Developer options.
  There, "Pair device with pairing code" shows a port and a code.
  Run `adb pair <tv-ip>:<port> <code>`, then `adb connect` to the port shown on the Wireless debugging screen.
- More than one device in `adb devices`: add `-s <tv-ip>:5555` to every adb command.

Now check the TV:

```
adb shell getprop ro.build.version.release        # 11 or newer
adb shell getprop ro.product.cpu.abilist
adb shell pm list packages -e | grep com.google.android.katniss
adb shell dumpsys bluetooth_manager | grep -iE 'Bonded devices' -A10
adb shell getevent -pl | grep 'name:'
```

What you need:

- Android 11 or newer.
- `com.google.android.katniss` installed and enabled. It is Google's TV search app, and tvask uses its speech recognizer.
- A voice remote bonded over Bluetooth LE, and connected: its name shows up in the `getevent` list.
  A bonded remote can still talk to the TV only by IR, and then voice cannot reach the TV.
  Ask the person to remove it and pair it again: Settings, Remotes and accessories, then the pairing combination shown on screen.

If a check fails, stop and explain it. tvask will not work without these.

## 2. Install

Use the APK from the latest GitHub release, or build it with `./build.sh`.
Check its SHA-256 against the release notes.
If another build of tvask is installed with a different signature, uninstall it first.
That deletes its keys and memory notes, so you will send the keys again in step 4.

```
adb install tvask.apk
adb shell pm grant io.github.urtuba.tvask android.permission.RECORD_AUDIO
adb shell appops set io.github.urtuba.tvask GET_USAGE_STATS allow
adb shell cmd notification allow_listener io.github.urtuba.tvask/.MediaListener
adb shell settings put secure assist_screenshot_enabled 0
```

What each one is for:

- `RECORD_AUDIO`: the speech recognizer is used on behalf of tvask.
- `GET_USAGE_STATS`: lets tvask list and close open apps.
- The notification listener: lets tvask read what is playing. It reads no notifications.
- `assist_screenshot_enabled 0`: Android would offer the assistant a screenshot. tvask never uses it, so turn it off.

## 3. Look for vendor quirks

Many vendors add a memory cleaner or a start blocker. Look for them:

```
adb shell appops get io.github.urtuba.tvask
adb shell pm list packages | grep -iE 'guard|clean|boost|security|manager'
adb logcat -d | grep -iE 'kill.*tvask|forbid|block.*tvask' | tail
```

Known cases:

- TCL: a custom app-op blocks background starts.
  `appops get` shows `APP_AUTO_START: ignore` with a reject time, and the log shows `forbid bind service` for tvask. Run
  `adb shell appops set io.github.urtuba.tvask APP_AUTO_START allow`.
  Its memory cleaner may still stop tvask when idle. Android starts it again on the next mic key press.
- TCL: the window manager sends "close system dialogs" on key presses. tvask already ignores it.

If you find a new quirk, tell the person and suggest a TV report as a GitHub issue.

Now make tvask the default assistant.
Do this after the quirks, so no start blocker stops Android from binding it:

```
adb shell settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
adb shell settings put secure voice_interaction_service io.github.urtuba.tvask/.AssistService
adb shell settings put secure assistant io.github.urtuba.tvask/.AssistService
```

Setting `voice_interaction_service` to Google first makes Android bind tvask fresh, even when the value was already tvask.
Changing it also resets `assistant`, so set `assistant` last.

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

To test the model and the panel without a voice, send a question as text:

```
adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
    -a io.github.urtuba.tvask.ASK --es text "What's the weather tomorrow?"
```

If it answers `not asked`, the assistant service is not bound yet.
Run the three `settings put` lines at the end of step 3 again, then retry.
This also happens after an update.

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

## More fixes for the TV

[TUNEUP.md](TUNEUP.md) covers a slow TV, a lighter home screen, remote keys that do nothing, a mic key that does nothing, and showing an iPhone on the TV.
Mention it at the end if the person complains about one of these.

## Undo

```
adb shell settings put secure voice_interaction_service com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
adb shell settings put secure assistant com.google.android.katniss/.search.serviceapi.KatnissVoiceInteractionService
adb shell cmd notification disallow_listener io.github.urtuba.tvask/.MediaListener
adb uninstall io.github.urtuba.tvask
```
