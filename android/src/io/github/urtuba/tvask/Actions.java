package io.github.urtuba.tvask;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Actions does things on the TV for the model: open an app, or open an
// app's search page.
final class Actions {
    // Search links per app, tested on a TCL Google TV. %s is the query.
    private static final Map<String, String> SEARCH = new HashMap<>();

    static {
        SEARCH.put("com.google.android.youtube.tv", "https://www.youtube.com/results?search_query=%s");
        SEARCH.put("com.google.android.youtube.tvmusic", "https://music.youtube.com/search?q=%s");
        SEARCH.put("com.netflix.ninja", "nflx://www.netflix.com/search?q=%s");
        SEARCH.put("com.amazon.amazonvideo.livingroom", "https://app.primevideo.com/search?phrase=%s");
    }

    private final Context c;
    private final PackageManager pm;

    Actions(Context c) {
        this.c = c;
        this.pm = c.getPackageManager();
    }

    // apps returns the launchable TV apps as package name to label.
    Map<String, String> apps() {
        Map<String, String> out = new LinkedHashMap<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> list = pm.queryIntentActivities(i, 0);
        for (ResolveInfo r : list) {
            String pkg = r.activityInfo.packageName;
            if (!pkg.equals(c.getPackageName())) {
                out.put(pkg, r.loadLabel(pm).toString());
            }
        }
        return out;
    }

    boolean canSearch(String pkg) {
        return SEARCH.containsKey(pkg);
    }

    // open starts the app and returns false if it has no launch activity.
    boolean open(String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) {
            i = pm.getLaunchIntentForPackage(pkg);
        }
        if (i == null) {
            return false;
        }
        start(i);
        return true;
    }

    // searchIn opens the app's search page for query. Apps without a known
    // search link are only opened. It returns a result line for the model.
    String searchIn(String pkg, String query) {
        String link = SEARCH.get(pkg);
        if (link == null) {
            return open(pkg) ? "This app has no search link, so it was only opened." : "App not found.";
        }
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(link, Uri.encode(query))));
        i.setPackage(pkg);
        start(i);
        return "Opened the search page.";
    }

    private void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
