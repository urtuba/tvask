package io.github.urtuba.tvask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

// Brain turns what the user said into an answer or an action. It calls the
// chat model in a loop: the model may search the web first, then answer, or
// it may open an app, which ends the turn. Run it on a background thread.
final class Brain {
    interface Listener {
        void onStatus(int text);

        void onAnswer(String text);

        void onAction(String log);

        void onFailure(String message);
    }

    private static final int MAX_ROUNDS = 4;

    private final Chat chat;
    private final Search search;
    private final Actions actions;

    Brain(Chat chat, Search search, Actions actions) {
        this.chat = chat;
        this.search = search;
        this.actions = actions;
    }

    // run takes up to five recognizer guesses, best first.
    void run(List<String> heard, Listener l) {
        try {
            Map<String, String> apps = actions.apps();
            JSONArray msgs = new JSONArray()
                    .put(message("system", prompt(apps)))
                    .put(message("user", guesses(heard)));
            JSONArray tools = tools(apps);
            for (int round = 0; round < MAX_ROUNDS; round++) {
                l.onStatus(R.string.thinking);
                JSONObject m = chat.complete(msgs, tools);
                JSONArray calls = m.optJSONArray("tool_calls");
                if (calls == null || calls.length() == 0) {
                    l.onAnswer(m.isNull("content") ? "" : m.optString("content").trim());
                    return;
                }
                msgs.put(m);
                String acted = null;
                for (int i = 0; i < calls.length(); i++) {
                    JSONObject call = calls.getJSONObject(i);
                    JSONObject fn = call.getJSONObject("function");
                    JSONObject args = new JSONObject(fn.optString("arguments", "{}"));
                    String name = fn.optString("name");
                    String result;
                    if (name.equals("web_search")) {
                        l.onStatus(R.string.searching);
                        result = search.search(args.optString("query"));
                    } else if (name.equals("open_app")) {
                        String pkg = args.optString("package");
                        result = actions.open(pkg) ? "Opened." : "App not found.";
                        acted = "open_app " + pkg;
                    } else if (name.equals("search_in_app")) {
                        String pkg = args.optString("package");
                        String q = args.optString("query");
                        result = actions.searchIn(pkg, q);
                        acted = "search_in_app " + pkg + " " + q;
                    } else {
                        result = "Unknown tool.";
                    }
                    msgs.put(new JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.optString("id"))
                            .put("content", result));
                }
                if (acted != null) {
                    l.onAction(acted);
                    return;
                }
            }
            l.onFailure("no answer after " + MAX_ROUNDS + " rounds");
        } catch (IOException | JSONException | RuntimeException e) {
            l.onFailure(e.getMessage());
        }
    }

    private static String prompt(Map<String, String> apps) {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm, EEEE", Locale.ENGLISH);
        StringBuilder sb = new StringBuilder()
                .append("You are tvask, a voice assistant on an Android TV.\n")
                .append("The user speaks Turkish or English and often mixes both in one sentence.\n")
                .append("You get up to 5 guesses from a speech recognizer, best first.\n")
                .append("The guesses can be wrong. English words are often spelled the Turkish way.\n")
                .append("Work out what the user meant.\n")
                .append("To open an app, call open_app. To find a show, film, video or song in an app, call search_in_app.\n")
                .append("Call web_search for news, weather, scores, prices, or anything you are not sure about.\n")
                .append("Otherwise answer directly.\n")
                .append("Your answer is shown on the TV and read aloud.\n")
                .append("Reply in the user's language, in at most 3 short sentences.\n")
                .append("Do not use markdown, lists or links.\n")
                .append("Now: ").append(f.format(new Date()))
                .append(", time zone ").append(TimeZone.getDefault().getID()).append(".\n")
                .append("Installed apps (package: name):\n");
        for (Map.Entry<String, String> e : apps.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static String guesses(List<String> heard) {
        StringBuilder sb = new StringBuilder("Speech recognizer guesses:\n");
        for (int i = 0; i < heard.size(); i++) {
            sb.append(i + 1).append(". ").append(heard.get(i)).append('\n');
        }
        return sb.toString();
    }

    private static JSONArray tools(Map<String, String> apps) throws JSONException {
        JSONArray pkgs = new JSONArray();
        for (String p : apps.keySet()) {
            pkgs.put(p);
        }
        return new JSONArray()
                .put(tool("open_app", "Open an installed app.",
                        new JSONObject().put("package", prop("Package name of the app.", pkgs))))
                .put(tool("search_in_app", "Open an app and search in it for a show, film, video or song.",
                        new JSONObject()
                                .put("package", prop("Package name of the app.", pkgs))
                                .put("query", prop("What to search for, in its original language.", null))))
                .put(tool("web_search", "Search the web. Results are short snippets.",
                        new JSONObject().put("query", prop("Search query.", null))));
    }

    private static JSONObject tool(String name, String desc, JSONObject props) throws JSONException {
        JSONArray required = new JSONArray();
        for (Iterator<String> it = props.keys(); it.hasNext(); ) {
            required.put(it.next());
        }
        return new JSONObject().put("type", "function").put("function", new JSONObject()
                .put("name", name)
                .put("description", desc)
                .put("parameters", new JSONObject()
                        .put("type", "object")
                        .put("properties", props)
                        .put("required", required)));
    }

    private static JSONObject prop(String desc, JSONArray values) throws JSONException {
        JSONObject p = new JSONObject().put("type", "string").put("description", desc);
        if (values != null) {
            p.put("enum", values);
        }
        return p;
    }

    private static JSONObject message(String role, String content) throws JSONException {
        return new JSONObject().put("role", role).put("content", content);
    }
}
