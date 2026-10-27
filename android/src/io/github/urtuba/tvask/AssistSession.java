package io.github.urtuba.tvask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
    // slot holds the card under the answer. picking is true while site
    // options wait for a choice.
    private LinearLayout slot;
    private boolean picking;
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
    // Loudness stats of the current listen, for the log.
    private float rmsSum, rmsMax;
    private int rmsCount;
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
                // A short silence loads the voice while the user speaks.
                // Otherwise the first answer after a cold start waits for it.
                if (ttsReady) {
                    tts.setLanguage(Locale.getDefault());
                    tts.playSilentUtterance(50, TextToSpeech.QUEUE_FLUSH, "warmup");
                }
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
        slot = new LinearLayout(getContext());
        slot.setOrientation(LinearLayout.VERTICAL);
        slot.setPadding(0, 16, 0, 0);
        box.addView(slot);
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
        loudness();
        state.setText(R.string.listening);
        line(words, "");
        line(answer, "");
        slot.removeAllViews();
        slot.setVisibility(View.GONE);
        picking = false;
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

    // Google's recognizer reports about -2 for silence and tops out near 10.
    // Normal speech from the remote measured 7 to 9, so the curve puts it in
    // the middle of the meter and leaves the top for a loud voice. Values
    // above 1 also go into the loudness stats in the log.
    @Override
    public void onRmsChanged(float rms) {
        if (!listening) {
            return;
        }
        if (rms > 1f) {
            rmsSum += rms;
            rmsCount++;
            rmsMax = Math.max(rmsMax, rms);
        }
        float v = Math.max(0f, Math.min(1f, (rms - 2f) / 8f));
        meter.setLevel((float) Math.pow(v, 2.5));
    }

    private String loudness() {
        String s = rmsCount == 0 ? "\trms none"
                : String.format(Locale.US, "\trms avg %.1f max %.1f", rmsSum / rmsCount, rmsMax);
        rmsSum = 0;
        rmsMax = 0;
        rmsCount = 0;
        return s;
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
        save("HEARD " + (parts.size() + 1) + "\t" + join(heard) + loudness());
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
                        reply(text, picking ? 15000 : 5000);
                    }
                });
            }

            // The app starts behind the panel. tvask says what it does and
            // fades out once it has said it.
            @Override
            public void onAction(String log, final String pkg, final String app, final int kind,
                    final String detail) {
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
                        opening(pkg, getContext().getString(now, app), detail);
                    }
                });
            }

            @Override
            public void onCard(final Object card) {
                post(myTurn, new Runnable() {
                    @Override
                    public void run() {
                        showCard(card);
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
        say(text, after);
    }

    // say reads text aloud and closes the panel after milliseconds past the
    // end of the reading.
    private void say(String text, long after) {
        if (!ttsReady || text.isEmpty()) {
            hideLater(after + 80L * text.length());
            return;
        }
        // A Turkish letter means Turkish. Otherwise read it as English.
        tts.setLanguage(text.matches("(?s).*[çğıöşüÇĞİÖŞÜ].*") ? TURKISH : Locale.US);
        tts.setSpeechRate(new Config(getContext()).speechRate);
        utterance = "u" + SystemClock.elapsedRealtime();
        afterSpeech = after;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance);
        // In case the TTS never reports the end.
        hideLater(after + 20000 + 100L * text.length());
    }

    // opening shows the app's opening card, says it, and fades out.
    private void opening(String pkg, String text, String detail) {
        state.setText("");
        line(answer, "");
        if (pkg != null) {
            put(Cards.opening(getContext(), pkg, text, detail));
        }
        say(text, 300);
    }

    // showCard draws card data from the brain under the answer.
    private void showCard(Object card) {
        Context c = getContext();
        if (card instanceof Weather.Forecast) {
            put(Cards.weather(c, (Weather.Forecast) card));
        } else if (card instanceof Device.Status) {
            put(Cards.status(c, (Device.Status) card));
        } else if (card instanceof Cards.AppList) {
            put(Cards.apps(c, (Cards.AppList) card));
        } else if (card instanceof Cards.Score) {
            put(Cards.score(c, (Cards.Score) card));
        } else if (card instanceof Cards.SiteList) {
            ViewGroup v = (ViewGroup) Cards.sites(c, ((Cards.SiteList) card).sites, new Cards.Pick() {
                @Override
                public void open(Cards.Site s) {
                    pick(s);
                }
            });
            put(v);
            picking = true;
            if (v.getChildCount() > 0) {
                v.getChildAt(0).requestFocus();
            }
        }
    }

    // pick opens the site the user chose with the remote.
    private void pick(Cards.Site s) {
        Actions a = new Actions(getContext(), new Config(getContext()).browser);
        save("PICK\t" + s.url);
        picking = false;
        if (!a.openUrl(s.url)) {
            return;
        }
        Conversation.show(said(""), getContext().getString(R.string.opened, s.title));
        opening(a.browser(), getContext().getString(R.string.opening, s.title), Uri.parse(s.url).getHost());
    }

    private void put(View v) {
        slot.removeAllViews();
        slot.addView(v);
        slot.setVisibility(View.VISIBLE);
        v.setAlpha(0f);
        v.animate().alpha(1f).setDuration(200).start();
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
