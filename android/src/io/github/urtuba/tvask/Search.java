package io.github.urtuba.tvask;

import org.json.JSONException;

import java.io.IOException;

// Search is a web search engine. It returns plain text for the model.
interface Search {
    String search(String query) throws IOException, JSONException;
}
