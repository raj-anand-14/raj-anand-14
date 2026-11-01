package com.itachi.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A small blocking client for Claude's Messages API that remembers the last few turns. */
final class ClaudeClient {
    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final int MAX_TURNS = 12; // messages kept (6 exchanges)

    private final List<JSONObject> history = new ArrayList<>();

    void forget() { history.clear(); }

    /** Call from a background thread. Returns Claude's text, or throws with a readable message. */
    String ask(String apiKey, String model, String system, String userText) throws IOException {
        try {
            JSONArray messages = new JSONArray();
            for (JSONObject m : history) messages.put(m);
            messages.put(message("user", userText));

            JSONObject body = new JSONObject()
                    .put("model", model)
                    .put("max_tokens", 600)
                    .put("system", system)
                    .put("messages", messages);

            HttpURLConnection http = (HttpURLConnection) new URL(ENDPOINT).openConnection();
            http.setConnectTimeout(10_000);
            http.setReadTimeout(45_000);
            http.setRequestMethod("POST");
            http.setDoOutput(true);
            http.setRequestProperty("content-type", "application/json");
            http.setRequestProperty("x-api-key", apiKey);
            http.setRequestProperty("anthropic-version", "2023-06-01");
            try (OutputStream out = http.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }

            int code = http.getResponseCode();
            String raw = read(code >= 400 ? http.getErrorStream() : http.getInputStream());
            http.disconnect();
            if (code >= 400) throw new IOException(explain(code, raw));

            JSONArray content = new JSONObject(raw).getJSONArray("content");
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                JSONObject block = content.getJSONObject(i);
                if ("text".equals(block.optString("type"))) text.append(block.optString("text"));
            }
            String reply = text.toString().trim();
            if (reply.isEmpty()) throw new IOException("Claude sent an empty answer.");

            history.add(message("user", userText));
            history.add(message("assistant", reply));
            while (history.size() > MAX_TURNS) { history.remove(0); history.remove(0); }
            return reply;
        } catch (JSONException e) {
            throw new IOException("Could not read Claude's answer.", e);
        }
    }

    private static JSONObject message(String role, String text) throws JSONException {
        return new JSONObject().put("role", role).put("content", text);
    }

    private static String explain(int code, String raw) {
        String detail = "";
        try { detail = new JSONObject(raw).getJSONObject("error").optString("message"); }
        catch (JSONException ignored) { }
        switch (code) {
            case 401: return "The Claude API key was refused. Run set-claude-key.bat again.";
            case 403: return "This API key may not use that model. " + detail;
            case 404: return "Model not found. " + detail;
            case 429: return "Too many requests or no credit left. Try again shortly.";
            case 529: return "Claude is busy right now. Try again in a moment.";
            default: return "Claude error " + code + (detail.isEmpty() ? "" : ": " + detail);
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream s = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = s.read(chunk)) > 0) buf.write(chunk, 0, n);
            return buf.toString("UTF-8");
        }
    }
}
