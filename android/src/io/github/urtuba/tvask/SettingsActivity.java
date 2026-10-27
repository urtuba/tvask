package io.github.urtuba.tvask;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.text.format.Formatter;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// SettingsActivity lets the user change the basic settings with the remote.
// Left and right, or OK, step through a row's values. Values save at once.
// API keys stay with adb, see the README.
public class SettingsActivity extends Activity {
    private static final String[] RATES = {"1.0", "1.1", "1.25", "1.4", "1.6", "1.8"};
    private static final String[] WAITS = {"0", "1000", "1500", "2000", "2500", "3000"};
    private static final String[] SIZES = {"0", "1048576", "2097152", "5242880", "10485760"};

    // Row is one setting: its stored values and what the screen shows.
    private final class Row {
        final String name;
        final String[] values, labels;
        TextView value;
        int index;

        // A value set with adb that is not in the list joins it as the last
        // option, so the row never shows a wrong label.
        Row(String name, String[] values, String[] labels, String def) {
            this.name = name;
            String now = prefs().getString(name, def);
            int found = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(now)) {
                    found = i;
                }
            }
            if (found < 0) {
                values = Arrays.copyOf(values, values.length + 1);
                labels = Arrays.copyOf(labels, labels.length + 1);
                values[values.length - 1] = now;
                labels[labels.length - 1] = now;
                found = values.length - 1;
            }
            this.values = values;
            this.labels = labels;
            index = found;
        }

        void step(int d) {
            index = (index + d + values.length) % values.length;
            prefs().edit().putString(name, values[index]).apply();
            value.setText("‹  " + labels[index] + "  ›");
            if (name.equals("ui_language")) {
                recreate();
            }
        }
    }

    private TextToSpeech tts;
    private boolean ttsReady;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Locales.wrap(base));
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
            }
        });
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(96, 56, 96, 56);
        root.setBackgroundColor(Color.rgb(16, 16, 16));
        TextView title = text(getString(R.string.settings_title), 28, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, 24);
        root.addView(title);

        root.addView(section(R.string.settings_speech));
        add(root, R.string.set_rate, new Row("speech_rate", RATES, rates(), "1.25"));
        add(root, R.string.set_wait, new Row("listen_more", WAITS, waits(), "2000"));
        TextView test = button(getString(R.string.set_test));
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!ttsReady) {
                    return;
                }
                tts.setSpeechRate(new Config(SettingsActivity.this).speechRate);
                tts.speak(getString(R.string.set_sample), TextToSpeech.QUEUE_FLUSH, null, "test");
            }
        });
        root.addView(test, rowParams());

        root.addView(section(R.string.settings_screen));
        String[][] browsers = browsers();
        add(root, R.string.set_browser, new Row("browser", browsers[0], browsers[1], ""));
        String[] langs = new String[Locales.LANGUAGES.length];
        for (int i = 0; i < langs.length; i++) {
            langs[i] = i == 0 ? getString(R.string.set_tv_language) : Locales.name(Locales.LANGUAGES[i]);
        }
        add(root, R.string.set_language, new Row("ui_language", Locales.LANGUAGES, langs, ""));

        root.addView(section(R.string.settings_memory));
        add(root, R.string.set_memory_size, new Row("memory_size", SIZES, sizes(), "1048576"));
        add(root, R.string.set_tv_context, new Row("tv_context", new String[] {"on", "off"},
                new String[] {getString(R.string.set_on), getString(R.string.set_off)}, "on"));
        final TextView clear = button(memoryLabel());
        clear.setOnClickListener(new View.OnClickListener() {
            private boolean asked;

            @Override
            public void onClick(View v) {
                if (!asked) {
                    asked = true;
                    clear.setText(R.string.set_memory_confirm);
                    return;
                }
                asked = false;
                new Memory(SettingsActivity.this, 1).clear();
                clear.setText(memoryLabel());
            }
        });
        root.addView(clear, rowParams());

        Config cfg = new Config(this);
        TextView keys = text(getString(cfg.chatKey.isEmpty() || cfg.braveKey.isEmpty()
                ? R.string.set_keys_missing : R.string.set_keys_ok), 14, Cards.MUTED);
        keys.setPadding(0, 32, 0, 0);
        root.addView(keys);
        // The list is taller than the screen, so it scrolls with the focus.
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(16, 16, 16));
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onDestroy() {
        tts.shutdown();
        super.onDestroy();
    }

    private void add(LinearLayout root, int label, final Row r) {
        final LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setPadding(24, 16, 24, 16);
        v.setFocusable(true);
        v.setClickable(true);
        v.addView(text(getString(label), 18, Color.WHITE), new LinearLayout.LayoutParams(0, -2, 1));
        r.value = text("‹  " + r.labels[r.index] + "  ›", 18, Cards.ACCENT);
        v.addView(r.value);
        focusLook(v);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                r.step(1);
            }
        });
        v.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int code, KeyEvent e) {
                if (e.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                    r.step(-1);
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    r.step(1);
                    return true;
                }
                return false;
            }
        });
        root.addView(v, rowParams());
    }

    private TextView button(String s) {
        TextView t = text(s, 18, Color.WHITE);
        t.setPadding(24, 16, 24, 16);
        t.setFocusable(true);
        t.setClickable(true);
        focusLook(t);
        return t;
    }

    private static void focusLook(View v) {
        final GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(4);
        bg.setColor(Color.TRANSPARENT);
        v.setBackground(bg);
        v.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean has) {
                bg.setColor(has ? Cards.TRACK : Color.TRANSPARENT);
                bg.setStroke(2, has ? Cards.ACCENT : Color.TRANSPARENT);
            }
        });
    }

    private TextView section(int label) {
        TextView t = text(getString(label), 14, Cards.MUTED);
        t.setPadding(24, 28, 0, 8);
        return t;
    }

    private String[] rates() {
        String[] out = new String[RATES.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = RATES[i].replace('.', getString(R.string.decimal).charAt(0)) + "×";
        }
        return out;
    }

    private String[] sizes() {
        String[] out = new String[SIZES.length];
        for (int i = 0; i < out.length; i++) {
            long b = Long.parseLong(SIZES[i]);
            out[i] = b == 0 ? getString(R.string.set_off) : Formatter.formatShortFileSize(this, b);
        }
        return out;
    }

    private String memoryLabel() {
        long used = new Memory(this, 1).used();
        return getString(R.string.set_memory_clear, Formatter.formatShortFileSize(this, used));
    }

    private String[] waits() {
        String[] out = new String[WAITS.length];
        for (int i = 0; i < out.length; i++) {
            long ms = Long.parseLong(WAITS[i]);
            out[i] = ms == 0 ? getString(R.string.set_off)
                    : getString(R.string.set_seconds,
                    String.format(getResources().getConfiguration().getLocales().get(0), "%.1f", ms / 1000f));
        }
        return out;
    }

    // browsers returns the installed browsers as {values, labels}, with
    // "automatic" first.
    private String[][] browsers() {
        List<String> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        values.add("");
        labels.add(getString(R.string.set_auto));
        for (Map.Entry<String, String> e : Actions.browsers(getPackageManager()).entrySet()) {
            values.add(e.getKey());
            labels.add(e.getValue());
        }
        return new String[][] {values.toArray(new String[0]), labels.toArray(new String[0])};
    }

    private SharedPreferences prefs() {
        return Config.prefs(this);
    }

    private static LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2);
        lp.setMargins(0, 4, 0, 4);
        return lp;
    }

    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }
}
