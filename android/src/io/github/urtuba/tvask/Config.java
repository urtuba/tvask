package io.github.urtuba.tvask;

import android.content.Context;
import android.content.SharedPreferences;

// Config holds the settings that adb sets through ConfigReceiver.
// API keys live only in the app's private storage on the TV.
final class Config {
    static final String[] NAMES = {"chat_url", "chat_model", "chat_effort", "chat_key", "brave_key", "browser",
            "speech_rate", "listen_more", "ui_language", "memory_size", "tv_context"};

    final String chatUrl, chatModel, chatEffort, chatKey, braveKey, browser;
    final float speechRate;
    final long listenMore, memorySize;
    final boolean tvContext;

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
        listenMore = (long) number(p.getString("listen_more", ""), 2000);
        memorySize = (long) number(p.getString("memory_size", ""), 1024 * 1024);
        tvContext = !"off".equals(p.getString("tv_context", "on"));
    }

    // number reads a setting as a number. Empty, broken or negative values
    // fall back to def.
    private static float number(String s, float def) {
        try {
            float f = s.isEmpty() ? def : Float.parseFloat(s);
            return f < 0 ? def : f;
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
