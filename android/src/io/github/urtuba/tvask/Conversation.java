package io.github.urtuba.tvask;

import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

// Conversation keeps the last turns, so the user can ask follow-up
// questions and see the earlier ones. It lives in memory only and forgets
// after a few idle minutes.
final class Conversation {
    private static final long IDLE_MS = 5 * 60 * 1000;
    private static final int MAX_TURNS = 6;

    // turns holds the model messages of each turn. lines holds what the
    // panel shows: what the user said and the reply.
    private static final List<JSONArray> turns = new ArrayList<>();
    private static final List<String[]> lines = new ArrayList<>();
    private static long lastUse;

    private Conversation() {
    }

    // history returns the messages of the kept turns, oldest first.
    static synchronized JSONArray history() throws JSONException {
        expire();
        JSONArray all = new JSONArray();
        for (JSONArray turn : turns) {
            for (int i = 0; i < turn.length(); i++) {
                all.put(turn.get(i));
            }
        }
        return all;
    }

    static synchronized void add(JSONArray turn) {
        turns.add(turn);
        while (turns.size() > MAX_TURNS) {
            turns.remove(0);
        }
        lastUse = SystemClock.elapsedRealtime();
    }

    static synchronized List<String[]> shown() {
        expire();
        return new ArrayList<>(lines);
    }

    static synchronized void show(String said, String reply) {
        lines.add(new String[] {said, reply});
        while (lines.size() > MAX_TURNS) {
            lines.remove(0);
        }
        lastUse = SystemClock.elapsedRealtime();
    }

    private static void expire() {
        if (SystemClock.elapsedRealtime() - lastUse > IDLE_MS) {
            turns.clear();
            lines.clear();
        }
    }
}
