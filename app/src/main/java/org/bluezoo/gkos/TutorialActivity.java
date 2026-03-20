/*
 * TutorialActivity.java
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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Interactive tutorial wizard for new GKOS users.
 * Embeds a GkosKeyboardView directly (not as an IME) and walks through
 * chord types step by step, highlighting target keys and validating input.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class TutorialActivity extends Activity
        implements GkosKeyboardView.ChordOutputHandler, GkosKeyboardView.OutcomeProvider {

    /** Unicode keyboard symbols for action names (shared with GkosInputMethodService). */
    private static final Map<String, String> ACTION_SYMBOLS = new HashMap<>();
    static {
        ACTION_SYMBOLS.put("space",       "\u2423");
        ACTION_SYMBOLS.put("backspace",   "\u232B");
        ACTION_SYMBOLS.put("delete",      "\u2326");
        ACTION_SYMBOLS.put("enter",       "\u23CE");
        ACTION_SYMBOLS.put("tab",         "\u21E5");
        ACTION_SYMBOLS.put("esc",         "\u238B");
        ACTION_SYMBOLS.put("shift",       "\u21E7");
        ACTION_SYMBOLS.put("ctrl",        "\u2303");
        ACTION_SYMBOLS.put("alt",         "\u2325");
        ACTION_SYMBOLS.put("mode_toggle", "\u21C4");
        ACTION_SYMBOLS.put("symb",        "@");
        ACTION_SYMBOLS.put("UpArrow",     "\u2191");
        ACTION_SYMBOLS.put("DownArrow",   "\u2193");
        ACTION_SYMBOLS.put("LeftArrow",   "\u2190");
        ACTION_SYMBOLS.put("RightArrow",  "\u2192");
    }

    private GkosKeyboardView keyboardView;
    private TextView instructionText;
    private TextView outputText;
    private TextView stepCounter;
    private ProgressBar progressBar;
    private TextView skipButton;

    private LayoutEngine layoutEngine;
    private List<TutorialStep> steps;
    private int currentStepIndex = 0;
    private StringBuilder outputBuffer = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        instructionText = findViewById(R.id.tutorial_instruction);
        outputText = findViewById(R.id.tutorial_output);
        stepCounter = findViewById(R.id.tutorial_step_counter);
        progressBar = findViewById(R.id.tutorial_progress);
        skipButton = findViewById(R.id.tutorial_skip);

        keyboardView = findViewById(R.id.tutorial_keyboard);
        keyboardView.setOutputHandler(this);
        keyboardView.setOutcomeProvider(this);
        keyboardView.setGlobeVisible(true);

        skipButton.setOnClickListener(v -> completeTutorial());

        layoutEngine = new LayoutEngine();
        loadLayout();

        steps = TutorialStepBuilder.build(this, layoutEngine.getLayout(),
                layoutEngine.getLayout().getId());

        presentStep(0);
    }

    private void loadLayout() {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        String langId = prefs.getString(SettingsActivity.KEY_PREFERRED_LAYOUT, "en");
        String variant = prefs.getString(SettingsActivity.KEY_VARIANT_PREFIX + langId, "optimized");
        String filename = "standard".equals(variant)
                ? langId + "-standard.xml"
                : langId + ".xml";
        try {
            Layout layout = layoutEngine.loadFromAssets(this, filename);
            layoutEngine.setLayout(layout);
        } catch (IOException | XmlPullParserException e) {
            try {
                Layout layout = layoutEngine.loadFromAssets(this, "en.xml");
                layoutEngine.setLayout(layout);
            } catch (IOException | XmlPullParserException e2) {
                // Fatal: no layout available
            }
        }
    }

    // ── OutcomeProvider (for progressive disclosure on the keyboard) ──

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

    // ── Step presentation ───────────────────────────────────────────

    private void presentStep(int index) {
        if (index < 0 || index >= steps.size()) return;
        currentStepIndex = index;
        TutorialStep step = steps.get(index);

        // Update progress
        int totalSteps = steps.size();
        progressBar.setMax(totalSteps);
        progressBar.setProgress(index + 1);
        stepCounter.setText(getString(R.string.tutorial_step_of, index + 1, totalSteps));

        // Update instruction text
        String outcome = step.getOutcomeDisplay();
        if (outcome != null) {
            instructionText.setText(getString(step.getInstructionResId(), outcome));
        } else {
            instructionText.setText(step.getInstructionResId());
        }

        // Update keyboard highlights
        keyboardView.clearHighlights();
        if (step.getType() == TutorialStep.Type.CHORD) {
            keyboardView.setHighlightMask(step.getHighlightMask());
            if (step.getSwipePath() != null && step.getSwipePath().length > 1) {
                keyboardView.setSwipeHintPath(step.getSwipePath());
            }
        }

        // Completion: tap anywhere to finish
        if (step.getType() == TutorialStep.Type.COMPLETION) {
            skipButton.setText(R.string.tutorial_done);
            instructionText.setOnClickListener(v -> completeTutorial());
        } else {
            instructionText.setOnClickListener(null);
            instructionText.setClickable(false);
        }
    }

    private void advanceStep() {
        if (currentStepIndex + 1 < steps.size()) {
            presentStep(currentStepIndex + 1);
        } else {
            completeTutorial();
        }
    }

    private void completeTutorial() {
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean("tutorial_completed", true).apply();
        finish();
    }

    // ── Chord handling ──────────────────────────────────────────────

    @Override
    public void onChord(int chord) {
        if (currentStepIndex < 0 || currentStepIndex >= steps.size()) return;
        TutorialStep step = steps.get(currentStepIndex);

        if (step.getType() != TutorialStep.Type.CHORD) return;

        if (chord == step.getTargetChord()) {
            onCorrectChord(step);
        } else {
            onWrongChord(chord, step);
        }
    }

    private void onCorrectChord(TutorialStep step) {
        // Apply the chord's effect to the tutorial state
        LayoutEngine.ResolveResult result = layoutEngine.resolve(step.getTargetChord());
        if (result != null) {
            if (result.isAction()) {
                handleAction(result.action);
            } else if (result.text != null) {
                outputBuffer.append(result.text);
            }
        }
        outputText.setText(outputBuffer.toString());

        // Flash the keyboard to acknowledge success before moving on
        keyboardView.clearHighlights();

        // Small delay before advancing to let the user see the result
        keyboardView.postDelayed(this::advanceStep, 400);
    }

    private void onWrongChord(int chord, TutorialStep step) {
        // Find keys in the user's chord that aren't in the target
        int wrongKeys = chord & ~step.getTargetChord();
        if (wrongKeys != 0) {
            keyboardView.flashErrorKeys(wrongKeys);
        }
    }

    private void handleAction(String action) {
        switch (action) {
            case "backspace":
                if (outputBuffer.length() > 0) {
                    outputBuffer.deleteCharAt(outputBuffer.length() - 1);
                }
                break;
            case "space":
                outputBuffer.append(' ');
                break;
            case "enter":
                outputBuffer.append('\n');
                break;
            case "shift":
                if (layoutEngine != null) {
                    LayoutEngine.ShiftState state = layoutEngine.getShiftState();
                    layoutEngine.setShiftState(state == LayoutEngine.ShiftState.OFF
                            ? LayoutEngine.ShiftState.ON : LayoutEngine.ShiftState.OFF);
                }
                updateModeIndicator();
                break;
            case "mode_toggle":
                if (layoutEngine != null) {
                    LayoutEngine.Mode m = layoutEngine.getMode();
                    layoutEngine.setMode(m == LayoutEngine.Mode.ABC
                            ? LayoutEngine.Mode.NUM : LayoutEngine.Mode.ABC);
                }
                updateModeIndicator();
                break;
            default:
                break;
        }
    }

    private void updateModeIndicator() {
        if (keyboardView == null || layoutEngine == null) return;
        boolean shift = layoutEngine.getShift();
        LayoutEngine.Mode mode = layoutEngine.getMode();
        StringBuilder sb = new StringBuilder();
        if (shift) sb.append("\u21E7");
        if (mode == LayoutEngine.Mode.NUM) sb.append("123");
        keyboardView.setModeLabel(sb.length() > 0 ? sb.toString() : null);
    }
}
