package com.itachi.voice;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * am broadcast -n com.itachi.voice/.SetupReceiver --es api_key KEY [--es model ID]
 *     [--es language en-IN] [--ez conversation false]
 * Protected by android.permission.DUMP in the manifest, so only the ADB shell can send it.
 */
public final class SetupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs prefs = new Prefs(c);
        StringBuilder done = new StringBuilder();
        String key = i.getStringExtra("api_key");
        if (key != null && !key.trim().isEmpty()) { prefs.save("api_key", key.trim()); done.append("key "); }
        String model = i.getStringExtra("model");
        if (model != null && !model.trim().isEmpty()) { prefs.save("model", model.trim()); done.append("model "); }
        String lang = i.getStringExtra("language");
        if (lang != null) { prefs.save("language", lang.trim()); done.append("language "); }
        if (i.hasExtra("conversation")) {
            prefs.save("conversation", i.getBooleanExtra("conversation", true));
            done.append("conversation ");
        }
        setResultCode(1);
        setResultData(done.length() == 0 ? "nothing to save" : "saved: " + done.toString().trim());
    }
}
