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
    private String currentLangId;
    private static final int MAX_SUGGESTIONS = 3;

    // Auto-space tracking for punctuation handling
    private boolean lastSpaceWasAuto = false;
    private boolean suppressNextSelectionReset = false;

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
        lastSpaceWasAuto = false;
        suppressNextSelectionReset = false;
        clearSuggestions();
        maybeAutoShift();
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
        maybeAutoShift();
        updateModeIndicator();
        if (keyboardView != null) {
            keyboardView.invalidate();
        }
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                  int newSelStart, int newSelEnd,
                                  int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        if (suppressNextSelectionReset) {
            suppressNextSelectionReset = false;
        } else {
            lastSpaceWasAuto = false;
        }
        if (newSelStart == newSelEnd) {
            updateSuggestions();
        } else {
            clearSuggestions();
        }
    }

    /**
     * Called when a chord produces output. Commits text and updates word tracking.
     */
    public void commitChordOutput(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && text.length() > 0) {
            if (lastSpaceWasAuto && text.length() == 1
                    && isClosingPunctuation(text.charAt(0))
                    && shouldDeleteAutoSpaceBefore(ic, text.charAt(0))) {
                CharSequence before = ic.getTextBeforeCursor(1, 0);
                if (before != null && before.length() == 1 && before.charAt(0) == ' ') {
                    ic.deleteSurroundingText(1, 0);
                }
            }

            if (!isAllLetters(text)) {
                finishCurrentWord();
            }

            ic.commitText(text, 1);

            if (layoutEngine != null
                    && layoutEngine.getShiftState() == LayoutEngine.ShiftState.ONE_SHOT) {
                layoutEngine.setShiftState(LayoutEngine.ShiftState.OFF);
                updateModeIndicator();
            }

            boolean endsWithSpace = text.charAt(text.length() - 1) == ' ';
            lastSpaceWasAuto = endsWithSpace;
            if (endsWithSpace) {
                suppressNextSelectionReset = true;
            }

            if (isAllLetters(text)) {
                updateSuggestions();
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
                lastSpaceWasAuto = false;
                ic.deleteSurroundingText(1, 0);
                updateSuggestions();
                break;
            case "enter":
                lastSpaceWasAuto = false;
                finishCurrentWord();
                performEnterAction(ic);
                maybeAutoShift();
                break;
            case "space":
                finishCurrentWord();
                ic.commitText(" ", 1);
                lastSpaceWasAuto = false;
                maybeAutoShift();
                break;
            case "mode_toggle":
                if (layoutEngine != null) {
                    LayoutEngine.Mode m = layoutEngine.getMode();
                    layoutEngine.setMode(m == LayoutEngine.Mode.ABC ? LayoutEngine.Mode.NUM : LayoutEngine.Mode.ABC);
                }
                updateModeIndicator();
                break;
            case "shift":
                if (layoutEngine != null) {
                    switch (layoutEngine.getShiftState()) {
                        case OFF:
                            layoutEngine.setShiftState(LayoutEngine.ShiftState.ON);
                            break;
                        case ONE_SHOT:
                        case ON:
                            layoutEngine.setShiftState(LayoutEngine.ShiftState.OFF);
                            break;
                    }
                }
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

    /**
     * Returns the word immediately before the cursor by reading the
     * InputConnection buffer. This is the single source of truth for
     * the "current word" being typed, replacing keystroke tracking.
     */
    private String getWordBeforeCursor() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return "";
        CharSequence before = ic.getTextBeforeCursor(50, 0);
        if (before == null || before.length() == 0) return "";
        int end = before.length();
        int start = end;
        while (start > 0 && Character.isLetter(before.charAt(start - 1))) {
            start--;
        }
        return before.subSequence(start, end).toString();
    }

    /**
     * Returns true if the cursor is at the end of a word (or in whitespace/
     * empty space), meaning completions are appropriate. Returns false if
     * the character immediately after the cursor is a letter, indicating
     * the cursor is in the middle of a word.
     */
    private boolean isCursorAtEndOfWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return true;
        CharSequence after = ic.getTextAfterCursor(1, 0);
        if (after == null || after.length() == 0) return true;
        return !Character.isLetter(after.charAt(0));
    }

    @Override
    public void onSuggestionTapped(int index, String word) {
        if (word == null || word.isEmpty()) return;
        boolean firstInSentence = isFirstWordInSentence();
        String prev = getEffectivePreviousWord();
        String partial = getWordBeforeCursor();
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && partial.length() > 0) {
            ic.deleteSurroundingText(partial.length(), 0);
            ic.commitText(word + " ", 1);
            lastSpaceWasAuto = true;
            suppressNextSelectionReset = true;
        }
        if (userDictionary != null) {
            userDictionary.recordWord(word, firstInSentence);
        }
        String canonical = firstInSentence ? word.toLowerCase() : word;
        if (prev != null && userBigrams != null) {
            userBigrams.recordBigram(prev.toLowerCase(), canonical);
        }
        clearSuggestions();
        maybeAutoShift();
    }

    /**
     * Returns the complete word preceding the word currently at the cursor,
     * derived entirely from the InputConnection buffer.
     */
    private String getEffectivePreviousWord() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return null;

        CharSequence before = ic.getTextBeforeCursor(100, 0);
        if (before == null || before.length() == 0) return null;

        int i = before.length() - 1;
        // Skip past current word (letters at cursor)
        while (i >= 0 && Character.isLetter(before.charAt(i))) i--;
        // Skip whitespace between words
        while (i >= 0 && Character.isWhitespace(before.charAt(i))) i--;
        if (i < 0) return null;
        // i now points to last char of previous word — must be a letter
        if (!Character.isLetter(before.charAt(i))) return null;
        int end = i + 1;
        int start = end;
        while (start > 0 && Character.isLetter(before.charAt(start - 1))) start--;
        return before.subSequence(start, end).toString();
    }

    /**
     * Queries bigrams (if a previous word is known), user dictionary, and
     * bundled dictionary, then merges results with bigram matches first,
     * de-duplicated, and applies first-in-sentence capitalisation.
     */
    private void updateSuggestions() {
        if (!isCursorAtEndOfWord()) {
            clearSuggestions();
            return;
        }

        String wordAtCursor = getWordBeforeCursor();
        String prefix = wordAtCursor.toLowerCase();
        if (prefix.isEmpty()) {
            clearSuggestions();
            return;
        }

        // Bigram suggestions: contextually relevant completions
        String prev = getEffectivePreviousWord();
        String prevLower = prev != null ? prev.toLowerCase() : null;
        List<String> userBigramMatches = (prevLower != null && userBigrams != null)
                ? userBigrams.getFollowers(prevLower, prefix, MAX_SUGGESTIONS)
                : new ArrayList<>();
        List<String> bundledBigramMatches = (prevLower != null && bigramDictionary != null)
                ? bigramDictionary.getFollowers(prevLower, prefix, MAX_SUGGESTIONS)
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
        int idx = 0;
        for (String s : result) {
            if (idx >= MAX_SUGGESTIONS) break;
            if (capitalise && !s.isEmpty() && Character.isLowerCase(s.charAt(0))) {
                s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
            }
            arr[idx++] = s;
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
     * a sentence.  Reads the buffer directly: walks backwards past any letters
     * (the current word), then past whitespace, and checks for sentence-ending
     * punctuation ({@code .}, {@code !}, or {@code ?}).  Returns true if there
     * is no text before the current word (start of input).
     */
    private boolean isFirstWordInSentence() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return true;

        CharSequence before = ic.getTextBeforeCursor(70, 0);
        if (before == null || before.length() == 0) return true;

        int i = before.length() - 1;
        // Skip past current word (letters at cursor)
        while (i >= 0 && Character.isLetter(before.charAt(i))) i--;
        // Skip whitespace
        while (i >= 0 && Character.isWhitespace(before.charAt(i))) i--;
        if (i < 0) return true;
        char c = before.charAt(i);
        return c == '.' || c == '!' || c == '?';
    }

    /**
     * Records the word at the cursor in the user dictionary and bigrams.
     * Must be called while the word is still at the cursor (before the
     * word-boundary character is committed).
     */
    private void finishCurrentWord() {
        String word = getWordBeforeCursor();
        if (word.length() >= 2) {
            boolean firstInSentence = isFirstWordInSentence();
            String canonical = firstInSentence ? word.toLowerCase() : word;
            if (userDictionary != null) {
                userDictionary.recordWord(word, firstInSentence);
            }
            String prev = getEffectivePreviousWord();
            if (prev != null && userBigrams != null) {
                userBigrams.recordBigram(prev.toLowerCase(), canonical);
            }
        }
        clearSuggestions();
    }

    private void clearSuggestions() {
        if (keyboardView != null) {
            keyboardView.setSuggestions(null);
        }
    }

    // ── Auto-capitalization ─────────────────────────────────────────

    /**
     * Engages one-shot shift if conditions are met: layout supports caps,
     * we're in ABC mode (not SYMB), shift is currently off, and the cursor
     * is at the start of a sentence.
     */
    private void maybeAutoShift() {
        if (layoutEngine == null) return;
        Layout layout = layoutEngine.getLayout();
        if (layout == null || !layout.supportsCaps()) return;
        if (layoutEngine.getMode() != LayoutEngine.Mode.ABC) return;
        if (layoutEngine.getSymb()) return;
        if (layoutEngine.getShiftState() != LayoutEngine.ShiftState.OFF) return;
        if (isFirstWordInSentence()) {
            layoutEngine.setShiftState(LayoutEngine.ShiftState.ONE_SHOT);
            updateModeIndicator();
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

    // ── Punctuation auto-space helpers ────────────────────────────────

    private static final String CLOSING_PUNCTUATION = ".,!?;:'/" + '"';

    private static boolean isClosingPunctuation(char c) {
        return CLOSING_PUNCTUATION.indexOf(c) >= 0;
    }

    private boolean isSpacedPunctuation(char c) {
        if (layoutEngine == null) return false;
        Layout layout = layoutEngine.getLayout();
        if (layout == null) return false;
        String spaced = layout.getSpacedPunctuation();
        return spaced != null && spaced.indexOf(c) >= 0;
    }

    /**
     * Decides whether an auto-inserted space should be deleted before the
     * given punctuation character, taking language rules and quote context
     * into account.
     */
    private boolean shouldDeleteAutoSpaceBefore(InputConnection ic, char c) {
        if (isSpacedPunctuation(c)) return false;
        if (c == '"') {
            CharSequence prior = ic.getTextBeforeCursor(500, 0);
            if (prior != null) {
                int count = 0;
                for (int i = 0; i < prior.length(); i++) {
                    if (prior.charAt(i) == '"') count++;
                }
                return count % 2 == 1;
            }
            return false;
        }
        return true;
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
