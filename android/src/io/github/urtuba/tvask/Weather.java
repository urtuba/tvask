package io.github.urtuba.tvask;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

// Weather gets the current weather and a seven day forecast from
// Open-Meteo. It needs no key. Open-Meteo is free for non-commercial use.
final class Weather {
    private Weather() {
    }

    static String forecast(String place) throws IOException, JSONException {
        String lang = Locale.getDefault().getLanguage();
        JSONObject geo = Http.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language="
                + lang + "&name=" + Uri.encode(place));
        JSONArray found = geo.optJSONArray("results");
        if (found == null || found.length() == 0) {
            return "Place not found: " + place;
        }
        JSONObject p = found.getJSONObject(0);
        JSONObject f = Http.get("https://api.open-meteo.com/v1/forecast?latitude=" + p.getDouble("latitude")
                + "&longitude=" + p.getDouble("longitude")
                + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                + "precipitation_sum,wind_speed_10m_max&timezone=auto&forecast_days=7");
        JSONObject now = f.getJSONObject("current");
        StringBuilder sb = new StringBuilder()
                .append(p.optString("name")).append(", ").append(p.optString("country"))
                .append(". Local time ").append(now.optString("time")).append(".\n")
                .append(String.format(Locale.US, "Now: %.0f C, feels like %.0f C, %s, wind %.0f km/h.\n",
                        now.optDouble("temperature_2m"), now.optDouble("apparent_temperature"),
                        code(now.optInt("weather_code")), now.optDouble("wind_speed_10m")));
        JSONObject d = f.getJSONObject("daily");
        JSONArray days = d.getJSONArray("time");
        for (int i = 0; i < days.length(); i++) {
            sb.append(String.format(Locale.US, "%s: %s, %.0f to %.0f C, rain chance %d%%, rain %.1f mm, wind up to %.0f km/h.\n",
                    days.getString(i), code(d.getJSONArray("weather_code").optInt(i)),
                    d.getJSONArray("temperature_2m_min").optDouble(i),
                    d.getJSONArray("temperature_2m_max").optDouble(i),
                    d.getJSONArray("precipitation_probability_max").optInt(i),
                    d.getJSONArray("precipitation_sum").optDouble(i),
                    d.getJSONArray("wind_speed_10m_max").optDouble(i)));
        }
        return sb.toString();
    }

    // code turns a WMO weather code into words.
    private static String code(int c) {
        if (c == 0) {
            return "clear sky";
        } else if (c <= 2) {
            return "partly cloudy";
        } else if (c == 3) {
            return "overcast";
        } else if (c == 45 || c == 48) {
            return "fog";
        } else if (c >= 51 && c <= 57) {
            return "drizzle";
        } else if (c >= 61 && c <= 67) {
            return "rain";
        } else if (c >= 71 && c <= 77) {
            return "snow";
        } else if (c >= 80 && c <= 82) {
            return "rain showers";
        } else if (c == 85 || c == 86) {
            return "snow showers";
        } else if (c >= 95) {
            return "thunderstorm";
        }
        return "weather code " + c;
    }
}
