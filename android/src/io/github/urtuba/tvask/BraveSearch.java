package io.github.urtuba.tvask;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

// BraveSearch uses the Brave Search API and returns the top results.
final class BraveSearch implements Search {
    private static final int COUNT = 5;
    private final String key;

    BraveSearch(String key) {
        this.key = key;
    }

    @Override
    public String search(String query) throws IOException, JSONException {
        String url = "https://api.search.brave.com/res/v1/web/search?count=" + COUNT
                + "&q=" + Uri.encode(query);
        JSONObject r = Http.get(url, "X-Subscription-Token", key);
        JSONObject web = r.optJSONObject("web");
        JSONArray results = web == null ? null : web.optJSONArray("results");
        if (results == null || results.length() == 0) {
            return "No results.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.length() && i < COUNT; i++) {
            JSONObject x = results.getJSONObject(i);
            sb.append(x.optString("title")).append('\n')
                    .append(x.optString("url")).append('\n')
                    .append(x.optString("description")).append("\n\n");
        }
        return sb.toString().trim();
    }
}
