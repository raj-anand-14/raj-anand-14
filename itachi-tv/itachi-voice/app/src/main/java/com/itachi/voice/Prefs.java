package com.itachi.voice;

import android.content.Context;
import android.content.SharedPreferences;

/** Settings kept on the TV only. The API key arrives from the PC through SetupReceiver. */
final class Prefs {
    static final String DEFAULT_MODEL = "claude-haiku-5-5";

    private final SharedPreferences p;

    Prefs(Context c) { p = c.getSharedPreferences("voice", Context.MODE_PRIVATE); }

    String apiKey() { return p.getString("api_key", "").trim(); }
    String model() { return p.getString("model", DEFAULT_MODEL); }
    /** Listen again after each spoken reply, until you go quiet. */
    boolean conversation() { return p.getBoolean("conversation", true); }
    /** The language it listens for first: en-IN or ta-IN. The other one is accepted too. */
    String language() {
        String l = p.getString("language", "");
        return "ta-IN".equals(l) ? "ta-IN" : "en-IN";
    }

    void save(String key, String value) { p.edit().putString(key, value).apply(); }
    void save(String key, boolean value) { p.edit().putBoolean(key, value).apply(); }
}
