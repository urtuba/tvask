package io.github.urtuba.tvask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// AssistSession runs while tvask is on screen. It asks Google's recognizer for
// the words, because only Google's remote service gets the remote's audio.
// The recognizer uses the TV's language. Brain then answers or acts.
public class AssistSession extends VoiceInteractionSession implements RecognitionListener {
    static final String TAG = "tvask";
    static final ComponentName GOOGLE = new ComponentName("com.google.android.katniss",
            "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService");
    private static final Locale TURKISH = new Locale("tr", "TR");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView state, words, answer;
    private SpeechRecognizer sr;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String last = "";
    // turn grows on every show and hide, so late results from an old turn
    // are dropped.
    private int turn;

    AssistSession(Context c) {
        super(c);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        tts = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
            }
        });
    }

    @Override
    public void onDestroy() {
        tts.shutdown();
        super.onDestroy();
    }

    @Override
    public View onCreateContentView() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(64, 40, 64, 40);
        box.setBackgroundColor(Color.argb(235, 16, 16, 16));
        TextView title = text(30, Color.WHITE);
        title.setText(R.string.app_name);
        state = text(22, Color.LTGRAY);
        words = text(26, Color.rgb(255, 210, 90));
        answer = text(30, Color.WHITE);
        answer.setPadding(0, 24, 0, 0);
        box.addView(title);
        box.addView(state);
        box.addView(words);
        box.addView(answer);
        return box;
    }

    @Override
    public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        turn++;
        ui.removeCallbacksAndMessages(null);
        tts.stop();
        state.setText(R.string.listening);
        words.setText("");
        answer.setText("");
        last = "";
        if (sr != null) {
            sr.destroy();
        }
        sr = SpeechRecognizer.createSpeechRecognizer(getContext(), GOOGLE);
        sr.setRecognitionListener(this);
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // args carries the id of the remote that sent the mic key.
        if (args != null) {
            i.putExtras(args);
        }
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        sr.startListening(i);
    }

    // TCL's window manager sends "close system dialogs" on key presses.
    // The default handler hides the session, sometimes right after it opens.
    @Override
    public void onCloseSystemDialogs() {
        Log.i(TAG, "close system dialogs ignored");
    }

    @Override
    public void onHide() {
        turn++;
        ui.removeCallbacksAndMessages(null);
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
        tts.stop();
        super.onHide();
    }

    @Override
    public void onReadyForSpeech(Bundle p) {
        state.setText(R.string.ready);
    }

    @Override
    public void onBeginningOfSpeech() {
        state.setText(R.string.hearing);
    }

    @Override
    public void onRmsChanged(float rms) {
    }

    @Override
    public void onBufferReceived(byte[] b) {
    }

    @Override
    public void onEndOfSpeech() {
        state.setText(R.string.transcribing);
    }

    @Override
    public void onError(int e) {
        Log.i(TAG, "recognizer error " + e);
        state.setText(getContext().getString(R.string.error, e));
        save("ERROR " + e + "\tlast partial: " + last);
        hideLater(5000);
    }

    @Override
    public void onPartialResults(Bundle r) {
        List<String> l = all(r);
        if (!l.isEmpty()) {
            last = l.get(0);
            words.setText(last);
        }
    }

    @Override
    public void onResults(Bundle r) {
        List<String> heard = all(r);
        save("HEARD\t" + join(heard));
        if (heard.isEmpty()) {
            hideLater(3000);
            return;
        }
        words.setText(heard.get(0));
        Config cfg = new Config(getContext());
        if (cfg.chatKey.isEmpty()) {
            state.setText(R.string.no_key);
            hideLater(8000);
            return;
        }
        think(cfg, heard);
    }

    @Override
    public void onEvent(int type, Bundle p) {
    }

    private void think(Config cfg, final List<String> heard) {
        final Brain brain = new Brain(
                new OpenAIChat(cfg.chatUrl, cfg.chatKey, cfg.chatModel, cfg.chatEffort),
                new BraveSearch(cfg.braveKey),
                new Actions(getContext()));
        final int myTurn = turn;
        final long start = SystemClock.elapsedRealtime();
        final Brain.Listener l = new Brain.Listener() {
            @Override
            public void onStatus(final int text) {
                post(myTurn, new Runnable() {
                    @Override
                    public void run() {
                        state.setText(text);
                    }
                });
            }

            @Override
            public void onAnswer(final String text) {
                save("ANSWER\t" + ms(start) + "\t" + text);
                post(myTurn, new Runnable() {
                    @Override
                    public void run() {
                        state.setText("");
                        answer.setText(text);
                        speak(text);
                        hideLater(8000 + 80L * text.length());
                    }
                });
            }

            @Override
            public void onAction(String log) {
                save("ACTION\t" + ms(start) + "\t" + log);
                post(myTurn, new Runnable() {
                    @Override
                    public void run() {
                        hide();
                    }
                });
            }

            @Override
            public void onFailure(final String message) {
                save("FAIL\t" + ms(start) + "\t" + message);
                post(myTurn, new Runnable() {
                    @Override
                    public void run() {
                        state.setText(getContext().getString(R.string.failed, message));
                        hideLater(8000);
                    }
                });
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                brain.run(heard, l);
            }
        }, "tvask-brain").start();
    }

    // post runs r on the main thread if the turn is still current.
    private void post(final int myTurn, final Runnable r) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (myTurn == turn) {
                    r.run();
                }
            }
        });
    }

    private void hideLater(long delay) {
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                hide();
            }
        }, delay);
    }

    private void speak(String text) {
        if (!ttsReady || text.isEmpty()) {
            return;
        }
        // A Turkish letter means Turkish. Otherwise read it as English.
        tts.setLanguage(text.matches("(?s).*[çğıöşüÇĞİÖŞÜ].*") ? TURKISH : Locale.US);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer");
    }

    // save appends a line to files/transcripts.txt in the app's external
    // storage, because logcat on some TVs rotates within minutes.
    private void save(String line) {
        File dir = getContext().getExternalFilesDir(null);
        if (dir == null) {
            return;
        }
        try (FileWriter w = new FileWriter(new File(dir, "transcripts.txt"), true)) {
            w.write(new Date() + "\t" + line + "\n");
        } catch (IOException e) {
            Log.w(TAG, "cannot save transcript: " + e);
        }
    }

    private TextView text(float size, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private static List<String> all(Bundle r) {
        ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null ? new ArrayList<String>() : l;
    }

    private static String join(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (String s : l) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static String ms(long start) {
        return (SystemClock.elapsedRealtime() - start) + "ms";
    }
}
