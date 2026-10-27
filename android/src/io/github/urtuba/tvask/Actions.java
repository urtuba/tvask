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

// Actions does things on the TV for the model: open an app, open an app's
// search page, or open a web page in the browser.
final class Actions {
    // Search links per app, tested on a TCL Google TV. %s is the query.
    // Netflix is missing on purpose: it ignored both search and title links.
    // The browser, whichever it is, searches with WEB_SEARCH.
    private static final Map<String, String> SEARCH = new HashMap<>();
    private static final String WEB_SEARCH = "https://www.google.com/search?q=%s";
    // Google TV ships a stub that claims web links but only shows an error.
    private static final String STUBS = "com.google.android.tv.frameworkpackagestubs";

    static {
        SEARCH.put("com.google.android.youtube.tv", "https://www.youtube.com/results?search_query=%s");
        SEARCH.put("com.google.android.youtube.tvmusic", "https://music.youtube.com/search?q=%s");
        SEARCH.put("com.amazon.amazonvideo.livingroom", "https://app.primevideo.com/search?phrase=%s");
    }

    private final Context c;
    private final PackageManager pm;
    private final String preferredBrowser;

    // preferredBrowser is a package name, or empty for the first browser.
    Actions(Context c, String preferredBrowser) {
        this.c = c;
        this.pm = c.getPackageManager();
        this.preferredBrowser = preferredBrowser;
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
        return SEARCH.containsKey(pkg) || pkg.equals(browser());
    }

    // browser returns the package of the TV's web browser, or null. The
    // preferred browser wins if it is installed.
    String browser() {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        String first = null;
        for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(preferredBrowser)) {
                return pkg;
            }
            if (first == null && !pkg.equals(STUBS)) {
                first = pkg;
            }
        }
        return first;
    }

    // openUrl opens an http or https page in the browser. It returns false
    // if the link is not a web link or the TV has no browser.
    boolean openUrl(String url) {
        Uri u = Uri.parse(url);
        String browser = browser();
        if (browser == null || !("https".equals(u.getScheme()) || "http".equals(u.getScheme()))) {
            return false;
        }
        Intent i = new Intent(Intent.ACTION_VIEW, u);
        i.setPackage(browser);
        start(i);
        return true;
    }

    String label(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
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

    // searchIn opens the app's search page for query. Check canSearch first.
    void searchIn(String pkg, String query) {
        String link = SEARCH.containsKey(pkg) ? SEARCH.get(pkg) : WEB_SEARCH;
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(String.format(link, Uri.encode(query))));
        i.setPackage(pkg);
        start(i);
    }

    private void start(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
