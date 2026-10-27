package io.github.urtuba.tvask;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

// BraveSearch uses the Brave Search API. It returns the top web results with
// their extra snippets, plus news and FAQ answers when Brave has them.
final class BraveSearch implements Search {
    private static final int COUNT = 5;
    private static final int EXTRA = 3;
    private final String key;

    BraveSearch(String key) {
        this.key = key;
    }

    @Override
    public String search(String query) throws IOException, JSONException {
        String url = "https://api.search.brave.com/res/v1/web/search?extra_snippets=true&count=" + COUNT
                + "&q=" + Uri.encode(query);
        JSONObject r = Http.get(url, "X-Subscription-Token", key);
        StringBuilder sb = new StringBuilder();
        JSONArray web = results(r, "web");
        for (int i = 0; i < web.length() && i < COUNT; i++) {
            JSONObject x = web.getJSONObject(i);
            sb.append(x.optString("title")).append('\n').append(x.optString("url")).append('\n');
            if (x.has("page_age")) {
                sb.append("Date: ").append(x.optString("page_age")).append('\n');
            }
            sb.append(x.optString("description")).append('\n');
            JSONArray extra = x.optJSONArray("extra_snippets");
            for (int k = 0; extra != null && k < extra.length() && k < EXTRA; k++) {
                sb.append("- ").append(extra.optString(k)).append('\n');
            }
            sb.append('\n');
        }
        JSONArray news = results(r, "news");
        for (int i = 0; i < news.length() && i < 3; i++) {
            JSONObject x = news.getJSONObject(i);
            sb.append("News: ").append(x.optString("title")).append(" (").append(x.optString("age")).append(")\n")
                    .append(x.optString("description")).append("\n\n");
        }
        JSONArray faq = results(r, "faq");
        for (int i = 0; i < faq.length() && i < 2; i++) {
            JSONObject x = faq.getJSONObject(i);
            sb.append("Q: ").append(x.optString("question")).append('\n')
                    .append("A: ").append(x.optString("answer")).append("\n\n");
        }
        String out = sb.toString().trim();
        return out.isEmpty() ? "No results." : out;
    }

    private static JSONArray results(JSONObject r, String section) {
        JSONObject s = r.optJSONObject(section);
        JSONArray a = s == null ? null : s.optJSONArray("results");
        return a == null ? new JSONArray() : a;
    }
}
