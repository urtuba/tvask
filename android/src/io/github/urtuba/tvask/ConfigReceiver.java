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
// An empty value removes the setting. SHOW never prints keys.
public class ConfigReceiver extends BroadcastReceiver {
    static final String SET = "io.github.urtuba.tvask.SET";
    static final String SHOW = "io.github.urtuba.tvask.SHOW";

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
            if (value == null || value.isEmpty()) {
                p.edit().remove(name).commit();
                setResultData("removed " + name);
            } else {
                p.edit().putString(name, value).commit();
                setResultData("set " + name);
            }
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
