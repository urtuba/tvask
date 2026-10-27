package io.github.urtuba.tvask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
    // After a pause, tvask listens again for MORE_MS, so the user can go on.
    // It stops after MAX_PARTS stretches of speech.
    private static final long MORE_MS = 2000;
    private static final int MAX_PARTS = 4;
    private static final int PAST_TURNS = 3;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private View card;
    private LevelMeter meter;
    private TextView past, state, words, answer;
    private SpeechRecognizer sr;
    private Intent listen;
    private TextToSpeech tts;
    private boolean ttsReady;
    private String last = "";
    private final List<List<String>> parts = new ArrayList<>();
    // listening is true while the recognizer runs. The recognizer reports an
    // error after cancel, and that late error must not count.
    private boolean listening;
    // more is true while tvask waits for the next part. spoke is true once
    // speech started in the current listen.
    private boolean more, spoke;
    private final Runnable noMore = new Runnable() {
        @Override
        public void run() {
            if (more && !spoke) {
                listening = false;
                sr.cancel();
                done();
            }
        }
    };
    // turn grows on every show and hide, so late results from an old turn
    // are dropped.
    private int turn;
    // utterance is the id of the text being spoken. When it ends, the panel
    // closes after afterSpeech milliseconds.
    private String utterance = "";
    private long afterSpeech;
    private final Runnable closeRun = new Runnable() {
        @Override
        public void run() {
            close();
        }
    };

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
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String id) {
            }

            @Override
            public void onDone(String id) {
                spoken(id);
            }

            @Override
            public void onError(String id) {
                spoken(id);
            }
        });
    }

    // spoken runs on a TTS thread when an utterance ends.
    private void spoken(final String id) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (id.equals(utterance)) {
                    hideLater(afterSpeech);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        tts.shutdown();
        super.onDestroy();
    }

    // The panel is a card at the top. The rest of the screen stays visible.
    // A thin bar under the state line moves with the voice while listening.
    @Override
    public View onCreateContentView() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 24, 40, 28);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 16, 16, 16));
        bg.setCornerRadius(6);
        box.setBackground(bg);
        TextView title = text(14, Color.GRAY);
        title.setText(R.string.app_name);
        past = text(14, Color.GRAY);
        past.setPadding(0, 4, 0, 12);
        state = text(14, Color.LTGRAY);
        meter = new LevelMeter(getContext());
        words = text(18, Color.rgb(255, 210, 90));
        answer = text(20, Color.WHITE);
        answer.setPadding(0, 12, 0, 0);
        box.addView(title);
        box.addView(past);
        box.addView(state);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16);
        mp.setMargins(0, 10, 0, 8);
        box.addView(meter, mp);
        box.addView(words);
        box.addView(answer);
        FrameLayout root = new FrameLayout(getContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        lp.setMargins(48, 32, 48, 0);
        root.addView(box, lp);
        card = box;
        return root;
    }

    @Override
    public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        turn++;
        ui.removeCallbacksAndMessages(null);
        tts.stop();
        utterance = "";
        card.animate().cancel();
        card.setAlpha(0f);
        card.setTranslationY(-24f);
        card.animate().alpha(1f).translationY(0f).setDuration(200).start();
        meter.setLevel(0f);
        meter.setVisibility(View.VISIBLE);
        state.setText(R.string.listening);
        line(words, "");
        line(answer, "");
        showPast();
        last = "";
        parts.clear();
        more = false;
        spoke = false;
        if (sr != null) {
            sr.destroy();
        }
        sr = SpeechRecognizer.createSpeechRecognizer(getContext(), GOOGLE);
        sr.setRecognitionListener(this);
        listen = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        // args carries the id of the remote that sent the mic key.
        if (args != null) {
            listen.putExtras(args);
        }
        listen.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        listen.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        listen.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        // Ask for longer pauses before the end of speech. Recognizers may
        // ignore these.
        listen.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        listen.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L);
        listening = true;
        sr.startListening(listen);
    }

    // showPast lists the last few questions and answers above the new one.
    private void showPast() {
        StringBuilder sb = new StringBuilder();
        List<String[]> lines = Conversation.shown();
        String you = getContext().getString(R.string.you);
        for (int i = Math.max(0, lines.size() - PAST_TURNS); i < lines.size(); i++) {
            sb.append(you).append(": ").append(lines.get(i)[0]).append('\n')
                    .append("tvask: ").append(lines.get(i)[1]).append("\n\n");
        }
        past.setText(sb.toString().trim());
        past.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
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
        listening = false;
        more = false;
        ui.removeCallbacksAndMessages(null);
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
        tts.stop();
        utterance = "";
        card.animate().cancel();
        super.onHide();
    }

    // close fades the panel out, then hides it.
    private void close() {
        card.animate().alpha(0f).translationY(-24f).setDuration(250).withEndAction(new Runnable() {
            @Override
            public void run() {
                hide();
            }
        }).start();
    }

    @Override
    public void onReadyForSpeech(Bundle p) {
        if (more) {
            state.setText(R.string.go_on);
            ui.postDelayed(noMore, MORE_MS);
        } else {
            state.setText(R.string.ready);
        }
    }

    @Override
    public void onBeginningOfSpeech() {
        spoke = true;
        ui.removeCallbacks(noMore);
        state.setText(R.string.hearing);
    }

    // Google's recognizer reports about -2 for silence and up to about 10
    // for loud speech. Values below 1 are room noise and show nothing. The
    // curve keeps normal speech in the middle of the meter.
    @Override
    public void onRmsChanged(float rms) {
        if (listening) {
            float v = Math.max(0f, (rms - 1f) / 10f);
            meter.setLevel((float) Math.pow(v, 1.4));
        }
    }

    @Override
    public void onBufferReceived(byte[] b) {
    }

    @Override
    public void onEndOfSpeech() {
        meter.setLevel(0f);
        state.setText(R.string.transcribing);
    }

    @Override
    public void onError(int e) {
        if (!listening) {
            return;
        }
        listening = false;
        ui.removeCallbacks(noMore);
        if (more) {
            // Nothing more came after a pause. That is the normal end.
            save("NO MORE\terror " + e);
            done();
            return;
        }
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
            line(words, said(last));
        }
    }

    @Override
    public void onResults(Bundle r) {
        listening = false;
        List<String> heard = all(r);
        save("HEARD " + (parts.size() + 1) + "\t" + join(heard));
        if (!heard.isEmpty()) {
            parts.add(heard);
            line(words, said(""));
        }
        if (heard.isEmpty() || parts.size() >= MAX_PARTS) {
            done();
            return;
        }
        more = true;
        spoke = false;
        last = "";
        listening = true;
        sr.startListening(listen);
    }

    // done ends listening and hands the parts to the brain.
    private void done() {
        more = false;
        meter.setLevel(0f);
        meter.setVisibility(View.GONE);
        if (parts.isEmpty()) {
            hideLater(3000);
            return;
        }
        Config cfg = new Config(getContext());
        if (cfg.chatKey.isEmpty()) {
            state.setText(R.string.no_key);
            hideLater(8000);
            return;
        }
        think(cfg, new ArrayList<List<String>>(parts));
    }

    // said joins the best guess of each part and the current partial result.
    private String said(String partial) {
        StringBuilder sb = new StringBuilder();
        for (List<String> p : parts) {
            sb.append(p.get(0)).append(' ');
        }
        return sb.append(partial).toString().trim();
    }

    @Override
    public void onEvent(int type, Bundle p) {
    }

    private void think(Config cfg, final List<List<String>> heard) {
        final Brain brain = new Brain(
                new OpenAIChat(cfg.chatUrl, cfg.chatKey, cfg.chatModel, cfg.chatEffort),
                new BraveSearch(cfg.braveKey),
                new Actions(getContext(), cfg.browser));
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
                        Conversation.show(said(""), text);
                        reply(text, 5000);
                    }
                });
            }

            // The app starts behind the panel. tvask says what it does and
            // fades out once it has said it.
            @Override
            public void onAction(String log, final String app, final int kind) {
                save("ACTION\t" + ms(start) + "\t" + log);
                post(myTurn, new Runnable() {
                    @Override
                    public void run() {
                        if (kind == Brain.NO_SEARCH) {
                            String text = getContext().getString(R.string.no_app_search, app);
                            Conversation.show(said(""), text);
                            reply(text, 3000);
                            return;
                        }
                        int note = kind == Brain.SEARCHED ? R.string.searched : R.string.opened;
                        Conversation.show(said(""), getContext().getString(note, app));
                        int now = kind == Brain.SEARCHED ? R.string.searching_in : R.string.opening;
                        reply(getContext().getString(now, app), 300);
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
        ui.removeCallbacks(closeRun);
        ui.postDelayed(closeRun, delay);
    }

    // reply shows text, reads it aloud, and closes the panel after
    // the reading ends.
    private void reply(String text, long after) {
        state.setText("");
        answer.setAlpha(0f);
        line(answer, text);
        answer.animate().alpha(1f).setDuration(200).start();
        if (!ttsReady || text.isEmpty()) {
            hideLater(after + 80L * text.length());
            return;
        }
        // A Turkish letter means Turkish. Otherwise read it as English.
        tts.setLanguage(text.matches("(?s).*[çğıöşüÇĞİÖŞÜ].*") ? TURKISH : Locale.US);
        utterance = "u" + SystemClock.elapsedRealtime();
        afterSpeech = after;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance);
        // In case the TTS never reports the end.
        hideLater(after + 20000 + 100L * text.length());
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

    // line sets a text line and hides it while it is empty.
    private static void line(TextView t, CharSequence s) {
        t.setText(s);
        t.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
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
