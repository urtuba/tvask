package io.github.urtuba.tvask;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Cards draws the panel's cards. Tools fill the data, see Brain, and the
// session places the card under the answer.
final class Cards {
    static final int ACCENT = Color.rgb(255, 210, 90);
    static final int MUTED = Color.rgb(154, 154, 154);
    static final int TRACK = Color.rgb(48, 48, 48);
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int DAYS = 5;
    private static final int MAX_APPS = 8;

    static final class Site {
        String title, url, note;
    }

    static final class Score {
        String home, away, competition, date, venue, note;
        int homeGoals, awayGoals;
        List<String> homeScorers, awayScorers;
    }

    static final class SiteList {
        final List<Site> sites = new ArrayList<>();
    }

    static final class AppList {
        final List<String> pkgs;

        AppList(List<String> pkgs) {
            this.pkgs = pkgs;
        }
    }

    // Pick opens the site the user chose with the remote.
    interface Pick {
        void open(Site s);
    }

    private Cards() {
    }

    static View weather(Context c, Weather.Forecast f) {
        LinearLayout card = column(c);
        LinearLayout top = row(c);
        LinearLayout now = column(c);
        now.addView(text(c, c.getString(R.string.wx_now, f.place), 14, MUTED));
        TextView temp = text(c, Math.round(f.temp) + "°", 36, Color.WHITE);
        temp.setTypeface(Typeface.DEFAULT_BOLD);
        now.addView(temp);
        now.addView(text(c, c.getString(Weather.label(f.code)) + " · "
                + c.getString(R.string.wx_detail, Math.round(f.feels), Math.round(f.wind)), 14, Color.WHITE));
        top.addView(now);
        card.addView(top);
        LinearLayout days = row(c);
        days.setPadding(0, 16, 0, 0);
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat out = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < f.days.size() && i < DAYS; i++) {
            Weather.Day d = f.days.get(i);
            LinearLayout col = column(c);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            String name;
            try {
                name = out.format(in.parse(d.date));
            } catch (ParseException e) {
                name = d.date;
            }
            col.addView(text(c, name, 14, MUTED));
            col.addView(text(c, c.getString(Weather.label(d.code)), 13, Color.WHITE));
            col.addView(text(c, Math.round(d.min) + "° / " + Math.round(d.max) + "°", 15, Color.WHITE));
            col.addView(text(c, "%" + d.rainChance, 14, ACCENT));
            days.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1));
        }
        card.addView(days, new LinearLayout.LayoutParams(MATCH, WRAP));
        return card;
    }

    // sites lists numbered options. Each row takes D-pad focus, and OK opens
    // it. The user can also say the number.
    static View sites(Context c, List<Site> sites, final Pick pick) {
        LinearLayout card = column(c);
        for (int i = 0; i < sites.size(); i++) {
            final Site s = sites.get(i);
            LinearLayout r = row(c);
            r.setPadding(12, 8, 12, 8);
            r.setFocusable(true);
            r.setClickable(true);
            TextView n = text(c, String.valueOf(i + 1), 20, ACCENT);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            n.setPadding(0, 0, 16, 0);
            r.addView(n);
            LinearLayout col = column(c);
            TextView title = text(c, s.title, 17, Color.WHITE);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(title);
            String host = Uri.parse(s.url).getHost();
            String note = TextUtils.isEmpty(s.note) ? "" : " · " + s.note;
            col.addView(text(c, (host == null ? s.url : host) + note, 14, MUTED));
            r.addView(col);
            final GradientDrawable bg = box(Color.TRANSPARENT);
            r.setBackground(bg);
            r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean has) {
                    bg.setStroke(2, has ? ACCENT : Color.TRANSPARENT);
                    bg.setColor(has ? TRACK : Color.TRANSPARENT);
                }
            });
            r.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pick.open(s);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.setMargins(0, 4, 0, 4);
            card.addView(r, lp);
        }
        return card;
    }

    static View score(Context c, Score s) {
        LinearLayout card = column(c);
        TextView meta = text(c, join(" · ", s.competition, s.date, s.venue), 14, MUTED);
        meta.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(meta, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout teams = row(c);
        teams.setGravity(Gravity.CENTER_VERTICAL);
        TextView home = text(c, s.home, 20, Color.WHITE);
        home.setTypeface(Typeface.DEFAULT_BOLD);
        home.setGravity(Gravity.END);
        TextView goals = text(c, s.homeGoals + " - " + s.awayGoals, 34, ACCENT);
        goals.setTypeface(Typeface.DEFAULT_BOLD);
        goals.setPadding(28, 0, 28, 0);
        TextView away = text(c, s.away, 20, Color.WHITE);
        away.setTypeface(Typeface.DEFAULT_BOLD);
        teams.addView(home, new LinearLayout.LayoutParams(0, WRAP, 1));
        teams.addView(goals);
        teams.addView(away, new LinearLayout.LayoutParams(0, WRAP, 1));
        card.addView(teams, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout scorers = row(c);
        TextView hs = text(c, TextUtils.join("\n", s.homeScorers), 14, Color.WHITE);
        hs.setGravity(Gravity.END);
        TextView as = text(c, TextUtils.join("\n", s.awayScorers), 14, MUTED);
        as.setPadding(0, 0, 0, 0);
        scorers.addView(hs, new LinearLayout.LayoutParams(0, WRAP, 1));
        scorers.addView(text(c, "", 14, MUTED), new LinearLayout.LayoutParams(120, WRAP));
        scorers.addView(as, new LinearLayout.LayoutParams(0, WRAP, 1));
        card.addView(scorers, new LinearLayout.LayoutParams(MATCH, WRAP));
        if (!TextUtils.isEmpty(s.note)) {
            TextView note = chip(c, s.note);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            lp.setMargins(0, 12, 0, 0);
            card.addView(note, lp);
        }
        return card;
    }

    static View apps(Context c, AppList a) {
        LinearLayout card = column(c);
        card.addView(text(c, c.getString(R.string.apps_count, a.pkgs.size()), 14, MUTED));
        LinearLayout r = row(c);
        r.setPadding(0, 12, 0, 0);
        PackageManager pm = c.getPackageManager();
        for (int i = 0; i < a.pkgs.size() && i < MAX_APPS; i++) {
            String pkg = a.pkgs.get(i);
            LinearLayout tile = column(c);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(0, 0, 28, 0);
            tile.addView(icon(c, pkg, 64), new LinearLayout.LayoutParams(64, 64));
            TextView label = text(c, label(pm, pkg), 13, Color.WHITE);
            label.setMaxWidth(130);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            tile.addView(label);
            r.addView(tile);
        }
        card.addView(r);
        return card;
    }

    static View status(Context c, Device.Status s) {
        LinearLayout card = column(c);
        double ram = (double) s.ramUsed / s.ramTotal;
        long storeUsed = s.storeTotal - s.storeFree;
        double store = (double) storeUsed / s.storeTotal;
        card.addView(metric(c, c.getString(R.string.st_memory),
                c.getString(R.string.st_used, Formatter.formatShortFileSize(c, s.ramUsed),
                        Formatter.formatShortFileSize(c, s.ramTotal)), ram));
        card.addView(metric(c, c.getString(R.string.st_storage),
                c.getString(R.string.st_used, Formatter.formatShortFileSize(c, storeUsed),
                        Formatter.formatShortFileSize(c, s.storeTotal)), store));
        card.addView(text(c, c.getString(R.string.st_up, (int) (s.upMinutes / (24 * 60)),
                (int) (s.upMinutes / 60 % 24)), 14, MUTED));
        return card;
    }

    // opening shows the app's TV banner, or its icon, while it starts.
    static View opening(Context c, String pkg, String title, String detail) {
        LinearLayout card = column(c);
        LinearLayout r = row(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        Drawable banner = null;
        try {
            banner = c.getPackageManager().getApplicationBanner(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            // No such app: the icon below falls back to the default.
        }
        ImageView img = new ImageView(c);
        if (banner != null) {
            img.setImageDrawable(banner);
            r.addView(img, new LinearLayout.LayoutParams(160, 90));
        } else {
            r.addView(icon(c, pkg, 72), new LinearLayout.LayoutParams(72, 72));
        }
        LinearLayout col = column(c);
        col.setPadding(24, 0, 0, 0);
        TextView t = text(c, title, 20, Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(t);
        if (!TextUtils.isEmpty(detail)) {
            col.addView(text(c, detail, 14, MUTED));
        }
        r.addView(col);
        card.addView(r);
        ProgressBar bar = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        bar.setIndeterminateTintList(ColorStateList.valueOf(ACCENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 12);
        lp.setMargins(0, 14, 0, 0);
        card.addView(bar, lp);
        return card;
    }

    private static View metric(Context c, String name, String value, double fraction) {
        LinearLayout col = column(c);
        LinearLayout r = row(c);
        r.addView(text(c, name, 15, Color.WHITE), new LinearLayout.LayoutParams(0, WRAP, 1));
        r.addView(text(c, value, 14, MUTED));
        col.addView(r, new LinearLayout.LayoutParams(MATCH, WRAP));
        LinearLayout track = row(c);
        track.setBackground(box(TRACK));
        View fill = new View(c);
        fill.setBackground(box(ACCENT));
        float f = (float) Math.max(0, Math.min(1, fraction));
        track.addView(fill, new LinearLayout.LayoutParams(0, MATCH, f));
        track.addView(new View(c), new LinearLayout.LayoutParams(0, MATCH, 1 - f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, 10);
        lp.setMargins(0, 6, 0, 14);
        col.addView(track, lp);
        return col;
    }

    private static ImageView icon(Context c, String pkg, int size) {
        ImageView img = new ImageView(c);
        try {
            img.setImageDrawable(c.getPackageManager().getApplicationIcon(pkg));
        } catch (PackageManager.NameNotFoundException e) {
            img.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        img.setMaxWidth(size);
        img.setMaxHeight(size);
        img.setAdjustViewBounds(true);
        return img;
    }

    private static String label(PackageManager pm, String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private static TextView chip(Context c, String s) {
        TextView t = text(c, s, 13, Color.rgb(207, 207, 207));
        GradientDrawable bg = box(Color.TRANSPARENT);
        bg.setStroke(2, Color.rgb(58, 58, 58));
        t.setBackground(bg);
        t.setPadding(12, 4, 12, 4);
        return t;
    }

    private static GradientDrawable box(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(4);
        return d;
    }

    private static String join(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!TextUtils.isEmpty(p)) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(p);
            }
        }
        return sb.toString();
    }

    private static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    private static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private static TextView text(Context c, String s, float size, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }
}
