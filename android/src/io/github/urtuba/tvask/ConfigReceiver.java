package io.github.urtuba.tvask;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

// ConfigReceiver lets adb change the settings in Config. The DUMP permission
// guards it, and only the shell holds that permission on a normal TV.
//
//   adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
//       -a io.github.urtuba.tvask.SET --es name chat_key --es value <key>
//   adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
//       -a io.github.urtuba.tvask.SHOW
//
//   adb shell am broadcast -n io.github.urtuba.tvask/.ConfigReceiver \
//       -a io.github.urtuba.tvask.ASK --es text "What's the weather tomorrow?"
//
// An empty value removes the setting. SHOW never prints keys. ASK opens
// the panel as if the user had said the text, for tests and demos.
public class ConfigReceiver extends BroadcastReceiver {
    static final String SET = "io.github.urtuba.tvask.SET";
    static final String SHOW = "io.github.urtuba.tvask.SHOW";
    static final String ASK = "io.github.urtuba.tvask.ASK";

    @Override
    public void onReceive(Context c, Intent i) {
        SharedPreferences p = Config.prefs(c);
        if (SET.equals(i.getAction())) {
            String name = i.getStringExtra("name");
            String value = i.getStringExtra("value");
            if (!Config.known(name)) {
                setResultData("unknown name: " + name);
                return;
            }
            // A key belongs to one server. When the server changes, the old
            // key must not go to the new one.
            if (name.equals("chat_url") && !String.valueOf(value).equals(p.getString(name, ""))) {
                p.edit().remove("chat_key").commit();
            }
            if (value == null || value.isEmpty()) {
                p.edit().remove(name).commit();
                setResultData("removed " + name);
            } else {
                p.edit().putString(name, value).commit();
                setResultData("set " + name);
            }
            return;
        }
        if (ASK.equals(i.getAction())) {
            String text = i.getStringExtra("text");
            boolean ok = text != null && !text.trim().isEmpty() && AssistService.ask(text.trim());
            setResultData(ok ? "asked" : "not asked: no text, or the assistant is not bound yet");
            return;
        }
        if (SHOW.equals(i.getAction())) {
            StringBuilder sb = new StringBuilder();
            for (String n : Config.NAMES) {
                String v = p.getString(n, null);
                sb.append(n).append('=');
                if (v == null) {
                    sb.append("(default)");
                } else if (Config.secret(n)) {
                    sb.append("set, ").append(v.length()).append(" chars");
                } else {
                    sb.append(v);
                }
                sb.append('\n');
            }
            setResultData(sb.toString());
        }
    }
}
