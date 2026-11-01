package com.itachi.voice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Things the TV does by itself, instantly and offline, before anything goes to Claude. */
final class Commands {
    private static final Pattern OPEN =
            Pattern.compile("^(?:please\\s+)?(?:open|launch|start|play|run|go to)\\s+(?:the\\s+)?(.+?)(?:\\s+app)?$");
    static final Pattern CLAUDE_OPEN = Pattern.compile("\\[\\[open:([^\\]]+)\\]\\]");

    private final Context context;
    private final Map<String, String> apps = new LinkedHashMap<>(); // label -> package

    Commands(Context c) {
        context = c;
        PackageManager pm = c.getPackageManager();
        for (String category : new String[] { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER }) {
            Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
            for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(c.getPackageName())) continue;
                String label = String.valueOf(r.loadLabel(pm)).trim();
                if (!label.isEmpty() && !apps.containsKey(label)) apps.put(label, pkg);
            }
        }
    }

    List<String> appLabels() { return new ArrayList<>(apps.keySet()); }

    /** A spoken answer if handled here, or null to ask Claude. */
    String handle(String said) {
        String s = said.toLowerCase(Locale.ROOT).replaceAll("[.!?,]", "").trim();
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (s.matches(".*\\b(volume up|louder|increase (the )?volume)\\b.*")) {
            for (int i = 0; i < 3; i++) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, i == 2 ? AudioManager.FLAG_SHOW_UI : 0);
            return "Volume up.";
        }
        if (s.matches(".*\\b(volume down|quieter|softer|decrease (the )?volume|lower (the )?volume)\\b.*")) {
            for (int i = 0; i < 3; i++) audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, i == 2 ? AudioManager.FLAG_SHOW_UI : 0);
            return "Volume down.";
        }
        if (s.matches("^(unmute|sound on)$")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
            return "Sound on.";
        }
        if (s.matches("^(mute|silence|sound off)$")) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
            return "";
        }
        Matcher m = OPEN.matcher(s);
        if (m.matches()) {
            String label = find(m.group(1));
            if (label != null) return open(label) ? "Opening " + label + "." : null;
        }
        return null;
    }

    /** Best installed-app match for a spoken or Claude-given name, or null. */
    String find(String name) {
        String want = squash(name);
        if (want.isEmpty()) return null;
        for (String label : apps.keySet()) if (squash(label).equals(want)) return label;
        for (String label : apps.keySet()) if (squash(label).startsWith(want)) return label;
        for (String label : apps.keySet()) {
            String l = squash(label);
            if (l.contains(want) || (l.length() >= 4 && want.contains(l))) return label;
        }
        return null;
    }

    boolean open(String label) {
        String pkg = apps.get(label);
        if (pkg == null) return false;
        PackageManager pm = context.getPackageManager();
        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
        if (launch == null) return false;
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(launch);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String squash(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
