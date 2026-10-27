# Tuning an Android TV: a guide for AI coding agents

The person asked you to fix one thing on their TV.
Each part below is one problem. Do only the part they asked for.

- Find and connect to the TV as in step 1 of [AGENTS.md](AGENTS.md).
- Show each command before you run it, and say what it does.
- Ask before each change, and say what the person loses with it.
- Prefer changes that can be undone. Keep a list of them.
- Never uninstall system packages. Disable them with `pm disable-user`, so `pm enable` brings them back.

These worked on a TCL 50P735 with 2 GB of RAM. Other TVs have other package names, so look before you act.

## The TV feels slow

Look first:

```
adb shell cat /proc/meminfo | head -3
adb shell top -b -n 1 -m 15 -o PID,RES,NAME -s 2
adb shell pm list packages -3
adb shell pm list packages -s | sort
adb shell pm list packages -d
```

`top` lists the processes that use the most memory.
`-3` lists the apps the person installed, `-s` the apps that came with the TV, and `-d` the ones already disabled.

Make a short list of candidates:

- vendor apps for analytics, ads, promotions, messages and auto installs
- vendor extras the person does not use, such as a media center, a game bar, Miracast or another voice assistant
- streaming apps that came with the TV and are never opened

Say what each one does, then ask. Disable the ones the person agrees to:

```
adb shell pm disable-user --user 0 <package>
```

Never disable these:

- the current home app, and the stock home until another one works (see "The home screen is heavy")
- `com.google.android.katniss`, `com.google.android.tv.remote.service` and tvask
- Settings, the system UI, the keyboard, Bluetooth and the package installer
- Google Play services, the Play Store and `com.google.android.webview`
- packages with `providers`, `permissioncontroller` or `ext.services` in the name

Two more changes help on most TVs:

```
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
adb shell pm trim-caches 999G
```

The first three make menus faster. Set them back to 1 to undo.
`trim-caches` deletes app caches. Apps build them again when needed.

Ask before you restart the TV with `adb reboot`, then check that the home screen, the remote and voice still work.

On the TCL, 28 packages are off.
Among them are TCL's analytics, promotions, messages, game bar, media center, Alexa and Miracast apps, Google's auto installs and Play Games, and two streaming apps that came with the TV.

## The home screen is heavy

The Google TV home screen loads rows of recommendations and uses a lot of memory.
A simple launcher is lighter. We use Projectivy Launcher from the Play Store.

1. Ask the person to install the launcher from the Play Store and open it once.
2. List the home apps:
   ```
   adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME
   ```
3. Only when the new launcher is in that list, disable the stock home.
   On Google TV it is `com.google.android.apps.tv.launcherx`.
   Also disable `com.google.android.tungsten.setupwraith`, or it can take the Home key.
4. Ask the person to press Home. The new launcher must open.
   If the screen stays empty, enable both packages again at once.

Undo with `pm enable` for both packages.

Some remote keys belong to the stock home.
On the TCL, the Netflix, YouTube, Prime Video and Settings keys stopped working.
"Remote keys do nothing" brings them back.

## Remote keys do nothing

tvkeys gives spare keys a new job, for example opening an app.
It reads the remote at the Linux input level. It needs no root and adds no lag.

Follow the README of https://github.com/urtuba/tvkeys.
Find the codes of the spare keys with `tvkeys learn`, then write `keys.conf` with the person.
Map only keys that do nothing now. A key that already opens something would open both.

On the TCL, tvkeys runs the Netflix, YouTube, Prime Video, Settings and Media keys.

## Show an iPhone on the TV

android-airplay-server turns the TV into an AirPlay receiver.
It mirrors the screen with sound, and follows the phone when it turns.
It is a third party app under GPL-3: https://github.com/jqssun/android-airplay-server

1. Tell the person which APK you download, and from where. Take it from the project's latest GitHub release.
2. Install it with `adb install`, and ask the person to open it on the TV.
3. On the iPhone: Control Center, Screen Mirroring, then "Android AirPlay". Both must be on the same Wi-Fi.

On the TCL, it works only while the app is open, because TCL stops its background service.
We put it on the Media key with tvkeys.

## When you are done

Give the person the list of changes and the commands that undo them.
If something worked or broke in a new way, suggest an issue at https://github.com/urtuba/tvask/issues.
