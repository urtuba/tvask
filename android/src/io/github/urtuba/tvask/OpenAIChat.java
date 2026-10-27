package io.github.urtuba.tvask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

// OpenAIChat talks to any provider with an OpenAI compatible
// /chat/completions endpoint. The default is DeepSeek.
final class OpenAIChat implements Chat {
    private final String url, key, model, effort;

    OpenAIChat(String baseUrl, String key, String model, String effort) {
        this.url = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        this.key = key;
        this.model = model;
        this.effort = effort;
    }

    @Override
    public JSONObject complete(JSONArray messages, JSONArray tools) throws IOException, JSONException {
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("messages", messages)
                .put("tools", tools);
        if (!effort.isEmpty()) {
            body.put("reasoning_effort", effort);
        }
        JSONObject r = Http.post(url, body, "Authorization", "Bearer " + key);
        return r.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
    }
}
