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

import android.inputmethodservice.InputMethodService;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * GKOS Input Method Service.
 * Provides the chorded keyboard interface.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class GkosInputMethodService extends InputMethodService
        implements GkosKeyboardView.ChordOutputHandler, GkosKeyboardView.OutcomeProvider,
                   GkosKeyboardView.GlobeClickListener {

    private GkosKeyboardView keyboardView;
    private LayoutEngine layoutEngine;

    @Override
    public void onCreate() {
        super.onCreate();
        layoutEngine = new LayoutEngine();
        loadLayoutForCurrentSubtype();
    }

    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype);
        loadLayoutForCurrentSubtype();
    }

    private static final String PREFS_NAME = "gkos_prefs";
    private static final String KEY_PREFERRED_LAYOUT = "preferred_layout";

    /**
     * Reads layout id from user preference (Settings) or subtype extra value, and loads that layout.
     */
    private void loadLayoutForCurrentSubtype() {
        String layoutId = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_PREFERRED_LAYOUT, null);
        if (layoutId == null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            InputMethodSubtype subtype = imm != null ? imm.getCurrentInputMethodSubtype() : null;
            layoutId = getLayoutIdFromSubtype(subtype);
        }
        String filename = layoutId + ".xml";
        try {
            Layout layout = layoutEngine.loadFromAssets(this, filename);
            layoutEngine.setLayout(layout);
        } catch (IOException | XmlPullParserException e) {
            try {
                Layout layout = layoutEngine.loadFromAssets(this, "en-US.xml");
                layoutEngine.setLayout(layout);
            } catch (IOException | XmlPullParserException e2) {
                // Fallback: layoutEngine has no layout
            }
        }
    }

    private static String getLayoutIdFromSubtype(InputMethodSubtype subtype) {
        if (subtype == null) return "en-US";
        String extra = subtype.getExtraValue();
        if (extra != null && extra.startsWith("layout=")) {
            return extra.substring(7);
        }
        return "en-US";
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
        keyboardView = new GkosKeyboardView(this);
        keyboardView.setOutputHandler(this);
        keyboardView.setOutcomeProvider(this);
        keyboardView.setGlobeClickListener(this);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (int) (200 * getResources().getDisplayMetrics().density)
        );
        keyboardView.setLayoutParams(params);
        return keyboardView;
    }

    @Override
    public String getOutcomeForChord(int chordBitmask) {
        if (layoutEngine == null) return null;
        LayoutEngine.ResolveResult r = layoutEngine.resolve(chordBitmask);
        if (r == null) return null;
        return r.isAction() ? r.action : r.text;
    }

    @Override
    public void onGlobeClick() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showInputMethodPicker();
        }
    }

    @Override
    public void onChord(int chord) {
        if (layoutEngine == null) return;

        LayoutEngine.ResolveResult result = layoutEngine.resolve(chord);
        if (result == null) return;

        if (result.isAction()) {
            performAction(result.action);
        } else {
            commitChordOutput(result.text);
        }
    }

    @Override
    public void onStartInput(EditorInfo info, boolean restarting) {
        super.onStartInput(info, restarting);
        if (keyboardView != null) {
            keyboardView.onStartInput(info);
        }
    }

    /**
     * Called when a chord produces output. Commits text or performs action.
     */
    public void commitChordOutput(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && text != null && text.length() > 0) {
            ic.commitText(text, 1);
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
                break;
            case "enter":
                ic.commitText("\n", 1);
                break;
            case "space":
                ic.commitText(" ", 1);
                break;
            case "mode_toggle":
                if (layoutEngine != null) {
                    LayoutEngine.Mode m = layoutEngine.getMode();
                    layoutEngine.setMode(m == LayoutEngine.Mode.ABC ? LayoutEngine.Mode.NUM : LayoutEngine.Mode.ABC);
                }
                break;
            case "shift":
                if (layoutEngine != null) layoutEngine.setShift(!layoutEngine.getShift());
                break;
            case "symb":
                if (layoutEngine != null) layoutEngine.setSymb(!layoutEngine.getSymb());
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
            case "ctrl":
            case "alt":
                // Modifiers not yet implemented
                break;
            default:
                break;
        }
    }
}
