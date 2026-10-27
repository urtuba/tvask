package io.github.urtuba.tvask;

import android.content.Intent;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

// StubRecognizer exists because an assistant must name a recognition service.
// AssistSession uses Google's recognizer instead.
public class StubRecognizer extends RecognitionService {
    @Override
    protected void onStartListening(Intent i, Callback cb) {
        try {
            cb.error(SpeechRecognizer.ERROR_CLIENT);
        } catch (RemoteException e) {
            // The caller is gone.
        }
    }

    @Override
    protected void onCancel(Callback cb) {
    }

    @Override
    protected void onStopListening(Callback cb) {
    }
}
