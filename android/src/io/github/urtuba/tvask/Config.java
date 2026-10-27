package io.github.urtuba.tvask;

import android.content.Context;
import android.content.SharedPreferences;

// Config holds the settings that adb sets through ConfigReceiver.
// API keys live only in the app's private storage on the TV.
final class Config {
    static final String[] NAMES = {"chat_url", "chat_model", "chat_effort", "chat_key", "brave_key", "browser",
            "speech_rate"};

    final String chatUrl, chatModel, chatEffort, chatKey, braveKey, browser;
    final float speechRate;

    Config(Context c) {
        SharedPreferences p = prefs(c);
        chatUrl = p.getString("chat_url", "https://api.deepseek.com");
        chatModel = p.getString("chat_model", "deepseek-flash");
        chatEffort = p.getString("chat_effort", "low");
        chatKey = p.getString("chat_key", "");
        braveKey = p.getString("brave_key", "");
        // Empty means the first browser Android finds.
        browser = p.getString("browser", "");
        speechRate = number(p.getString("speech_rate", ""), 1.25f);
    }

    private static float number(String s, float def) {
        try {
            return s.isEmpty() ? def : Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    static boolean known(String name) {
        for (String n : NAMES) {
            if (n.equals(name)) {
                return true;
            }
        }
        return false;
    }

    static boolean secret(String name) {
        return name.endsWith("_key");
    }
}
