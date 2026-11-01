package com.itachi.voice;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;

/** Press OK, speak, and Claude answers out loud. */
public final class VoiceActivity extends Activity implements RecognitionListener {

    private static final int REQ_MIC = 1;
    private static final int REQ_SYSTEM_VOICE = 2;

    private enum State { IDLE, LISTENING, THINKING, SPEAKING }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final ClaudeClient claude = new ClaudeClient();

    private Prefs prefs;
    private Commands commands;
    private SharinganView eye;
    private TextView status, heard, reply;
    private ScrollView scroll;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady;
    private State state = State.IDLE;
    private boolean heardSomething;
    private String pendingOpen;     // app Claude asked to open, after it finishes speaking
    private int request;            // drops answers that arrive after a cancel

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        setContentView(R.layout.activity_voice);
        eye = findViewById(R.id.eye);
        status = findViewById(R.id.status);
        heard = findViewById(R.id.heard);
        reply = findViewById(R.id.reply);
        scroll = findViewById(R.id.scroll);

        prefs = new Prefs(this);
        updateHint();
        commands = new Commands(this);

        tts = new TextToSpeech(this, ok -> {
            ttsReady = ok == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(ENGLISH);
                tts.setSpeechRate(1.02f);
                tts.setPitch(0.88f);
            }
        });
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { main.post(() -> spokenDone()); }
            @Override public void onError(String id) { main.post(() -> spokenDone()); }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        }

        if (prefs.apiKey().isEmpty()) {
            show(State.IDLE, "Setup needed");
            reply.setText("Add your Claude API key: on your PC, double-click\nwork\\itachi-voice\\set-claude-key.bat");
        } else {
            show(State.IDLE, "Press OK to speak");
            main.postDelayed(this::listen, 450); // opening the app is the wake word
        }
    }

    // ---- Remote keys ----

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
            case KeyEvent.KEYCODE_SEARCH:
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                if (e.getRepeatCount() == 0) toggle();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (e.getRepeatCount() == 0) switchLanguage();
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                scroll.smoothScrollBy(0, -scroll.getHeight() / 2);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                scroll.smoothScrollBy(0, scroll.getHeight() / 2);
                return true;
            case KeyEvent.KEYCODE_MENU:
                claude.forget();
                show(State.IDLE, "New conversation");
                heard.setText("");
                reply.setText("");
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override
    public void onBackPressed() {
        if (state == State.IDLE) { super.onBackPressed(); return; }
        stopAll();
        show(State.IDLE, "Press OK to speak");
    }

    private void toggle() {
        switch (state) {
            case LISTENING: if (recognizer != null) recognizer.stopListening(); break;
            case SPEAKING: tts.stop(); pendingOpen = null; listen(); break;
            case THINKING: stopAll(); listen(); break;
            default: listen();
        }
    }

    // ---- Listening ----

    private void listen() {
        if (isFinishing()) return;
        if (prefs.apiKey().isEmpty()) { show(State.IDLE, "Setup needed"); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQ_MIC);
            return;
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        // Listen for the chosen language first and accept the other: Tamil and English, mixed freely.
        String first = prefs.language(), other = first.equals("ta-IN") ? "en-IN" : "ta-IN";
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, first);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, first);
        i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", new String[] { other });

        if (recognizer == null) { systemVoice(i); return; }
        heardSomething = false;
        heard.setText("");
        show(State.LISTENING, "Listening…");
        try {
            recognizer.startListening(i);
        } catch (RuntimeException e) {
            systemVoice(i);
        }
    }

    /** Fallback: Google's own voice screen, when the recognizer cannot run inside this app. */
    private void systemVoice(Intent i) {
        try {
            show(State.LISTENING, "Listening…");
            startActivityForResult(new Intent(i).putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Claude"), REQ_SYSTEM_VOICE);
        } catch (ActivityNotFoundException e) {
            show(State.IDLE, "No speech recognizer on this TV");
            eye.setMode(SharinganView.Mode.ERROR);
        }
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != REQ_SYSTEM_VOICE) return;
        ArrayList<String> said = data == null ? null : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (result == RESULT_OK && said != null && !said.isEmpty()) understood(said.get(0));
        else show(State.IDLE, "Press OK to speak");
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req == REQ_MIC && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) listen();
        else if (req == REQ_MIC) show(State.IDLE, "Microphone permission needed");
    }

    @Override public void onReadyForSpeech(Bundle b) { show(State.LISTENING, "Listening…"); }
    @Override public void onBeginningOfSpeech() { heardSomething = true; }
    @Override public void onRmsChanged(float rms) { eye.setRms(rms); }
    @Override public void onBufferReceived(byte[] b) { }
    @Override public void onEndOfSpeech() { show(State.THINKING, "…"); }
    @Override public void onEvent(int type, Bundle b) { }

    @Override
    public void onPartialResults(Bundle b) {
        String text = first(b);
        if (!TextUtils.isEmpty(text)) heard.setText("“" + text + "”");
    }

    @Override
    public void onResults(Bundle b) {
        String text = first(b);
        if (TextUtils.isEmpty(text)) onError(SpeechRecognizer.ERROR_NO_MATCH);
        else understood(text);
    }

    @Override
    public void onError(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                show(State.IDLE, heardSomething ? "Didn't catch that. Press OK" : "Press OK to speak");
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, REQ_MIC);
                break;
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                show(State.IDLE, "No internet for speech");
                eye.setMode(SharinganView.Mode.ERROR);
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
            case SpeechRecognizer.ERROR_CLIENT:
            default:
                // The TV's recognizer would not run for us: use its own voice screen instead.
                if (recognizer != null) { recognizer.destroy(); recognizer = null; listen(); }
                else show(State.IDLE, "Press OK to speak");
        }
    }

    private static String first(Bundle b) {
        ArrayList<String> r = b == null ? null : b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return r == null || r.isEmpty() ? null : r.get(0);
    }

    // ---- Answering ----

    private void understood(String text) {
        heard.setText("“" + text + "”");
        String local = commands.handle(text);
        if (local != null) {
            reply.setText(local);
            if (local.isEmpty() || local.startsWith("Opening")) { show(State.IDLE, "Done"); if (local.startsWith("Opening")) finish(); }
            else say(local, false);
            return;
        }
        show(State.THINKING, "Thinking…");
        reply.setText("");
        final int id = ++request;
        final String key = prefs.apiKey(), model = prefs.model(), system = systemPrompt();
        net.execute(() -> {
            String answer;
            String error = null;
            try {
                answer = claude.ask(key, model, system, text);
            } catch (IOException e) {
                answer = null;
                error = e.getMessage() == null ? "Could not reach Claude. Check the TV's internet." : e.getMessage();
                if (e instanceof java.net.UnknownHostException || e instanceof java.net.SocketTimeoutException)
                    error = "Could not reach Claude. Check the TV's internet.";
            }
            final String a = answer, err = error;
            main.post(() -> {
                if (id != request || isFinishing()) return;
                if (a == null) {
                    show(State.IDLE, "Something went wrong");
                    eye.setMode(SharinganView.Mode.ERROR);
                    reply.setText(err);
                    return;
                }
                answered(a);
            });
        });
    }

    private void answered(String raw) {
        pendingOpen = null;
        Matcher m = Commands.CLAUDE_OPEN.matcher(raw);
        if (m.find()) pendingOpen = commands.find(m.group(1).trim());
        String spoken = Commands.CLAUDE_OPEN.matcher(raw).replaceAll("")
                .replaceAll("[*_#`>]", "")
                .replaceAll("\\s+\n", "\n")
                .trim();
        reply.setText(spoken);
        scroll.scrollTo(0, 0);
        if (spoken.isEmpty() && pendingOpen != null) { spokenDone(); return; }
        say(spoken, prefs.conversation() && pendingOpen == null);
    }

    private boolean listenAfter;

    private void say(String text, boolean thenListen) {
        listenAfter = thenListen;
        if (!ttsReady || text.isEmpty()) { spokenDone(); return; }
        // Tamil script is read with the Tamil voice, everything else with the Indian English voice.
        Locale voice = isTamil(text) ? TAMIL : ENGLISH;
        if (voice == TAMIL && tts.isLanguageAvailable(TAMIL) < TextToSpeech.LANG_AVAILABLE) {
            status.setText("Tamil voice not installed");
            offerTamilVoice();
            spokenDone();
            return;
        }
        tts.setLanguage(voice);
        show(State.SPEAKING, "");
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply");
    }

    private void spokenDone() {
        if (pendingOpen != null) {
            String app = pendingOpen;
            pendingOpen = null;
            if (commands.open(app)) { finish(); return; }
        }
        if (listenAfter && !isFinishing()) { listenAfter = false; listen(); }
        else show(State.IDLE, "Press OK to speak");
    }

    private String systemPrompt() {
        return "You are Itachi Voice, the voice assistant on the user's Android TV. "
                + "Your answers are read aloud by text-to-speech, so: answer in one to three short sentences unless the user asks for more; "
                + "use plain spoken language with no markdown, lists, emojis, URLs or code. "
                + "Your manner is calm, quiet and wise, with a touch of mystery; never cold or rude. "
                + "The user speaks Tamil, English or a mix of both. When they speak Tamil (even mixed with English), "
                + "reply in Tamil written in Tamil script, keeping app names and English words they used in English; "
                + "when they speak English, reply in English. Never write Tamil in Latin letters, because the Tamil voice "
                + "cannot read it. "
                + "The apps installed on this TV are: " + TextUtils.join(", ", commands.appLabels()) + ". "
                + "When the user wants to watch, play or open something in one of these apps, say one short sentence "
                + "and end your reply with [[open:EXACT APP NAME]] using a name from that list. "
                + "You cannot control playback inside apps, search inside them, or see the screen; say so briefly if asked.";
    }

    // ---- State and lifecycle ----

    private void show(State s, String text) {
        state = s;
        status.setText(text);
        switch (s) {
            case LISTENING: eye.setMode(SharinganView.Mode.LISTENING); break;
            case THINKING: eye.setMode(SharinganView.Mode.THINKING); break;
            case SPEAKING: eye.setMode(SharinganView.Mode.SPEAKING); break;
            default: eye.setMode(SharinganView.Mode.IDLE);
        }
    }

    private static final Locale ENGLISH = new Locale("en", "IN");
    private static final Locale TAMIL = new Locale("ta", "IN");
    private boolean offeredVoice;

    static boolean isTamil(String text) {
        int tamil = 0, latin = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u0B80' && ch <= '\u0BFF') tamil++;
            else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) latin++;
        }
        return tamil > 0 && tamil >= latin / 3;
    }

    /** Left or Right on the remote: which language it listens for first. */
    private void switchLanguage() {
        String next = prefs.language().equals("ta-IN") ? "en-IN" : "ta-IN";
        prefs.save("language", next);
        boolean wasListening = state == State.LISTENING;
        if (wasListening && recognizer != null) recognizer.cancel();
        show(state == State.LISTENING ? State.IDLE : state, next.equals("ta-IN") ? "தமிழ்  ·  Tamil first" : "English first");
        updateHint();
        if (wasListening) main.postDelayed(this::listen, 250);
    }

    private void updateHint() {
        TextView hint = findViewById(R.id.hint);
        String lang = prefs.language().equals("ta-IN") ? "தமிழ்" : "English";
        hint.setText("OK  speak   ·   ◀ ▶  " + lang + "   ·   ▲ ▼  scroll   ·   BACK  close");
    }

    /** Opens Android's voice-data installer once, so the Tamil voice can be downloaded. */
    private void offerTamilVoice() {
        if (offeredVoice) return;
        offeredVoice = true;
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (ActivityNotFoundException ignored) { }
    }

    private Locale language() {
        String tag = prefs.language();
        return tag.isEmpty() ? Locale.getDefault() : Locale.forLanguageTag(tag);
    }

    private void stopAll() {
        request++;
        pendingOpen = null;
        listenAfter = false;
        if (recognizer != null) recognizer.cancel();
        if (tts != null) tts.stop();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAll();
        if (state != State.IDLE) show(State.IDLE, "Press OK to speak");
    }

    @Override
    protected void onDestroy() {
        stopAll();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) tts.shutdown();
        net.shutdownNow();
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
