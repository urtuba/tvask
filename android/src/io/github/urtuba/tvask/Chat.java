package io.github.urtuba.tvask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

// Chat is a chat model that can call tools. Each provider is an adapter
// that implements it. Messages and tools use the OpenAI chat format.
interface Chat {
    // complete returns the model's next message. The message either has
    // tool_calls or a text answer in content.
    JSONObject complete(JSONArray messages, JSONArray tools) throws IOException, JSONException;
}
