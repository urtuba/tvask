package io.github.urtuba.tvask;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Weather gets the current weather and a seven day forecast from
// Open-Meteo. It needs no key. Open-Meteo is free for non-commercial use.
final class Weather {
    static final class Day {
        String date;
        int code, rainChance;
        double min, max, rainMm, wind;
    }

    // Forecast is both the model's tool result and the weather card's data.
    static final class Forecast {
        String place, country, time;
        int code;
        double temp, feels, wind;
        final List<Day> days = new ArrayList<>();

        String text() {
            StringBuilder sb = new StringBuilder()
                    .append(place).append(", ").append(country)
                    .append(". Local time ").append(time).append(".\n")
                    .append(String.format(Locale.US, "Now: %.0f C, feels like %.0f C, %s, wind %.0f km/h.\n",
                            temp, feels, words(code), wind));
            for (Day d : days) {
                sb.append(String.format(Locale.US,
                        "%s: %s, %.0f to %.0f C, rain chance %d%%, rain %.1f mm, wind up to %.0f km/h.\n",
                        d.date, words(d.code), d.min, d.max, d.rainChance, d.rainMm, d.wind));
            }
            return sb.toString();
        }
    }

    private Weather() {
    }

    // forecast returns null if Open-Meteo does not know the place.
    static Forecast forecast(String place) throws IOException, JSONException {
        String lang = Locale.getDefault().getLanguage();
        JSONObject geo = Http.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language="
                + lang + "&name=" + Uri.encode(place));
        JSONArray found = geo.optJSONArray("results");
        if (found == null || found.length() == 0) {
            return null;
        }
        JSONObject p = found.getJSONObject(0);
        JSONObject f = Http.get("https://api.open-meteo.com/v1/forecast?latitude=" + p.getDouble("latitude")
                + "&longitude=" + p.getDouble("longitude")
                + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                + "precipitation_sum,wind_speed_10m_max&timezone=auto&forecast_days=7");
        Forecast out = new Forecast();
        out.place = p.optString("name");
        out.country = p.optString("country");
        JSONObject now = f.getJSONObject("current");
        out.time = now.optString("time");
        out.code = now.optInt("weather_code");
        out.temp = now.optDouble("temperature_2m");
        out.feels = now.optDouble("apparent_temperature");
        out.wind = now.optDouble("wind_speed_10m");
        JSONObject d = f.getJSONObject("daily");
        JSONArray dates = d.getJSONArray("time");
        for (int i = 0; i < dates.length(); i++) {
            Day day = new Day();
            day.date = dates.getString(i);
            day.code = d.getJSONArray("weather_code").optInt(i);
            day.min = d.getJSONArray("temperature_2m_min").optDouble(i);
            day.max = d.getJSONArray("temperature_2m_max").optDouble(i);
            day.rainChance = d.getJSONArray("precipitation_probability_max").optInt(i);
            day.rainMm = d.getJSONArray("precipitation_sum").optDouble(i);
            day.wind = d.getJSONArray("wind_speed_10m_max").optDouble(i);
            out.days.add(day);
        }
        return out;
    }

    // words turns a WMO weather code into English words for the model.
    static String words(int c) {
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

    // label turns a WMO weather code into a string resource for the card.
    static int label(int c) {
        if (c == 0) {
            return R.string.wx_clear;
        } else if (c <= 2) {
            return R.string.wx_partly;
        } else if (c == 3) {
            return R.string.wx_overcast;
        } else if (c == 45 || c == 48) {
            return R.string.wx_fog;
        } else if (c >= 51 && c <= 57) {
            return R.string.wx_drizzle;
        } else if (c >= 61 && c <= 67 || c >= 80 && c <= 82) {
            return R.string.wx_rain;
        } else if (c >= 71 && c <= 77 || c == 85 || c == 86) {
            return R.string.wx_snow;
        }
        return R.string.wx_storm;
    }
}
