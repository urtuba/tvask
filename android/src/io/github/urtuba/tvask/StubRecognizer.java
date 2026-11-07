package io.github.urtuba.tvask;

import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

// StubRecognizer exists because an assistant must name a recognition service.
// When tvask holds the assistant role, Android makes it the TV's default
// recognizer at every boot. So it passes each request on to Google's
// recognizer, and voice typing in other apps keeps working.
// AssistSession calls Google's recognizer directly and does not come here.
public class StubRecognizer extends RecognitionService {
    private SpeechRecognizer sr;

    @Override
    protected void onStartListening(Intent i, final Callback cb) {
        stop();
        if (getPackageManager().resolveService(new Intent().setComponent(AssistSession.GOOGLE), 0) == null) {
            send(cb, SpeechRecognizer.ERROR_CLIENT);
            return;
        }
        sr = SpeechRecognizer.createSpeechRecognizer(this, AssistSession.GOOGLE);
        sr.setRecognitionListener(new Forward(cb));
        sr.startListening(i);
    }

    @Override
    protected void onCancel(Callback cb) {
        stop();
    }

    @Override
    protected void onStopListening(Callback cb) {
        if (sr != null) {
            sr.stopListening();
        }
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    private void stop() {
        if (sr != null) {
            sr.destroy();
            sr = null;
        }
    }

    private static void send(Callback cb, int error) {
        try {
            cb.error(error);
        } catch (RemoteException e) {
            // The caller is gone.
        }
    }

    // Forward hands Google's events to the app that asked.
    private static class Forward implements RecognitionListener {
        private final Callback cb;

        Forward(Callback cb) {
            this.cb = cb;
        }

        @Override
        public void onReadyForSpeech(Bundle b) {
            try {
                cb.readyForSpeech(b);
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onBeginningOfSpeech() {
            try {
                cb.beginningOfSpeech();
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onRmsChanged(float rms) {
            try {
                cb.rmsChanged(rms);
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onBufferReceived(byte[] buf) {
            try {
                cb.bufferReceived(buf);
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onEndOfSpeech() {
            try {
                cb.endOfSpeech();
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onError(int error) {
            send(cb, error);
        }

        @Override
        public void onResults(Bundle b) {
            try {
                cb.results(b);
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onPartialResults(Bundle b) {
            try {
                cb.partialResults(b);
            } catch (RemoteException e) {
                // The caller is gone.
            }
        }

        @Override
        public void onEvent(int type, Bundle b) {
        }
    }
}
