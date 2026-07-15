package com.psalmsanalysis.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int GOOGLE_SIGN_IN_REQUEST = 4001;
    private static final String DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata";
    private static final int BG_APP = Color.rgb(247, 241, 230);
    private static final int BG_PANEL = Color.rgb(255, 252, 245);
    private static final int TEXT_MAIN = Color.rgb(37, 31, 24);
    private static final int TEXT_MUTED = Color.rgb(100, 89, 75);
    private static final int HIGHLIGHT = Color.rgb(255, 239, 146);
    private static final int NOTE_HIGHLIGHT = Color.rgb(255, 184, 107);
    private static final int FEATURE_INK = Color.rgb(82, 70, 173);

    private final List<Feature> features = new ArrayList<>();
    private List<List<Verse>> psalms;
    private AnnotationStore annotationStore;
    private PsalmTextView psalmTextView;
    private TextView titleView;
    private TextView featureDescriptionView;
    private Spinner chapterSpinner;
    private Spinner featureSpinner;
    private ScrollView scrollView;
    private Button accountButton;
    private SwipeRefreshLayout swipeRefreshLayout;
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private long syncIntervalMs;
    private final Runnable autoSyncRunnable = new Runnable() {
        @Override public void run() {
            syncFromGoogle(false);
            syncHandler.postDelayed(this, syncIntervalMs);
        }
    };

    private int currentPsalmIndex = 0;
    private int currentFeatureIndex = 0;
    private boolean changingChapterFromCode = false;
    private GoogleSync googleSync;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        features.addAll(createFeatures());
        annotationStore = new AnnotationStore(this);
        googleSync = new GoogleSync(this, annotationStore);
        syncIntervalMs = getSharedPreferences("psalm_settings", MODE_PRIVATE).getLong("sync_interval_ms", 30000L);
        GoogleSignInAccount previousAccount = GoogleSignIn.getLastSignedInAccount(this);
        if (previousAccount != null) googleSync.setAccount(previousAccount);
        psalms = PsalmRepository.load(this);
        buildUi();
        showPsalm(0);
        googleSync.syncInBackground(this::renderPsalm);
        startAutoSync();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG_APP);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(main, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        titleView = new TextView(this);
        titleView.setTextColor(TEXT_MAIN);
        titleView.setTextSize(24);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setGravity(Gravity.CENTER);
        main.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(0, dp(8), 0, dp(8));
        main.addView(controls);

        chapterSpinner = new Spinner(this);
        ArrayAdapter<String> chapterAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                createChapterLabels());
        chapterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chapterSpinner.setAdapter(chapterAdapter);
        controls.addView(chapterSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        featureSpinner = new Spinner(this);
        ArrayAdapter<String> featureAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                createFeatureLabels());
        featureAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        featureSpinner.setAdapter(featureAdapter);
        LinearLayout.LayoutParams featureSpinnerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        featureSpinnerParams.topMargin = dp(6);
        controls.addView(featureSpinner, featureSpinnerParams);

        featureDescriptionView = new TextView(this);
        featureDescriptionView.setTextColor(TEXT_MUTED);
        featureDescriptionView.setTextSize(14);
        featureDescriptionView.setLineSpacing(dp(2), 1.0f);
        featureDescriptionView.setPadding(dp(4), dp(6), dp(4), 0);
        controls.addView(featureDescriptionView);

        accountButton = new Button(this);
        accountButton.setText("Sign in to sync with Google Drive");
        accountButton.setAllCaps(false);
        accountButton.setOnClickListener(v -> startGoogleSignIn());
        controls.addView(accountButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        updateAccountButton();

        Button settingsButton = new Button(this);
        settingsButton.setText("Sync settings");
        settingsButton.setAllCaps(false);
        settingsButton.setOnClickListener(v -> showSyncSettingsDialog());
        controls.addView(settingsButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        psalmTextView = new PsalmTextView(this);
        psalmTextView.setTextColor(TEXT_MAIN);
        psalmTextView.setTextSize(21);
        psalmTextView.setLineSpacing(dp(8), 1.02f);
        psalmTextView.setPadding(dp(36), dp(18), dp(36), dp(30));
        psalmTextView.setBackgroundColor(BG_PANEL);
        psalmTextView.setAnnotationListener(new PsalmTextView.AnnotationListener() {
            @Override
            public void onToggleHighlight(WordRange word) {
                toggleHighlight(word);
            }

            @Override
            public void onEditNote(WordRange word) {
                showNoteDialog(word);
            }
        });
        scrollView.addView(psalmTextView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setOnRefreshListener(() -> syncFromGoogle(true));
        swipeRefreshLayout.addView(scrollView, new SwipeRefreshLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        main.addView(swipeRefreshLayout, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        Button previous = edgeButton("<");
        FrameLayout.LayoutParams previousParams = new FrameLayout.LayoutParams(
                dp(44),
                dp(96),
                Gravity.START | Gravity.CENTER_VERTICAL);
        root.addView(previous, previousParams);
        previous.setOnClickListener(v -> movePsalm(-1));

        Button next = edgeButton(">");
        FrameLayout.LayoutParams nextParams = new FrameLayout.LayoutParams(
                dp(44),
                dp(96),
                Gravity.END | Gravity.CENTER_VERTICAL);
        root.addView(next, nextParams);
        next.setOnClickListener(v -> movePsalm(1));

        chapterSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!changingChapterFromCode && position != currentPsalmIndex) {
                    showPsalm(position);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        featureSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFeatureIndex = position;
                featureDescriptionView.setText(features.get(position).description);
                renderPsalm();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        setContentView(root);
    }

    private Button edgeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(26);
        button.setTextColor(Color.WHITE);
        button.setBackgroundColor(Color.rgb(96, 82, 190));
        button.setAlpha(0.88f);
        button.setAllCaps(false);
        return button;
    }

    private void movePsalm(int delta) {
        int next = currentPsalmIndex + delta;
        if (next < 0 || next >= psalms.size()) {
            Toast.makeText(this, next < 0 ? "Psalm 1" : "Psalm 150", Toast.LENGTH_SHORT).show();
            return;
        }
        showPsalm(next);
    }

    private void showPsalm(int index) {
        currentPsalmIndex = index;
        titleView.setText(String.format(Locale.US, "Psalm %d", index + 1));
        changingChapterFromCode = true;
        chapterSpinner.setSelection(index);
        changingChapterFromCode = false;
        renderPsalm();
        scrollView.post(() -> scrollView.scrollTo(0, 0));
    }

    private void renderPsalm() {
        if (psalms == null || psalms.isEmpty() || psalmTextView == null) {
            return;
        }
        List<Verse> verses = psalms.get(currentPsalmIndex);
        String featureId = features.get(currentFeatureIndex).id;
        SpannableStringBuilder builder = new SpannableStringBuilder();
        List<WordRange> words = new ArrayList<>();

        for (Verse verse : verses) {
            int verseStart = builder.length();
            String verseNumber = verse.number + " ";
            builder.append(verseNumber);
            builder.setSpan(new StyleSpan(Typeface.BOLD), verseStart, verseStart + verseNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new ForegroundColorSpan(FEATURE_INK), verseStart, verseStart + verseNumber.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            int wordIndex = 0;
            int cursor = 0;
            while (cursor < verse.text.length()) {
                char c = verse.text.charAt(cursor);
                if (isWordChar(c)) {
                    int startInVerse = cursor;
                    while (cursor < verse.text.length() && isWordChar(verse.text.charAt(cursor))) {
                        cursor++;
                    }
                    int start = builder.length();
                    builder.append(verse.text, startInVerse, cursor);
                    int end = builder.length();
                    WordRange range = new WordRange(currentPsalmIndex + 1, verse.number, wordIndex, start, end);
                    words.add(range);
                    String key = range.key(featureId);
                    if (annotationStore.hasNote(key)) {
                        builder.setSpan(new BackgroundColorSpan(NOTE_HIGHLIGHT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    } else if (annotationStore.isHighlighted(key)) {
                        builder.setSpan(new BackgroundColorSpan(HIGHLIGHT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    wordIndex++;
                } else {
                    builder.append(c);
                    cursor++;
                }
            }
            builder.append("\n\n");
        }

        psalmTextView.setPsalmText(builder, words);
    }

    private void toggleHighlight(WordRange word) {
        String key = word.key(features.get(currentFeatureIndex).id);
        if (annotationStore.toggleHighlight(key)) {
            renderPsalm();
            googleSync.syncInBackground(this::renderPsalm);
        }
    }

    private void showNoteDialog(WordRange word) {
        String key = word.key(features.get(currentFeatureIndex).id);
        EditText editText = new EditText(this);
        editText.setMinLines(4);
        editText.setGravity(Gravity.TOP | Gravity.START);
        editText.setText(annotationStore.getNote(key));
        editText.setSelection(editText.getText().length());
        editText.setHint("Write your note for this word");
        editText.setPadding(dp(14), dp(10), dp(14), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("Psalm " + word.psalm + ":" + word.verse + " note")
                .setView(editText)
                .setPositiveButton("Save", (dialog, which) -> {
                    annotationStore.saveNote(key, editText.getText().toString());
                    renderPsalm();
                    googleSync.syncInBackground(this::renderPsalm);
                })
                .setNeutralButton("Delete", (dialog, which) -> {
                    annotationStore.deleteNote(key);
                    renderPsalm();
                    googleSync.syncInBackground(this::renderPsalm);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startGoogleSignIn() {
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(new Scope(DRIVE_APPDATA_SCOPE))
                .requestEmail()
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(this, options);
        startActivityForResult(client.getSignInIntent(), GOOGLE_SIGN_IN_REQUEST);
    }

    private void syncFromGoogle(boolean userRequested) {
        if (!googleSync.hasAccount()) {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            if (userRequested) Toast.makeText(this, "Sign in to sync with Google Drive", Toast.LENGTH_SHORT).show();
            return;
        }
        googleSync.syncInBackground(() -> {
            renderPsalm();
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        });
    }

    private void startAutoSync() {
        syncHandler.removeCallbacks(autoSyncRunnable);
        syncHandler.postDelayed(autoSyncRunnable, syncIntervalMs);
    }

    private void showSyncSettingsDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(syncIntervalMs / 1000L));
        input.setSelectAllOnFocus(true);
        input.setHint("Seconds");
        new AlertDialog.Builder(this)
                .setTitle("Automatic sync interval")
                .setMessage("Choose how often to sync with Google Drive (5-3600 seconds).")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    try {
                        long seconds = Math.max(5L, Math.min(3600L, Long.parseLong(input.getText().toString())));
                        syncIntervalMs = seconds * 1000L;
                        getSharedPreferences("psalm_settings", MODE_PRIVATE).edit().putLong("sync_interval_ms", syncIntervalMs).apply();
                        startAutoSync();
                        syncFromGoogle(false);
                    } catch (NumberFormatException ignored) {
                        Toast.makeText(this, "Enter a number of seconds", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != GOOGLE_SIGN_IN_REQUEST) return;
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            googleSync.setAccount(account);
            updateAccountButton();
            Toast.makeText(this, "Google sync connected", Toast.LENGTH_SHORT).show();
            googleSync.syncInBackground(this::renderPsalm);
        } catch (ApiException e) {
            Toast.makeText(this, "Google sign-in failed: " + e.getStatusCode(), Toast.LENGTH_LONG).show();
        }
    }

    private void updateAccountButton() {
        if (accountButton == null) return;
        accountButton.setText(GoogleSignIn.getLastSignedInAccount(this) != null
                ? "Already signed in to Google"
                : "Sign in to sync with Google Drive");
    }

    @Override protected void onDestroy() {
        syncHandler.removeCallbacks(autoSyncRunnable);
        super.onDestroy();
    }

    private List<String> createChapterLabels() {
        List<String> labels = new ArrayList<>();
        for (int i = 1; i <= 150; i++) {
            labels.add("Psalm " + i);
        }
        return labels;
    }

    private List<String> createFeatureLabels() {
        List<String> labels = new ArrayList<>();
        for (Feature feature : features) {
            labels.add(feature.name + " - " + feature.shortDescription);
        }
        return labels;
    }

    private List<Feature> createFeatures() {
        List<Feature> list = new ArrayList<>();
        list.add(new Feature("parallelism", "Parallelism", "Lines repeat, contrast, or expand an idea.",
                "Parallelism - Lines relate to one another by repeating, contrasting, or expanding an idea.\nExample: \"The heavens declare the glory of God; the skies proclaim the work of his hands.\" (Psalm 19:1)"));
        list.add(new Feature("imagery", "Imagery", "Vivid metaphors and pictures communicate spiritual truths.",
                "Imagery - Vivid metaphors and pictures communicate spiritual truths.\nExample: \"The LORD is my shepherd.\" (Psalm 23:1)"));
        list.add(new Feature("concrete_language", "Concrete Language", "Physical images express abstract emotions or realities.",
                "Concrete Language - Physical images express abstract emotions or realities.\nExample: \"He set my feet upon a rock.\" (Psalm 40:2)\nA picture of stability and security rather than simply saying, \"God made me secure.\""));
        list.add(new Feature("merism", "Merism", "Two opposite extremes represent the whole.",
                "Merism - Two opposite extremes represent the whole.\nExample: \"The LORD will keep your going out and your coming in.\" (Psalm 121:8)\nMeaning: God watches over every part of your life."));
        list.add(new Feature("chiasm", "Chiasm", "A mirrored A-B-C-B'-A' structure emphasizes the center or a reversal.",
                "Chiasm - A mirrored A-B-C-B'-A' structure emphasizes the center or a reversal.\nExample: \"Whoever exalts himself will be humbled, and whoever humbles himself will be exalted.\" (Matthew 23:12)\nAlthough from the New Testament, it reflects a classic Hebrew literary pattern. Many psalms also use chiastic structures across multiple verses."));
        list.add(new Feature("inclusio", "Inclusio (Bookends)", "A repeated phrase or theme frames the poem.",
                "Inclusio (Bookends) - A repeated phrase or theme frames the poem.\nExample: Psalm 8 begins and ends: \"O LORD, our Lord, how majestic is your name in all the earth!\""));
        list.add(new Feature("acrostics", "Acrostics", "Verses or sections follow the Hebrew alphabet.",
                "Acrostics - Verses or sections follow the Hebrew alphabet.\nExample: Psalm 119 has 22 sections, one for each Hebrew letter, with every verse in a section beginning with the same letter."));
        list.add(new Feature("repetition", "Repetition", "Words, phrases, or refrains are intentionally repeated for emphasis.",
                "Repetition - Words, phrases, or refrains are intentionally repeated for emphasis.\nExample: \"For his steadfast love endures forever.\" Repeated in every verse of Psalm 136."));
        list.add(new Feature("emotional_movement", "Emotional Movement", "The poem progresses emotionally, often from lament to trust to praise.",
                "Emotional Movement - The poem progresses emotionally, often from lament to trust to praise.\nExample: Psalm 13 begins: \"How long, O LORD?\" and ends: \"I will sing to the LORD, because he has dealt bountifully with me.\""));
        list.add(new Feature("symbolic_numbers", "Symbolic Numbers", "Certain numbers carry literary or theological significance.",
                "Symbolic Numbers - Certain numbers carry literary or theological significance.\nExample: Psalm 119 contains 22 sections, matching the Hebrew alphabet, each with 8 verses, emphasizing the completeness and fullness of God's instruction."));
        list.add(new Feature("sound_patterns", "Sound Patterns", "Alliteration, assonance, wordplay, and puns enrich the Hebrew text.",
                "Sound Patterns - Alliteration, assonance, wordplay, and puns enrich the Hebrew text.\nExample: In Genesis 2-3, the Hebrew words 'adam (man) and 'adamah (ground) echo each other, reinforcing humanity's origin from the earth. This kind of wordplay is common in Hebrew poetry, though it is usually lost in translation."));
        return list;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '\'' || c == 8217;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static final class Feature {
        final String id;
        final String name;
        final String shortDescription;
        final String description;

        Feature(String id, String name, String shortDescription, String description) {
            this.id = id;
            this.name = name;
            this.shortDescription = shortDescription;
            this.description = description;
        }
    }

    private static final class Verse {
        final int number;
        final String text;

        Verse(int number, String text) {
            this.number = number;
            this.text = text;
        }
    }

    private static final class WordRange {
        final int psalm;
        final int verse;
        final int wordIndex;
        final int start;
        final int end;

        WordRange(int psalm, int verse, int wordIndex, int start, int end) {
            this.psalm = psalm;
            this.verse = verse;
            this.wordIndex = wordIndex;
            this.start = start;
            this.end = end;
        }

        String key(String featureId) {
            return psalm + "|" + featureId + "|" + verse + "|" + wordIndex;
        }

        String gestureKey() {
            return psalm + "|" + verse + "|" + wordIndex;
        }

        boolean sameWord(WordRange other) {
            return other != null
                    && psalm == other.psalm
                    && verse == other.verse
                    && wordIndex == other.wordIndex;
        }
    }

    private static final class PsalmRepository {
        static List<List<Verse>> load(Context context) {
            try {
                InputStream input = context.getResources().openRawResource(R.raw.psalms_kjv);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                String json = output.toString(StandardCharsets.UTF_8.name());
                JSONObject root = new JSONObject(json);
                JSONArray chapters = root.getJSONArray("chapters");
                List<List<Verse>> psalms = new ArrayList<>();
                for (int i = 0; i < chapters.length(); i++) {
                    JSONObject chapter = chapters.getJSONObject(i);
                    JSONArray versesJson = chapter.getJSONArray("verses");
                    List<Verse> verses = new ArrayList<>();
                    for (int v = 0; v < versesJson.length(); v++) {
                        JSONObject verseJson = versesJson.getJSONObject(v);
                        verses.add(new Verse(
                                Integer.parseInt(verseJson.getString("verse")),
                                verseJson.getString("text")));
                    }
                    psalms.add(verses);
                }
                return psalms;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to load Psalms KJV resource", e);
            }
        }
    }

    private static final class AnnotationStore {
        private static final String PREFS = "psalm_annotations";
        private static final String HIGHLIGHTS = "highlights";
        private static final String NOTE_PREFIX = "note:";
        private final SharedPreferences preferences;

        AnnotationStore(Context context) {
            preferences = context.getSharedPreferences(PREFS, MODE_PRIVATE);
        }

        boolean isHighlighted(String key) {
            return preferences.getStringSet(HIGHLIGHTS, new HashSet<>()).contains(key);
        }

        boolean toggleHighlight(String key) {
            Set<String> highlights = new HashSet<>(preferences.getStringSet(HIGHLIGHTS, new HashSet<>()));
            if (highlights.contains(key)) {
                highlights.remove(key);
            } else {
                highlights.add(key);
            }
            preferences.edit().putStringSet(HIGHLIGHTS, highlights).apply();
            return true;
        }

        boolean hasNote(String key) {
            return !getNote(key).trim().isEmpty();
        }

        String getNote(String key) {
            return preferences.getString(NOTE_PREFIX + key, "");
        }

        void saveNote(String key, String note) {
            SharedPreferences.Editor editor = preferences.edit();
            if (note.trim().isEmpty()) {
                editor.remove(NOTE_PREFIX + key);
            } else {
                Set<String> highlights = new HashSet<>(preferences.getStringSet(HIGHLIGHTS, new HashSet<>()));
                highlights.add(key);
                editor.putStringSet(HIGHLIGHTS, highlights);
                editor.putString(NOTE_PREFIX + key, note);
            }
            editor.apply();
        }

        void deleteNote(String key) {
            preferences.edit().remove(NOTE_PREFIX + key).apply();
        }

        synchronized JSONObject toJson() {
            JSONObject result = new JSONObject();
            JSONArray highlights = new JSONArray();
            for (String key : preferences.getStringSet(HIGHLIGHTS, new HashSet<>())) highlights.put(key);
            JSONObject notes = new JSONObject();
            for (String key : preferences.getAll().keySet()) {
                if (key.startsWith(NOTE_PREFIX)) {
                    try { notes.put(key.substring(NOTE_PREFIX.length()), preferences.getString(key, "")); }
                    catch (Exception ignored) { }
                }
            }
            try {
                result.put("version", 1);
                result.put("highlights", highlights);
                result.put("notes", notes);
            } catch (Exception ignored) { }
            return result;
        }

        synchronized void mergeJson(JSONObject remote) {
            Set<String> highlights = new HashSet<>(preferences.getStringSet(HIGHLIGHTS, new HashSet<>()));
            JSONArray remoteHighlights = remote.optJSONArray("highlights");
            if (remoteHighlights != null) for (int i = 0; i < remoteHighlights.length(); i++) highlights.add(remoteHighlights.optString(i));
            SharedPreferences.Editor editor = preferences.edit().putStringSet(HIGHLIGHTS, highlights);
            JSONObject remoteNotes = remote.optJSONObject("notes");
            if (remoteNotes != null) {
                java.util.Iterator<String> keys = remoteNotes.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = remoteNotes.optString(key, "");
                    if (!value.trim().isEmpty() && !hasNote(key)) editor.putString(NOTE_PREFIX + key, value);
                }
            }
            editor.apply();
        }
    }

    private static final class GoogleSync {
        private static final String FILE_NAME = "psalms-analysis-annotations.json";
        private final Context context;
        private final AnnotationStore store;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private GoogleSignInAccount account;

        GoogleSync(Context context, AnnotationStore store) { this.context = context.getApplicationContext(); this.store = store; }
        void setAccount(GoogleSignInAccount account) { this.account = account; }
        boolean hasAccount() { return account != null; }
        void syncInBackground(Runnable onComplete) {
            if (account == null) return;
            executor.execute(() -> {
                try { DriveSyncClient.sync(context, account, store); }
                catch (Exception ignored) { }
                new Handler(Looper.getMainLooper()).post(onComplete);
            });
        }
    }

    private static final class DriveSyncClient {
        static void sync(Context context, GoogleSignInAccount account, AnnotationStore store) throws Exception {
            String token = com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account.getAccount(), "oauth2:" + DRIVE_APPDATA_SCOPE);
            String auth = "Bearer " + token;
            String query = java.net.URLEncoder.encode("name='" + GoogleSync.FILE_NAME + "' and 'appDataFolder' in parents and trashed=false", "UTF-8");
            java.net.HttpURLConnection list = request("https://www.googleapis.com/drive/v3/files?q=" + query + "&spaces=appDataFolder&fields=files(id)", "GET", auth);
            JSONObject files = new JSONObject(read(list));
            JSONArray rows = files.optJSONArray("files");
            JSONObject local = store.toJson();
            if (rows != null && rows.length() > 0) {
                String id = rows.getJSONObject(0).getString("id");
                java.net.HttpURLConnection get = request("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media", "GET", auth);
                store.mergeJson(new JSONObject(read(get)));
                java.net.HttpURLConnection update = request("https://www.googleapis.com/upload/drive/v3/files/" + id + "?uploadType=media", "PATCH", auth);
                write(update, store.toJson().toString());
                read(update);
            } else {
                java.net.HttpURLConnection create = request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart", "POST", auth);
                String boundary = "psalmsBoundary";
                create.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
                String metadata = "{\"name\":\"" + GoogleSync.FILE_NAME + "\",\"parents\":[\"appDataFolder\"]}";
                write(create, "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata + "\r\n--" + boundary + "\r\nContent-Type: application/json\r\n\r\n" + local + "\r\n--" + boundary + "--");
                read(create);
            }
            com.google.android.gms.auth.GoogleAuthUtil.clearToken(context, token);
        }
        private static java.net.HttpURLConnection request(String url, String method, String auth) throws Exception {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setRequestMethod(method); c.setRequestProperty("Authorization", auth); c.setDoInput(true); c.setDoOutput(!method.equals("GET")); return c;
        }
        private static String read(java.net.HttpURLConnection c) throws Exception { InputStream in = c.getResponseCode() < 400 ? c.getInputStream() : c.getErrorStream(); ByteArrayOutputStream o = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) o.write(b, 0, n); return o.toString(StandardCharsets.UTF_8.name()); }
        private static void write(java.net.HttpURLConnection c, String value) throws Exception { c.getOutputStream().write(value.getBytes(StandardCharsets.UTF_8)); }
    }

    public static final class PsalmTextView extends TextView {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final int doubleTapTimeout = ViewConfiguration.getDoubleTapTimeout();
        private final int longPressTimeout = ViewConfiguration.getLongPressTimeout();
        private List<WordRange> words = new ArrayList<>();
        private AnnotationListener annotationListener;
        private final Set<String> touchedWordKeys = new HashSet<>();
        private WordRange lastTapWord;
        private long lastTapUpTime;
        private boolean highlightingActive;
        private float lastTouchX;
        private float lastTouchY;

        private final Runnable longPressRunnable = new Runnable() {
            @Override
            public void run() {
                WordRange touched = wordAt(lastTouchX, lastTouchY);
                if (touched != null && annotationListener != null) {
                    highlightingActive = true;
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    highlightWord(touched);
                }
            }
        };

        public PsalmTextView(Context context) {
            super(context);
        }

        void setAnnotationListener(AnnotationListener annotationListener) {
            this.annotationListener = annotationListener;
        }

        void setPsalmText(SpannableStringBuilder text, List<WordRange> words) {
            this.words = words;
            setText(text);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            lastTouchX = event.getX();
            lastTouchY = event.getY();
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                WordRange touched = wordAt(lastTouchX, lastTouchY);
                if (isDoubleTap(touched, event.getEventTime())) {
                    handler.removeCallbacks(longPressRunnable);
                    clearGestureState();
                    if (annotationListener != null) {
                        annotationListener.onEditNote(touched);
                    }
                    return true;
                }
                clearGestureState();
                handler.postDelayed(longPressRunnable, longPressTimeout);
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (highlightingActive) {
                    WordRange touched = wordAt(lastTouchX, lastTouchY);
                    if (touched != null) {
                        highlightWord(touched);
                    }
                }
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                handler.removeCallbacks(longPressRunnable);
                getParent().requestDisallowInterceptTouchEvent(false);
                if (!highlightingActive) {
                    lastTapWord = wordAt(lastTouchX, lastTouchY);
                    lastTapUpTime = event.getEventTime();
                }
                clearGestureState();
                return true;
            }
            if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                handler.removeCallbacks(longPressRunnable);
                getParent().requestDisallowInterceptTouchEvent(false);
                clearGestureState();
                return true;
            }
            return true;
        }

        private void clearGestureState() {
            touchedWordKeys.clear();
            highlightingActive = false;
        }

        private boolean isDoubleTap(WordRange touched, long eventTime) {
            return touched != null
                    && touched.sameWord(lastTapWord)
                    && eventTime - lastTapUpTime <= doubleTapTimeout;
        }

        private void highlightWord(WordRange word) {
            if (!touchedWordKeys.add(word.gestureKey())) {
                return;
            }
            annotationListener.onToggleHighlight(word);
        }

        private WordRange wordAt(float eventX, float eventY) {
            Layout layout = getLayout();
            if (layout == null) {
                return null;
            }
            int x = (int) eventX - getTotalPaddingLeft() + getScrollX();
            int y = (int) eventY - getTotalPaddingTop() + getScrollY();
            if (y < 0 || y > layout.getHeight()) {
                return null;
            }
            int line = layout.getLineForVertical(y);
            int offset = layout.getOffsetForHorizontal(line, x);
            for (WordRange word : words) {
                if (offset >= word.start && offset <= word.end) {
                    return word;
                }
            }
            return null;
        }

        interface AnnotationListener {
            void onToggleHighlight(WordRange word);

            void onEditNote(WordRange word);
        }
    }
}
