package io.github.urtuba.tvask;

import android.os.Bundle;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

// AssistService is the assistant entry point. Android binds it while tvask is
// the default assistant, and opens an AssistSession on the remote's mic key.
public class AssistService extends VoiceInteractionService {
    // running is the bound service, so adb can open a session with text.
    private static volatile AssistService running;

    @Override
    public void onReady() {
        super.onReady();
        running = this;
        Log.i(AssistSession.TAG, "assistant ready");
    }

    @Override
    public void onShutdown() {
        running = null;
        super.onShutdown();
    }

    // ask opens the panel as if the user had said text. It returns false if
    // the assistant is not bound yet.
    static boolean ask(String text) {
        AssistService s = running;
        if (s == null) {
            return false;
        }
        Bundle args = new Bundle();
        args.putString(AssistSession.TEXT, text);
        s.showSession(args, 0);
        return true;
    }
}
