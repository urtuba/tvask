package io.github.urtuba.tvask;

import android.os.SystemClock;
import android.text.Html;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Web fetches pages from the TV's own connection. check tells whether a
// site answers. read returns a page as plain text for the model.
final class Web {
    private static final int MAX_BYTES = 1000000;
    private static final int MAX_TEXT = 6000;
    private static final String AGENT = "Mozilla/5.0 (Linux; Android 11; TV) tvask";
    private static final Pattern TITLE = Pattern.compile("<title[^>]*>(.*?)</title>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private Web() {
    }

    static String check(String url) {
        return fetch(url, false);
    }

    static String read(String url) {
        return fetch(url, true);
    }

    private static String fetch(String url, boolean withText) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return "Only http and https links work.";
        }
        long start = SystemClock.elapsedRealtime();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(10000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", AGENT);
            int code = c.getResponseCode();
            long ms = SystemClock.elapsedRealtime() - start;
            String type = c.getContentType() == null ? "" : c.getContentType();
            StringBuilder sb = new StringBuilder()
                    .append("Status ").append(code).append(' ').append(c.getResponseMessage())
                    .append(" in ").append(ms).append(" ms.\n")
                    .append("Final URL: ").append(c.getURL()).append('\n');
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            if (in == null || !(type.contains("html") || type.contains("text"))) {
                return sb.append("Content type: ").append(type).toString();
            }
            Matcher cs = CHARSET.matcher(type);
            String html = read(in, cs.find() ? cs.group(1) : "UTF-8");
            Matcher t = TITLE.matcher(html);
            if (t.find()) {
                sb.append("Title: ").append(plain(t.group(1))).append('\n');
            }
            if (withText) {
                sb.append("Text:\n").append(Http.cut(text(html), MAX_TEXT));
            }
            return sb.toString();
        } catch (UnknownHostException e) {
            return "Cannot reach the site: unknown host.";
        } catch (SocketTimeoutException e) {
            return "The site did not answer within 10 seconds.";
        } catch (IOException | RuntimeException e) {
            return "Cannot reach the site: " + e.getMessage();
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static String read(InputStream in, String charset) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while (out.size() < MAX_BYTES && (n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString(charset);
        }
    }

    // text drops scripts, styles and tags, and keeps one line per block.
    private static String text(String html) {
        String s = html.replaceAll("(?is)<(script|style|noscript|svg|head)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<!--.*?-->", " ")
                .replaceAll("(?i)<(br|/p|/div|/li|/h[1-6]|/tr)[^>]*>", "\n")
                .replaceAll("<[^>]+>", " ");
        return plain(s).replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim();
    }

    // plain decodes HTML entities.
    private static String plain(String s) {
        return Html.fromHtml(s.replace("\n", "<br>"), Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }
}
