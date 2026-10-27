package io.github.urtuba.tvask;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.Formatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Device answers questions about the TV itself without the AI model: which
// apps are open, closing them, and memory and storage use. It only uses
// public Android APIs. The texts it returns are ready to show and speak.
final class Device {
    // An app counts as open if it was used in the last WINDOW_MS and was
    // not closed by tvask after that.
    private static final long WINDOW_MS = 3 * 60 * 60 * 1000;
    private static final Map<String, Long> closedAt = new HashMap<>();

    private final Context c;
    private final Map<String, String> apps;

    // apps maps the launchable package names to labels, see Actions.apps.
    Device(Context c, Map<String, String> apps) {
        this.c = c;
        this.apps = apps;
    }

    // canSeeApps reports whether usage access was granted with adb.
    boolean canSeeApps() {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    // openApps returns the open apps, last used first.
    List<String> openApps() {
        long now = System.currentTimeMillis();
        UsageStatsManager usm = c.getSystemService(UsageStatsManager.class);
        final Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - WINDOW_MS, now);
        Set<String> skip = homeApps();
        List<String> out = new ArrayList<>();
        synchronized (closedAt) {
            for (Map.Entry<String, UsageStats> e : stats.entrySet()) {
                String pkg = e.getKey();
                long used = e.getValue().getLastTimeUsed();
                Long closed = closedAt.get(pkg);
                if (apps.containsKey(pkg) && !skip.contains(pkg) && used > now - WINDOW_MS
                        && (closed == null || closed < used)) {
                    out.add(pkg);
                }
            }
        }
        Collections.sort(out, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return Long.compare(stats.get(b).getLastTimeUsed(), stats.get(a).getLastTimeUsed());
            }
        });
        return out;
    }

    String openAppsText() {
        if (!canSeeApps()) {
            return c.getString(R.string.need_usage);
        }
        return openAppsText(openApps());
    }

    String openAppsText(List<String> open) {
        if (open.isEmpty()) {
            return c.getString(R.string.no_open_apps);
        }
        return c.getString(R.string.open_apps, TextUtils.join(", ", labels(open)));
    }

    // close closes the given apps, or all open apps if all is true. It goes
    // to the home screen first, because Android only stops apps that are in
    // the background. Run it on a background thread.
    String close(List<String> pkgs, boolean all) {
        if (all) {
            if (!canSeeApps()) {
                return c.getString(R.string.need_usage);
            }
            pkgs = openApps();
        }
        if (pkgs.isEmpty()) {
            return c.getString(R.string.no_open_apps);
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(home);
        SystemClock.sleep(1500);
        ActivityManager am = c.getSystemService(ActivityManager.class);
        long now = System.currentTimeMillis();
        for (String pkg : pkgs) {
            am.killBackgroundProcesses(pkg);
            synchronized (closedAt) {
                closedAt.put(pkg, now);
            }
        }
        return c.getString(R.string.closed, TextUtils.join(", ", labels(pkgs)));
    }

    // Status is memory, storage and uptime. It feeds the status card.
    static final class Status {
        long ramUsed, ramTotal, storeFree, storeTotal, upMinutes;
    }

    Status status() {
        ActivityManager am = c.getSystemService(ActivityManager.class);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        Status s = new Status();
        s.ramTotal = mi.totalMem;
        s.ramUsed = mi.totalMem - mi.availMem;
        s.storeFree = fs.getAvailableBytes();
        s.storeTotal = fs.getTotalBytes();
        s.upMinutes = SystemClock.elapsedRealtime() / 60000;
        return s;
    }

    // statusText describes memory, storage and uptime.
    String statusText(Status s) {
        return c.getString(R.string.status,
                Formatter.formatShortFileSize(c, s.ramUsed),
                Formatter.formatShortFileSize(c, s.ramTotal),
                (int) (100 * s.ramUsed / s.ramTotal),
                Formatter.formatShortFileSize(c, s.storeFree),
                Formatter.formatShortFileSize(c, s.storeTotal),
                (int) (s.upMinutes / (24 * 60)), (int) (s.upMinutes / 60 % 24));
    }

    private List<String> labels(List<String> pkgs) {
        List<String> out = new ArrayList<>();
        for (String p : pkgs) {
            out.add(apps.containsKey(p) ? apps.get(p) : p);
        }
        return out;
    }

    private Set<String> homeApps() {
        Set<String> out = new HashSet<>();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for (ResolveInfo r : c.getPackageManager().queryIntentActivities(i, PackageManager.MATCH_ALL)) {
            out.add(r.activityInfo.packageName);
        }
        out.add(c.getPackageName());
        return out;
    }
}
