package io.github.urtuba.tvask;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Http sends JSON requests. Headers come as name and value pairs.
final class Http {
    private Http() {
    }

    static JSONObject get(String url, String... headers) throws IOException, JSONException {
        return send(open(url, headers), null);
    }

    static JSONObject post(String url, JSONObject body, String... headers) throws IOException, JSONException {
        HttpURLConnection c = open(url, headers);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        return send(c, body.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static HttpURLConnection open(String url, String[] headers) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            c.setRequestProperty(headers[i], headers[i + 1]);
        }
        return c;
    }

    private static JSONObject send(HttpURLConnection c, byte[] body) throws IOException, JSONException {
        try {
            if (body != null) {
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body);
                }
            }
            int code = c.getResponseCode();
            String text = read(code < 400 ? c.getInputStream() : c.getErrorStream());
            if (code >= 400) {
                throw new IOException("HTTP " + code + ": " + cut(text, 200));
            }
            return new JSONObject(text);
        } finally {
            c.disconnect();
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        }
    }

    static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
