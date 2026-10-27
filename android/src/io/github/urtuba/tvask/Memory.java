package io.github.urtuba.tvask;

import android.content.Context;
import android.text.format.Formatter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Pattern;

// Memory keeps notes about the user as small Markdown files in the app's
// private storage. The model writes them as it learns, one topic per note.
// Every prompt carries an index of the notes and the "profile" note in
// full. The memory_size setting caps the total size; 0 turns memory off.
final class Memory {
    static final String PROFILE = "profile";
    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private static final int PROFILE_IN_PROMPT = 2000;
    private static final int MAX_NOTE = 4096;
    private static final int MAX_DESCRIPTION = 100;

    private final Context c;
    private final File dir;
    private final long limit;

    Memory(Context c, long limit) {
        this.c = c;
        this.dir = new File(c.getFilesDir(), "memory");
        this.limit = limit;
    }

    boolean on() {
        return limit > 0;
    }

    long used() {
        long n = 0;
        for (File f : files()) {
            n += f.length();
        }
        return n;
    }

    // prompt returns the notes part of the system prompt.
    String prompt() {
        StringBuilder sb = new StringBuilder()
                .append("You keep notes about this user in a small memory of Markdown notes.\n")
                .append("Save lasting facts and preferences the user shares or that you learn: city, teams, favorite apps, habits.\n")
                .append("One topic per note. Update a note instead of starting a new one. Keep notes short.\n")
                .append("Put the most useful facts in the note named profile.\n")
                .append("Never save passwords, keys, or one-off questions.\n")
                .append(String.format("Memory use: %s of %s.\n",
                        Formatter.formatShortFileSize(c, used()), Formatter.formatShortFileSize(c, limit)));
        File[] notes = files();
        if (notes.length == 0) {
            return sb.append("There are no notes yet.\n").toString();
        }
        sb.append("Notes (name: description):\n");
        for (File f : notes) {
            sb.append(base(f)).append(": ").append(description(f)).append('\n');
        }
        File profile = file(PROFILE);
        if (profile.exists()) {
            sb.append("The profile note:\n").append(Http.cut(read(profile), PROFILE_IN_PROMPT)).append('\n');
        }
        return sb.toString();
    }

    String read(String name) {
        File f = file(name);
        return valid(name) && f.exists() ? read(f) : "No note named " + name + ".";
    }

    // write saves a note. The first line holds the description for the index.
    String write(String name, String description, String content) {
        if (!valid(name)) {
            return "Use a short lowercase name with letters, digits and dashes.";
        }
        if (content.length() > MAX_NOTE) {
            return "Note too long. Keep a note under about 4 KB.";
        }
        File f = file(name);
        String d = Http.cut(description.replace('\n', ' '), MAX_DESCRIPTION);
        byte[] data = ("description: " + d + "\n\n" + content).getBytes(StandardCharsets.UTF_8);
        long after = used() - (f.exists() ? f.length() : 0) + data.length;
        if (after > limit) {
            return "Memory is full: " + Formatter.formatShortFileSize(c, limit)
                    + ". Shorten or delete notes first.";
        }
        dir.mkdirs();
        try (OutputStream os = new FileOutputStream(f)) {
            os.write(data);
            return "Saved.";
        } catch (IOException e) {
            return "Cannot save: " + e.getMessage();
        }
    }

    String delete(String name) {
        File f = file(name);
        return valid(name) && f.delete() ? "Deleted." : "No note named " + name + ".";
    }

    void clear() {
        for (File f : files()) {
            f.delete();
        }
    }

    private File[] files() {
        File[] all = dir.listFiles();
        if (all == null) {
            return new File[0];
        }
        Arrays.sort(all);
        return all;
    }

    private File file(String name) {
        return new File(dir, name + ".md");
    }

    private static boolean valid(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    private static String base(File f) {
        String n = f.getName();
        return n.endsWith(".md") ? n.substring(0, n.length() - 3) : n;
    }

    // description reads only the note's first line.
    private static String description(File f) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f),
                StandardCharsets.UTF_8))) {
            String first = r.readLine();
            return first != null && first.startsWith("description: ") ? first.substring(13) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int n = 0;
            while (n < b.length) {
                int r = in.read(b, n, b.length - n);
                if (r < 0) {
                    break;
                }
                n += r;
            }
            return new String(b, 0, n, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
