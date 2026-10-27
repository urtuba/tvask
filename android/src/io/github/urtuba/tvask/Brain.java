package io.github.urtuba.tvask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

        // onAction runs after an app was opened. kind is OPENED, SEARCHED,
        // or NO_SEARCH when the user asked to search in an app that has no
        // search link.
        void onAction(String log, String app, int kind);

        void onFailure(String message);
    }

    static final int OPENED = 0, SEARCHED = 1, NO_SEARCH = 2;
    private static final int MAX_ROUNDS = 4;

    private final Chat chat;
    private final Search search;
    private final Actions actions;

    Brain(Chat chat, Search search, Actions actions) {
        this.chat = chat;
        this.search = search;
        this.actions = actions;
    }

    // run takes what the user said as parts, one per stretch of speech.
    // Each part has up to five recognizer guesses, best first. Earlier turns
    // come from Conversation, and this turn is added to it at the end.
    void run(List<List<String>> parts, Listener l) {
        try {
            Map<String, String> apps = actions.apps();
            // turn keeps this turn's messages for Conversation.
            JSONArray turn = new JSONArray().put(message("user", guesses(parts)));
            JSONArray msgs = new JSONArray().put(message("system", prompt(apps)));
            JSONArray history = Conversation.history();
            for (int i = 0; i < history.length(); i++) {
                msgs.put(history.get(i));
            }
            msgs.put(turn.get(0));
            JSONArray tools = tools(apps);
            for (int round = 0; round < MAX_ROUNDS; round++) {
                l.onStatus(R.string.thinking);
                JSONObject m = chat.complete(msgs, tools);
                turn.put(withoutReasoning(m));
                JSONArray calls = m.optJSONArray("tool_calls");
                if (calls == null || calls.length() == 0) {
                    Conversation.add(turn);
                    l.onAnswer(m.isNull("content") ? "" : m.optString("content").trim());
                    return;
                }
                msgs.put(m);
                String acted = null;
                String app = null;
                int kind = OPENED;
                // direct is a device result that tvask shows as is,
                // without asking the model again.
                String direct = null;
                Device device = actions.device(apps);
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
                        app = actions.label(pkg);
                    } else if (name.equals("open_url")) {
                        String url = args.optString("url");
                        result = actions.openUrl(url) ? "Opened." : "Cannot open this link.";
                        acted = "open_url " + url;
                        String browser = actions.browser();
                        app = browser == null ? "browser" : actions.label(browser);
                    } else if (name.equals("search_in_app")) {
                        String pkg = args.optString("package");
                        String q = args.optString("query");
                        if (actions.canSearch(pkg)) {
                            actions.searchIn(pkg, q);
                            result = "Opened the search page.";
                            kind = SEARCHED;
                        } else {
                            result = actions.open(pkg) ? "No search link, only opened." : "App not found.";
                            kind = NO_SEARCH;
                        }
                        acted = "search_in_app " + pkg + " " + q;
                        app = actions.label(pkg);
                    } else if (name.equals("list_apps")) {
                        result = device.openAppsText();
                        direct = result;
                    } else if (name.equals("close_apps")) {
                        List<String> pkgs = new ArrayList<>();
                        JSONArray a = args.optJSONArray("packages");
                        for (int k = 0; a != null && k < a.length(); k++) {
                            pkgs.add(a.getString(k));
                        }
                        result = device.close(pkgs, args.optBoolean("all"));
                        direct = result;
                    } else if (name.equals("system_status")) {
                        result = device.statusText();
                        direct = result;
                    } else {
                        result = "Unknown tool.";
                    }
                    JSONObject reply = new JSONObject()
                            .put("role", "tool")
                            .put("tool_call_id", call.optString("id"))
                            .put("content", result);
                    msgs.put(reply);
                    turn.put(reply);
                }
                if (acted != null) {
                    Conversation.add(turn);
                    l.onAction(acted, app, kind);
                    return;
                }
                if (direct != null) {
                    Conversation.add(turn);
                    l.onAnswer(direct);
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
                .append("Work out what the user meant. Earlier turns of this conversation come before it.\n")
                .append("To open an app, call open_app. To find a show, film, video or song in an app, call search_in_app.\n")
                .append("To show a web page, call open_url with a full https link. To show web search results, call search_in_app with the browser.\n")
                .append("Only call these when the user clearly asks for an app, a page, or something to watch or play.\n")
                .append("If the guesses make no sense, never guess an app. Say in one short sentence that you did not understand.\n")
                .append("To see which apps are open, call list_apps. To close apps, call close_apps.\n")
                .append("For the TV's memory, storage or uptime, call system_status.\n")
                .append("Call web_search for news, weather, scores, prices, or anything you are not sure about.\n")
                .append("Otherwise answer directly.\n")
                .append("Your answer is shown on the TV and read aloud.\n")
                .append("Reply in the language of the request, in one language only, in at most 3 short sentences.\n")
                .append("Do not use markdown, lists or links.\n")
                .append("Now: ").append(f.format(new Date()))
                .append(", time zone ").append(TimeZone.getDefault().getID()).append(".\n")
                .append("Installed apps (package: name):\n");
        for (Map.Entry<String, String> e : apps.entrySet()) {
            sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    private static String guesses(List<List<String>> parts) {
        StringBuilder sb = new StringBuilder();
        if (parts.size() > 1) {
            sb.append("The user spoke in ").append(parts.size())
                    .append(" parts with short pauses. Read them as one request.\n");
        }
        for (int p = 0; p < parts.size(); p++) {
            if (parts.size() > 1) {
                sb.append("Part ").append(p + 1).append(". ");
            }
            sb.append("Speech recognizer guesses:\n");
            List<String> heard = parts.get(p);
            for (int i = 0; i < heard.size(); i++) {
                sb.append(i + 1).append(". ").append(heard.get(i)).append('\n');
            }
        }
        return sb.toString();
    }

    // withoutReasoning drops the model's reasoning from a message before it
    // goes into Conversation, to keep later requests small.
    private static JSONObject withoutReasoning(JSONObject m) throws JSONException {
        JSONObject copy = new JSONObject(m.toString());
        copy.remove("reasoning_content");
        return copy;
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
                .put(tool("open_url", "Open a web page in the TV's browser.",
                        new JSONObject().put("url", prop("Full https link.", null))))
                .put(tool("web_search", "Search the web. Results are short snippets.",
                        new JSONObject().put("query", prop("Search query.", null))))
                .put(tool("list_apps", "List the apps that are open on the TV. The result is shown to the user.",
                        new JSONObject()))
                .put(new JSONObject().put("type", "function").put("function", new JSONObject()
                        .put("name", "close_apps")
                        .put("description", "Close apps on the TV. The result is shown to the user.")
                        .put("parameters", new JSONObject()
                                .put("type", "object")
                                .put("properties", new JSONObject()
                                        .put("packages", new JSONObject()
                                                .put("type", "array")
                                                .put("description", "Package names of the apps to close.")
                                                .put("items", prop("Package name.", pkgs)))
                                        .put("all", new JSONObject()
                                                .put("type", "boolean")
                                                .put("description", "True to close all open apps.")))
                                .put("required", new JSONArray()))))
                .put(tool("system_status", "Show the TV's memory, storage and uptime to the user.",
                        new JSONObject()));
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
