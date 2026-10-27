package io.github.urtuba.tvask;

import android.service.voice.VoiceInteractionService;
import android.util.Log;

// AssistService is the assistant entry point. Android binds it while tvask is
// the default assistant, and opens an AssistSession on the remote's mic key.
public class AssistService extends VoiceInteractionService {
    @Override
    public void onReady() {
        super.onReady();
        Log.i(AssistSession.TAG, "assistant ready");
    }
}
