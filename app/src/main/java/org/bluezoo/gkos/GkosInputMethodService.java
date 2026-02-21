/*
 * GkosInputMethodService.java
 * Copyright (C) 2026 Chris Burdess
 *
 * This file is part of GKOS2, a chorded keyboard for Android.
 * For more information please visit https://gkos.com/
 *
 * GKOS2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * GKOS2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with GKOS2.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.bluezoo.gkos;

import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.emoji2.emojipicker.EmojiPickerView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * GKOS Input Method Service.
 * Provides the chorded keyboard interface.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class GkosInputMethodService extends InputMethodService
        implements GkosKeyboardView.ChordOutputHandler, GkosKeyboardView.OutcomeProvider,
                   GkosKeyboardView.GlobeClickListener,
                   GkosKeyboardView.SuggestionTapListener {

    /** Unicode keyboard symbols for action names. */
    private static final Map<String, String> ACTION_SYMBOLS = new HashMap<>();
    static {
        // Common keys
        ACTION_SYMBOLS.put("space",         "\u2423");  // ␣  OPEN BOX
        ACTION_SYMBOLS.put("backspace",     "\u232B");  // ⌫  ERASE TO THE LEFT
        ACTION_SYMBOLS.put("delete",        "\u2326");  // ⌦  ERASE TO THE RIGHT
        ACTION_SYMBOLS.put("enter",         "\u23CE");  // ⏎  RETURN SYMBOL
        ACTION_SYMBOLS.put("tab",           "\u21E5");  // ⇥  RIGHTWARDS ARROW TO BAR
        ACTION_SYMBOLS.put("esc",           "\u238B");  // ⎋  BROKEN CIRCLE WITH NORTHWEST ARROW
        // Modifiers
        ACTION_SYMBOLS.put("shift",         "\u21E7");  // ⇧  UPWARDS WHITE ARROW
        ACTION_SYMBOLS.put("ctrl",          "\u2303");  // ⌃  UP ARROWHEAD
        ACTION_SYMBOLS.put("alt",           "\u2325");  // ⌥  OPTION KEY
        ACTION_SYMBOLS.put("mode_toggle",   "\u21C4");  // ⇄  RIGHTWARDS ARROW OVER LEFTWARDS
        ACTION_SYMBOLS.put("symb",          "@");
        // Arrows
        ACTION_SYMBOLS.put("UpArrow",       "\u2191");  // ↑
        ACTION_SYMBOLS.put("DownArrow",     "\u2193");  // ↓
        ACTION_SYMBOLS.put("LeftArrow",     "\u2190");  // ←
        ACTION_SYMBOLS.put("RightArrow",    "\u2192");  // →
        // Page / Home / End
        ACTION_SYMBOLS.put("PageUp",        "\u21DE");  // ⇞  UPWARDS ARROW WITH DOUBLE STROKE
        ACTION_SYMBOLS.put("PageDown",      "\u21DF");  // ⇟  DOWNWARDS ARROW WITH DOUBLE STROKE
        ACTION_SYMBOLS.put("Home",          "\u2196");  // ↖  NORTH WEST ARROW
        ACTION_SYMBOLS.put("End",           "\u2198");  // ↘  SOUTH EAST ARROW
        ACTION_SYMBOLS.put("Insert",        "Ins");
        // Scroll / Pan
        ACTION_SYMBOLS.put("ScrollUp",      "\u21C8");  // ⇈  UPWARDS PAIRED ARROWS
        ACTION_SYMBOLS.put("ScrollDown",    "\u21CA");  // ⇊  DOWNWARDS PAIRED ARROWS
        ACTION_SYMBOLS.put("PanLeft",       "\u21D0");  // ⇐  LEFTWARDS DOUBLE ARROW
        ACTION_SYMBOLS.put("PanRight",      "\u21D2");  // ⇒  RIGHTWARDS DOUBLE ARROW
        // Word movement
        ACTION_SYMBOLS.put("WordLeft",      "\u21E0");  // ⇠  LEFTWARDS DASHED ARROW
        ACTION_SYMBOLS.put("WordRight",     "\u21E2");  // ⇢  RIGHTWARDS DASHED ARROW
        // Pan to boundary
        ACTION_SYMBOLS.put("PanLeftHome",   "\u21E4");  // ⇤  LEFTWARDS ARROW TO BAR
        ACTION_SYMBOLS.put("PanRightEnd",   "\u21A6");  // ↦  RIGHTWARDS ARROW FROM BAR
        // Special panes
        ACTION_SYMBOLS.put("emoji",         "\uD83D\uDE00");  // 😀
        ACTION_SYMBOLS.put("unicode_picker", "U+");
    }

    private FrameLayout container;
    private GkosKeyboardView keyboardView;
    private View emojiPane;
    private LayoutEngine layoutEngine;
    private boolean emojiVisible = false;

    // Predictive text
    private WordDictionary wordDictionary;
    private UserDictionary userDictionary;
    private UserBigrams userBigrams;
    private BigramDictionary bigramDictionary;
    private StringBuilder currentWord = new StringBuilder();
    private String previousWord;
    private String currentLangId;
    private static final int MAX_SUGGESTIONS = 3;

    // Unicode hex input mode
    private boolean unicodeInputMode = false;
    private StringBuilder unicodeBuffer = new StringBuilder();

    @Override
    public void onCreate() {
        super.onCreate();
        layoutEngine = new LayoutEngine();
        wordDictionary = new WordDictionary();
        userDictionary = new UserDictionary();
        userBigrams = new UserBigrams();
        bigramDictionary = new BigramDictionary();
        loadLayoutForCurrentSubtype();
    }

    @Override
    public void onDestroy() {
        if (userDictionary != null) {
            userDictionary.close();
        }
        if (userBigrams != null) {
            userBigrams.close();
        }
        super.onDestroy();
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype);
        loadLayoutForCurrentSubtype();
    }

    /**
     * Reads the language from the preferred layout or current subtype,
     * then checks the per-language variant preference to determine
     * the layout filename. Falls back to English Optimized.
     */
    private void loadLayoutForCurrentSubtype() {
        android.content.SharedPreferences prefs =
                getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String langId = prefs.getString(SettingsActivity.KEY_PREFERRED_LAYOUT, null);
        if (langId == null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            InputMethodSubtype subtype = imm != null ? imm.getCurrentInputMethodSubtype() : null;
            langId = getLayoutIdFromSubtype(subtype);
        }
        // Check per-language variant preference
        String variant = prefs.getString(SettingsActivity.KEY_VARIANT_PREFIX + langId, "optimized");
        String filename = "standard".equals(variant)
                ? langId + "-standard.xml"
                : langId + ".xml";
        try {
            Layout layout = layoutEngine.loadFromAssets(this, filename);
            layoutEngine.setLayout(layout);
        } catch (IOException | XmlPullParserException e) {
            langId = "en";
            try {
                Layout layout = layoutEngine.loadFromAssets(this, "en.xml");
                layoutEngine.setLayout(layout);
            } catch (IOException | XmlPullParserException e2) {
                // Fallback: layoutEngine has no layout
            }
        }

        // Reload dictionaries if the language changed
        if (!langId.equals(currentLangId)) {
            currentLangId = langId;
            wordDictionary.loadAsync(this, langId);
            bigramDictionary.loadAsync(this, langId);
            userDictionary.load(this, langId);
            userBigrams.load(this, langId);
            currentWord.setLength(0);
            previousWord = null;
            clearSuggestions();
        }
    }

    private static String getLayoutIdFromSubtype(InputMethodSubtype subtype) {
        if (subtype == null) return "en";
        String extra = subtype.getExtraValue();
        if (extra != null && extra.startsWith("layout=")) {
            return extra.substring(7);
        }
        return "en";
    }

    @Override
    public View onCreateInputView() {
        try {
            Window window = getWindow().getWindow();
            if (window != null) {
                window.setBackgroundDrawableResource(android.R.color.transparent);
            }
        } catch (Exception ignored) {
        }

        int height = (int) (200 * getResources().getDisplayMetrics().density);

        // Container that swaps between keyboard and emoji picker
        container = new FrameLayout(this);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, height));

        // Keyboard view
        keyboardView = new GkosKeyboardView(this);
        keyboardView.setOutputHandler(this);
        keyboardView.setOutcomeProvider(this);
        keyboardView.setGlobeClickListener(this);
        keyboardView.setSuggestionTapListener(this);
        keyboardView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        container.addView(keyboardView);

        // Emoji pane (hidden by default)
        emojiPane = createEmojiPane(height);
        emojiPane.setVisibility(View.GONE);
        container.addView(emojiPane);

        emojiVisible = false;
        return container;
    }

    private View createEmojiPane(int totalHeight) {
        float density = getResources().getDisplayMetrics().density;

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        layout.setBackgroundColor(0xFFF5F5F5);

        // Emoji picker (fills available space)
        EmojiPickerView picker = new EmojiPickerView(this);
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        picker.setLayoutParams(pickerParams);
        picker.setOnEmojiPickedListener(emojiViewItem -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(emojiViewItem.getEmoji(), 1);
            }
            // Auto-return to keyboard after picking an emoji
            hideEmojiPicker();
        });
        layout.addView(picker);

        // "Back to keyboard" bar at the bottom
        FrameLayout bottomBar = new FrameLayout(this);
        int barHeight = (int) (40 * density);
        bottomBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, barHeight));
        bottomBar.setBackgroundColor(0xFFE0E0E0);

        android.widget.TextView backBtn = new android.widget.TextView(this);
        backBtn.setText("ABC");
        backBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        backBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        backBtn.setTextColor(0xFF1565C0);
        backBtn.setGravity(Gravity.CENTER);
        backBtn.setContentDescription("Back to keyboard");
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        backBtn.setLayoutParams(btnParams);
        backBtn.setOnClickListener(v -> hideEmojiPicker());
        bottomBar.addView(backBtn);

        layout.addView(bottomBar);
        return layout;
    }

    private void showEmojiPicker() {
        if (container == null) return;
        keyboardView.setVisibility(View.GONE);
        emojiPane.setVisibility(View.VISIBLE);
        emojiVisible = true;
    }

    private void hideEmojiPicker() {
        if (container == null) return;
        emojiPane.setVisibility(View.GONE);
        keyboardView.setVisibility(View.VISIBLE);
        emojiVisible = false;
    }

    @Override
    public String getOutcomeForChord(int chordBitmask) {
        if (layoutEngine == null) return null;
        LayoutEngine.ResolveResult r = layoutEngine.resolve(chordBitmask);
        if (r == null) return null;
        if (r.isAction()) {
            String symbol = ACTION_SYMBOLS.get(r.action);
            return symbol != null ? symbol : r.action;
        }
        return r.text;
    }

    @Override
    public boolean isActionOutcome(int chordBitmask) {
        if (layoutEngine == null) return false;
        LayoutEngine.ResolveResult r = layoutEngine.resolve(chordBitmask);
        return r != null && r.isAction();
    }

    @Override
    public void onGlobeClick() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    public void onChord(int chord) {
        if (layoutEngine == null) return;

        LayoutEngine.ResolveResult result = layoutEngine.resolve(chord);
        if (result == null) return;

        // Unicode hex input mode intercepts all chords
        if (unicodeInputMode) {
            handleUnicodeChord(result);
            return;
        }

        if (result.isAction()) {
            performAction(result.action);
        } else {
            commitChordOutput(result.text);
        }
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        currentWord.setLength(0);
        previousWord = null;
        clearSuggestions();
        if (keyboardView != null) {
            keyboardView.onStartInput(info);
        }
    }

    @Override
    public void onWindowShown() {
        super.onWindowShown();
        // Reload the layout every time the keyboard appears, so changes
        // made in SettingsActivity take effect immediately.
        loadLayoutForCurrentSubtype();
        updateModeIndicator();
        if (keyboardView != null) {
            keyboardView.invalidate();
        }
    }

    /**
     * Called when a chord produces output. Commits text and updates word tracking.
     */
    public void commitChordOutput(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && text.length() > 0) {
            ic.commitText(text, 1);

            // Track the current word for predictive text
            if (isAllLetters(text)) {
                currentWord.append(text);
                updateSuggestions();
            } else {
                // Contains non-letter character(s): word boundary
                finishCurrentWord();
            }
        }
    }

    /**
     * Called for special actions (backspace, enter, etc.).
     */
    public void performAction(String action) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;

        switch (action) {
            case "backspace":
                ic.deleteSurroundingText(1, 0);
                if (currentWord.length() > 0) {
                    currentWord.deleteCharAt(currentWord.length() - 1);
                    if (currentWord.length() > 0) {
                        updateSuggestions();
                    } else {
                        clearSuggestions();
                    }
                }
                break;
            case "enter":
                finishCurrentWord();
                performEnterAction(ic);
                break;
            case "space":
                finishCurrentWord();
                ic.commitText(" ", 1);
                break;
            case "mode_toggle":
                if (layoutEngine != null) {
                    LayoutEngine.Mode m = layoutEngine.getMode();
                    layoutEngine.setMode(m == LayoutEngine.Mode.ABC ? LayoutEngine.Mode.NUM : LayoutEngine.Mode.ABC);
                }
                updateModeIndicator();
                break;
            case "shift":
                if (layoutEngine != null) layoutEngine.setShift(!layoutEngine.getShift());
                updateModeIndicator();
                break;
            case "symb":
                if (layoutEngine != null) layoutEngine.setSymb(!layoutEngine.getSymb());
                updateModeIndicator();
                break;
            case "tab":
                ic.commitText("\t", 1);
                break;
            case "esc":
                ic.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_ESCAPE));
                break;
            case "delete":
                ic.deleteSurroundingText(0, 1);
                break;
            case "emoji":
                showEmojiPicker();
                break;
            case "unicode_picker":
                enterUnicodeInputMode();
                break;
            case "ctrl":
            case "alt":
                // Modifiers not yet implemented
                break;
            default:
                break;
        }
    }

    // ── Enter key handling ────────────────────────────────────────────

    /**
     * Performs the appropriate Enter action based on the current editor.
     * If the editor has an explicit action (Go, Search, Send, Next, Done),
     * that action is performed.  Otherwise a newline is inserted (for
     * multi-line text fields, notes, etc.).
     */
    private void performEnterAction(InputConnection ic) {
        EditorInfo ei = getCurrentInputEditorInfo();
        if (ei != null) {
            int actionId = ei.imeOptions & EditorInfo.IME_MASK_ACTION;
            if (actionId != EditorInfo.IME_ACTION_NONE
                    && actionId != EditorInfo.IME_ACTION_UNSPECIFIED
                    && (ei.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
                // The editor has a specific action — perform it
                ic.performEditorAction(actionId);
                return;
            }
        }
        // No special action — insert a newline
        ic.commitText("\n", 1);
    }

    // ── Predictive text ──────────────────────────────────────────────

    @Override
    public void onSuggestionTapped(int index, String word) {
        if (word == null || word.isEmpty()) return;
        boolean firstInSentence = isFirstWordInSentence();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && currentWord.length() > 0) {
            ic.deleteSurroundingText(currentWord.length(), 0);
            ic.commitText(word + " ", 1);
        }
        if (userDictionary != null) {
            userDictionary.recordWord(word, firstInSentence);
        }
        String canonical = firstInSentence ? word.toLowerCase() : word;
        if (previousWord != null && userBigrams != null) {
            userBigrams.recordBigram(previousWord, canonical);
        }
        previousWord = canonical;
        currentWord.setLength(0);
        clearSuggestions();
    }

    /**
     * Returns the previous word by falling back to the InputConnection
     * when {@link #previousWord} is null (e.g. after cursor navigation).
     * Walks backwards through text before the cursor to find the last
     * complete word preceding the one currently being typed.
     */
    private String getEffectivePreviousWord() {
        if (previousWord != null) return previousWord;
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;

        CharSequence before = ic.getTextBeforeCursor(currentWord.length() + 50, 0);
        if (before == null) return null;
        int precedingLen = before.length() - currentWord.length();
        if (precedingLen <= 0) return null;

        // Walk backwards past whitespace to find end of previous word
        int end = precedingLen;
        while (end > 0 && Character.isWhitespace(before.charAt(end - 1))) end--;
        if (end == 0) return null;

        // Walk backwards to find start of previous word
        int start = end;
        while (start > 0 && Character.isLetter(before.charAt(start - 1))) start--;
        if (start == end) return null;
        return before.subSequence(start, end).toString();
    }

    /**
     * Queries bigrams (if a previous word is known), user dictionary, and
     * bundled dictionary, then merges results with bigram matches first,
     * de-duplicated, and applies first-in-sentence capitalisation.
     */
    private void updateSuggestions() {
        String prefix = currentWord.toString().toLowerCase();
        if (prefix.isEmpty()) {
            clearSuggestions();
            return;
        }

        // Bigram suggestions: contextually relevant completions
        String prev = getEffectivePreviousWord();
        List<String> userBigramMatches = (prev != null && userBigrams != null)
                ? userBigrams.getFollowers(prev, prefix, MAX_SUGGESTIONS)
                : new ArrayList<>();
        List<String> bundledBigramMatches = (prev != null && bigramDictionary != null)
                ? bigramDictionary.getFollowers(prev, prefix, MAX_SUGGESTIONS)
                : new ArrayList<>();

        // Unigram suggestions: user dictionary then bundled dictionary
        List<String> userMatches = userDictionary != null
                ? userDictionary.getMatches(prefix, MAX_SUGGESTIONS) : new ArrayList<>();
        List<String> bundledMatches = wordDictionary != null
                ? wordDictionary.getSuggestions(prefix, MAX_SUGGESTIONS) : new ArrayList<>();

        // Merge in priority order: user bigrams, bundled bigrams,
        // user unigrams, bundled unigrams; de-duplicate case-insensitively
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String s : userBigramMatches)    merged.add(s.toLowerCase());
        for (String s : bundledBigramMatches) merged.add(s.toLowerCase());
        for (String s : userMatches)          merged.add(s.toLowerCase());
        for (String s : bundledMatches)       merged.add(s.toLowerCase());

        // Rebuild with proper casing from the original sources
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String lower : merged) {
            String best = findOriginal(lower, userBigramMatches,
                    bundledBigramMatches, userMatches, bundledMatches);
            result.add(best != null ? best : lower);
        }

        boolean capitalise = isFirstWordInSentence();

        String[] arr = new String[Math.min(MAX_SUGGESTIONS, result.size())];
        int i = 0;
        for (String s : result) {
            if (i >= MAX_SUGGESTIONS) break;
            if (capitalise && !s.isEmpty() && Character.isLowerCase(s.charAt(0))) {
                s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
            arr[i++] = s;
        }

        if (keyboardView != null) {
            keyboardView.setSuggestions(arr.length > 0 ? arr : null);
        }
    }

    /**
     * Finds the original (properly-cased) form of a word from the source
     * lists, preferring the first match in priority order.
     */
    @SafeVarargs
    private static String findOriginal(String lower, List<String>... sources) {
        for (List<String> source : sources) {
            for (String s : source) {
                if (s.toLowerCase().equals(lower)) return s;
            }
        }
        return null;
    }

    /**
     * Determines whether the word currently being typed is the first word in
     * a sentence.  A word is first-in-sentence if there is no text before it,
     * or if the nearest non-whitespace character before it is a sentence-ending
     * punctuation mark ({@code .}, {@code !}, or {@code ?}).
     */
    private boolean isFirstWordInSentence() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return true;

        CharSequence before = ic.getTextBeforeCursor(currentWord.length() + 20, 0);
        if (before == null) return true;

        // Strip the current word from the end of the returned text
        int precedingLen = before.length() - currentWord.length();
        if (precedingLen <= 0) return true;

        // Walk backwards past whitespace to find the last meaningful character
        for (int i = precedingLen - 1; i >= 0; i--) {
            char c = before.charAt(i);
            if (!Character.isWhitespace(c)) {
                return c == '.' || c == '!' || c == '?';
            }
        }
        // Only whitespace before the current word — start of input
        return true;
    }

    /**
     * Records the current word in the user dictionary and clears tracking state.
     */
    private void finishCurrentWord() {
        if (currentWord.length() >= 2) {
            boolean firstInSentence = isFirstWordInSentence();
            String word = currentWord.toString();
            String canonical = firstInSentence ? word.toLowerCase() : word;
            if (userDictionary != null) {
                userDictionary.recordWord(word, firstInSentence);
            }
            if (previousWord != null && userBigrams != null) {
                userBigrams.recordBigram(previousWord, canonical);
            }
            previousWord = canonical;
        }
        currentWord.setLength(0);
        clearSuggestions();
    }

    private void clearSuggestions() {
        if (keyboardView != null) {
            keyboardView.setSuggestions(null);
        }
    }

    // ── Mode indicator ────────────────────────────────────────────────

    /**
     * Updates the mode label on the keyboard view.
     * Hidden in the default state (ABC, no shift, no symb).
     * Shows: "⇧" (shift), "123" (NUM), "⇧123", "@" (SYMB), "⇧@".
     */
    private void updateModeIndicator() {
        if (keyboardView == null || layoutEngine == null) return;
        boolean shift = layoutEngine.getShift();
        boolean symb = layoutEngine.getSymb();
        LayoutEngine.Mode mode = layoutEngine.getMode();

        StringBuilder sb = new StringBuilder();
        if (shift) sb.append("\u21E7");  // ⇧
        if (symb) {
            sb.append("@");
        } else if (mode == LayoutEngine.Mode.NUM) {
            sb.append("123");
        }
        // If sb is empty, we're in default ABC mode → hide indicator
        keyboardView.setModeLabel(sb.length() > 0 ? sb.toString() : null);
    }

    // ── Unicode hex input mode ──────────────────────────────────────

    private void enterUnicodeInputMode() {
        unicodeInputMode = true;
        unicodeBuffer.setLength(0);
        if (keyboardView != null) {
            keyboardView.setUnicodeHex("");
        }
    }

    private void exitUnicodeInputMode() {
        unicodeInputMode = false;
        unicodeBuffer.setLength(0);
        if (keyboardView != null) {
            keyboardView.setUnicodeHex(null);
        }
    }

    private void handleUnicodeChord(LayoutEngine.ResolveResult result) {
        if (result.isAction()) {
            switch (result.action) {
                case "enter":
                    // Commit the Unicode character and exit
                    if (unicodeBuffer.length() > 0) {
                        try {
                            int codepoint = Integer.parseInt(unicodeBuffer.toString(), 16);
                            if (codepoint >= 0 && codepoint <= 0x10FFFF
                                    && Character.isValidCodePoint(codepoint)) {
                                String ch = new String(Character.toChars(codepoint));
                                InputConnection ic = getCurrentInputConnection();
                                if (ic != null) {
                                    ic.commitText(ch, 1);
                                }
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    exitUnicodeInputMode();
                    break;
                case "backspace":
                    if (unicodeBuffer.length() > 0) {
                        unicodeBuffer.deleteCharAt(unicodeBuffer.length() - 1);
                        keyboardView.setUnicodeHex(unicodeBuffer.toString());
                    } else {
                        exitUnicodeInputMode();
                    }
                    break;
                case "esc":
                    exitUnicodeInputMode();
                    break;
                default:
                    // Ignore other actions in unicode mode
                    break;
            }
        } else if (result.text != null && result.text.length() == 1) {
            // Accept hex digits (0-9, a-f, A-F), max 6 digits (covers full Unicode range)
            char c = result.text.charAt(0);
            if (isHexDigit(c) && unicodeBuffer.length() < 6) {
                unicodeBuffer.append(Character.toUpperCase(c));
                keyboardView.setUnicodeHex(unicodeBuffer.toString());
            }
        }
    }

    private static boolean isAllLetters(CharSequence text) {
        if (text == null || text.length() == 0) return false;
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isLetter(text.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
