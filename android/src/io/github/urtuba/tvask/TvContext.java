package io.github.urtuba.tvask;

import android.app.assist.AssistContent;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// TvContext turns what Android shares with the assistant into short text for
// the model: what is playing, and the text of the app in front. tvask reads
// it only when the mic key is pressed, and never looks at the screen image.
final class TvContext {
    private static final int MAX_SCREEN = 1500;

    private TvContext() {
    }

    // playing describes the media sessions, or returns "" without access.
    static String playing(Context c) {
        List<MediaController> list;
        try {
            list = c.getSystemService(MediaSessionManager.class)
                    .getActiveSessions(new ComponentName(c, MediaListener.class));
        } catch (SecurityException e) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MediaController mc : list) {
            MediaMetadata md = mc.getMetadata();
            if (md == null) {
                continue;
            }
            String title = first(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
                    md.getString(MediaMetadata.METADATA_KEY_TITLE));
            if (title.isEmpty()) {
                continue;
            }
            PlaybackState st = mc.getPlaybackState();
            boolean on = st != null && st.getState() == PlaybackState.STATE_PLAYING;
            sb.append(on ? "Playing" : "Paused").append(" in ").append(Actions.label(c, mc.getPackageName()))
                    .append(": ").append(title);
            String more = first(md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
                    md.getString(MediaMetadata.METADATA_KEY_ARTIST));
            if (!more.isEmpty()) {
                sb.append(", ").append(more);
            }
            String album = md.getString(MediaMetadata.METADATA_KEY_ALBUM);
            if (!TextUtils.isEmpty(album)) {
                sb.append(", from ").append(album);
            }
            long length = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
            if (st != null && length > 0) {
                sb.append(String.format(Locale.US, ", at %s of %s", time(st.getPosition()), time(length)));
            }
            sb.append(".\n");
        }
        return sb.toString().trim();
    }

    // screen describes the app in front from its assist data: its name, the
    // link it shows, and its visible texts.
    static String screen(Context c, AssistStructure s, AssistContent content) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        ComponentName app = s.getActivityComponent();
        if (app != null && (dream(c, app) || home(c, app.getPackageName()))) {
            return "";
        }
        if (app != null) {
            sb.append("App in front: ").append(Actions.label(c, app.getPackageName())).append(".\n");
        }
        if (content != null) {
            Uri web = content.getWebUri();
            if (web != null) {
                sb.append("It shows: ").append(web).append('\n');
            }
            if (!TextUtils.isEmpty(content.getStructuredData())) {
                sb.append("Structured data: ").append(Http.cut(content.getStructuredData(), 500)).append('\n');
            }
        }
        Set<String> texts = new LinkedHashSet<>();
        for (int i = 0; i < s.getWindowNodeCount(); i++) {
            collect(s.getWindowNodeAt(i).getRootViewNode(), texts);
        }
        if (!texts.isEmpty()) {
            sb.append("Texts on screen: ").append(Http.cut(TextUtils.join(" | ", texts), MAX_SCREEN));
        }
        return sb.toString().trim();
    }

    // dream reports whether the app in front is the screen saver. Its text
    // is not about what the user watches, and it shows things like the
    // Wi-Fi name.
    private static boolean dream(Context c, ComponentName app) {
        if (app.getClassName().endsWith(".DreamActivity")) {
            return true;
        }
        String dreams = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
        return dreams != null && dreams.contains(app.getPackageName() + "/");
    }

    // home reports whether the app in front is a home screen. Its text is a
    // list of apps and rows, not what the user asks about.
    private static boolean home(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL)) {
            if (r.activityInfo.packageName.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    private static void collect(AssistStructure.ViewNode n, Set<String> out) {
        if (n == null) {
            return;
        }
        CharSequence t = n.getText();
        if (t == null) {
            t = n.getContentDescription();
        }
        if (t != null && t.toString().trim().length() > 1) {
            out.add(t.toString().trim());
        }
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChildAt(i), out);
        }
    }

    private static String first(CharSequence a, CharSequence b) {
        if (!TextUtils.isEmpty(a)) {
            return a.toString();
        }
        return TextUtils.isEmpty(b) ? "" : b.toString();
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
