package io.github.urtuba.tvask;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

// AssistSession runs while tvask is on screen. It asks Google's recognizer for
// the words, because only Google's remote service gets the remote's audio.
// The recognizer uses the TV's language.
public class AssistSession extends VoiceInteractionSession implements RecognitionListener {
    static final String TAG = "tvask";
    static final ComponentName GOOGLE = new ComponentName("com.google.android.katniss",
            "com.google.android.apps.tvsearch.voice.recognition.KatnissRecognitionService");

    TextView state, words;
    SpeechRecognizer sr;
    String last = "";

    AssistSession(Context c) {
        super(c);
    }

    @Override
    public View onCreateContentView() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(64, 40, 64, 40);
        box.setBackgroundColor(Color.argb(235, 16, 16, 16));
        TextView title = new TextView(getContext());
        title.setText(R.string.app_name);
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        state = new TextView(getContext());
        state.setTextSize(22);
        state.setTextColor(Color.LTGRAY);
        words = new TextView(getContext());
        words.setTextSize(30);
        words.setTextColor(Color.rgb(255, 210, 90));
        box.addView(title);
        box.addView(state);
        box.addView(words);
        return box;
    }

    @Override
    public void onShow(Bundle args, int flags) {
        super.onShow(args, flags);
        Log.i(TAG, "show, flags " + flags);
        state.setText(R.string.listening);
        words.setText("");
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
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
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
        Log.i(TAG, "error " + e);
        state.setText(getContext().getString(R.string.error, e));
        save("ERROR " + e + "\tlast partial: " + last);
    }

    @Override
    public void onResults(Bundle r) {
        String s = first(r);
        Log.i(TAG, "result: " + s);
        save("RESULT\t" + s);
        state.setText(R.string.heard);
        words.setText(s);
    }

    @Override
    public void onPartialResults(Bundle r) {
        last = first(r);
        words.setText(last);
    }

    @Override
    public void onEvent(int type, Bundle p) {
    }

    // save appends a line to files/transcripts.txt in the app's external
    // storage, because logcat on some TVs rotates within minutes.
    void save(String line) {
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

    static String first(Bundle r) {
        ArrayList<String> l = r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() ? "" : l.get(0);
    }
}
