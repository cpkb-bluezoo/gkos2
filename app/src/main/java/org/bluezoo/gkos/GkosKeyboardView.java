/*
 * GkosKeyboardView.java
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

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.HashSet;
import java.util.Set;

/**
 * 6-key GKOS keyboard view.
 * Keys: A(1), B(2), C(4) left; D(8), E(16), F(32) right.
 * Supports multitouch and swipe-to-chord.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class GkosKeyboardView extends View {

    private static final int KEY_A = 0x01;
    private static final int KEY_B = 0x02;
    private static final int KEY_C = 0x04;
    private static final int KEY_D = 0x08;
    private static final int KEY_E = 0x10;
    private static final int KEY_F = 0x20;

    private final RectF[] keyRects = new RectF[6];
    private final Paint keyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint keyPressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint secondaryTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bevelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint globePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint modePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suggestionTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint suggestionBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF globeRect;
    private RectF modeRect;
    private static final int MAX_SUGGESTIONS = 3;
    private final RectF[] suggestionRects = new RectF[MAX_SUGGESTIONS];

    private float cornerRadius;
    private float keyGap;
    private float textPadding;
    private float baseFontSize;

    // Colour scheme values (swapped for dark/light mode)
    private int colorAction;       // blue for action outcomes and mode indicator
    private int colorText;         // primary outcome text (for colour-swap restore)
    private int colorSecondary;    // secondary outcome text (for colour-swap restore)

    private int currentChord = 0;
    private Set<Integer> activePointers = new HashSet<>();
    private ChordOutputHandler outputHandler;
    private OutcomeProvider outcomeProvider;
    private GlobeClickListener globeClickListener;
    private boolean globeVisible = true;  // opens GKOS language/layout settings

    // Auto-repeat for space (ABC = 0x07) and backspace (DEF = 0x38)
    private static final int CHORD_SPACE = KEY_A | KEY_B | KEY_C;       // 0x07
    private static final int CHORD_BACKSPACE = KEY_D | KEY_E | KEY_F;   // 0x38
    private static final long AUTO_REPEAT_INTERVAL_MS = 80;
    private final Handler repeatHandler = new Handler(Looper.getMainLooper());
    private boolean autoRepeatActive = false;
    private int autoRepeatChord = 0;
    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (autoRepeatChord != 0 && outputHandler != null) {
                if (!autoRepeatActive) {
                    // First repeat — emit the initial chord
                    autoRepeatActive = true;
                    outputHandler.onChord(autoRepeatChord);
                }
                outputHandler.onChord(autoRepeatChord);
                repeatHandler.postDelayed(this, AUTO_REPEAT_INTERVAL_MS);
            }
        }
    };

    // Mode indicator — null or empty means hidden (default ABC mode)
    private String modeLabel = null;

    // Predictive text suggestions — null or empty means hidden
    private String[] suggestions = null;
    private SuggestionTapListener suggestionTapListener;

    // Unicode hex input mode display
    private String unicodeHex = null;        // null = not in unicode mode
    private final Paint unicodeBoxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unicodeHexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unicodePreviewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public interface ChordOutputHandler {
        void onChord(int chord);
    }

    /** Provides the outcome string for a chord (for progressive disclosure). */
    public interface OutcomeProvider {
        String getOutcomeForChord(int chordBitmask);

        /** Returns true if the chord resolves to an action (mode switch, nav, etc.). */
        boolean isActionOutcome(int chordBitmask);
    }

    /** Called when the globe icon is tapped (open settings). */
    public interface GlobeClickListener {
        void onGlobeClick();
    }

    /** Called when a predictive text suggestion is tapped. */
    public interface SuggestionTapListener {
        void onSuggestionTapped(int index, String word);
    }

    public GkosKeyboardView(Context context) {
        super(context);
        init();
    }

    public GkosKeyboardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Software layer needed for setShadowLayer on shapes
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setBackgroundColor(Color.TRANSPARENT);

        float density = getResources().getDisplayMetrics().density;
        cornerRadius = 14 * density;
        keyGap = 3 * density;
        textPadding = 32 * density;
        baseFontSize = 28 * density;

        // Paint styles (colours are set by applyColorScheme)
        keyPaint.setStyle(Paint.Style.FILL);
        keyPressedPaint.setStyle(Paint.Style.FILL);
        bevelPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStrokeWidth(1.5f * density);

        // Primary outcome text — large and bold
        textPaint.setTextSize(baseFontSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Secondary outcome text — half the size
        secondaryTextPaint.setTextSize(baseFontSize * 0.5f);
        secondaryTextPaint.setTextAlign(Paint.Align.CENTER);
        secondaryTextPaint.setTypeface(Typeface.DEFAULT);

        // Globe icon paint
        globePaint.setTextSize(20 * density);
        globePaint.setTextAlign(Paint.Align.CENTER);

        // Mode indicator paint — blue, bold, same size as globe
        modePaint.setTextSize(16 * density);
        modePaint.setTextAlign(Paint.Align.CENTER);
        modePaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Suggestion pill paints
        suggestionTextPaint.setTextSize(14 * density);
        suggestionTextPaint.setTextAlign(Paint.Align.CENTER);
        suggestionTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        suggestionBgPaint.setStyle(Paint.Style.FILL);

        // Unicode hex input mode paints
        unicodeBoxPaint.setStyle(Paint.Style.FILL);
        unicodeHexPaint.setTextSize(16 * density);
        unicodeHexPaint.setTextAlign(Paint.Align.CENTER);
        unicodeHexPaint.setTypeface(Typeface.MONOSPACE);
        unicodePreviewPaint.setTextSize(baseFontSize);
        unicodePreviewPaint.setTextAlign(Paint.Align.CENTER);

        applyColorScheme();
    }

    public void setOutputHandler(ChordOutputHandler handler) {
        this.outputHandler = handler;
    }

    public void setOutcomeProvider(OutcomeProvider provider) {
        this.outcomeProvider = provider;
    }

    public void setGlobeClickListener(GlobeClickListener listener) {
        this.globeClickListener = listener;
    }

    /**
     * Set the mode indicator label, or null/empty to hide it.
     * Hidden in the default ABC state; shown for SHIFT, NUM, SYMB.
     */
    public void setModeLabel(String label) {
        this.modeLabel = label;
        invalidate();
    }

    public void setSuggestionTapListener(SuggestionTapListener listener) {
        this.suggestionTapListener = listener;
    }

    /**
     * Set the predictive text suggestions to display in the transparent gap.
     * Pass null or an empty array to hide suggestions.
     */
    public void setSuggestions(String[] suggestions) {
        this.suggestions = (suggestions != null && suggestions.length > 0) ? suggestions : null;
        invalidate();
    }

    /**
     * Set the current unicode hex input string, or null to leave unicode mode.
     * Triggers a redraw to show/hide the hex box and preview character.
     */
    public void setUnicodeHex(String hex) {
        this.unicodeHex = hex;
        invalidate();
    }

    public void onStartInput(android.view.inputmethod.EditorInfo info) {
        // Reset state when starting new input
        cancelAutoRepeat();
        autoRepeatActive = false;
        currentChord = 0;
        activePointers.clear();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int minW = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 320, getResources().getDisplayMetrics());
        int minH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 200, getResources().getDisplayMetrics());
        int w = resolveSize(minW, widthMeasureSpec);
        int h = resolveSize(minH, heightMeasureSpec);
        setMeasuredDimension(Math.max(w, minW), Math.max(h, minH));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys(w, h);
    }

    private void layoutKeys(int w, int h) {
        float colW = w / 3f;
        float rowH = h / 3f;
        float g = keyGap;

        // Left side: A(top), B(mid), C(bottom) — with small gaps between keys
        keyRects[0] = new RectF(g, g, colW - g, rowH - g);                  // A
        keyRects[1] = new RectF(g, rowH + g, colW - g, 2 * rowH - g);      // B
        keyRects[2] = new RectF(g, 2 * rowH + g, colW - g, h - g);         // C

        // Right side: D(top), E(mid), F(bottom)
        keyRects[3] = new RectF(w - colW + g, g, w - g, rowH - g);         // D
        keyRects[4] = new RectF(w - colW + g, rowH + g, w - g, 2 * rowH - g); // E
        keyRects[5] = new RectF(w - colW + g, 2 * rowH + g, w - g, h - g); // F

        // Globe icon — bottom-right of the transparent centre area
        float iconSize = 36 * getResources().getDisplayMetrics().density;
        float gRight = w - colW - g;         // just left of right column
        float gBottom = h - g;
        globeRect = new RectF(gRight - iconSize, gBottom - iconSize, gRight, gBottom);

        // Mode indicator — bottom-left of the transparent centre area
        float gLeft = colW + g;              // just right of left column
        modeRect = new RectF(gLeft, gBottom - iconSize, gLeft + iconSize, gBottom);

        // Suggestion pills — vertically centred in the transparent gap
        float gapLeft = colW + g;
        float gapRight = w - colW - g;
        float gapCx = (gapLeft + gapRight) / 2f;
        float gapW = gapRight - gapLeft;
        float pillW = gapW * 0.85f;
        float pillH = 28 * getResources().getDisplayMetrics().density;
        float pillGap = 4 * getResources().getDisplayMetrics().density;
        float totalH = MAX_SUGGESTIONS * pillH + (MAX_SUGGESTIONS - 1) * pillGap;
        float startY = (h - totalH) / 2f;
        for (int i = 0; i < MAX_SUGGESTIONS; i++) {
            float top = startY + i * (pillH + pillGap);
            suggestionRects[i] = new RectF(gapCx - pillW / 2f, top,
                    gapCx + pillW / 2f, top + pillH);
        }
    }

    private int keyIndexToMask(int index) {
        return 1 << index;
    }

    private int hitTestKey(float x, float y) {
        for (int i = 0; i < 6; i++) {
            if (keyRects[i].contains(x, y)) {
                return keyIndexToMask(i);
            }
        }
        return 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Check suggestion tap first (single-finger only)
                if (suggestions != null && suggestionTapListener != null) {
                    for (int si = 0; si < suggestions.length && si < MAX_SUGGESTIONS; si++) {
                        if (suggestionRects[si] != null && suggestionRects[si].contains(x, y)) {
                            suggestionTapListener.onSuggestionTapped(si, suggestions[si]);
                            return true;
                        }
                    }
                }
                // Check globe tap (single-finger only)
                if (globeVisible && globeRect != null && globeRect.contains(x, y)) {
                    if (globeClickListener != null) {
                        globeClickListener.onGlobeClick();
                    }
                    return true;
                }
                // fall through
            case MotionEvent.ACTION_POINTER_DOWN: {
                int mask = hitTestKey(x, y);
                if (mask != 0) {
                    activePointers.add(pointerId);
                    int prevChord = currentChord;
                    currentChord |= mask;
                    checkAutoRepeatStart(prevChord, currentChord);
                    invalidate();
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                // For swipe: ACCUMULATE keys touched (don't replace — keep keys passed through)
                int prevChord = currentChord;
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    if (activePointers.contains(pid)) {
                        int m = hitTestKey(event.getX(i), event.getY(i));
                        if (m != 0) currentChord |= m;
                    }
                }
                if (currentChord != prevChord) {
                    checkAutoRepeatStart(prevChord, currentChord);
                }
                invalidate();
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                activePointers.remove(pointerId);
                if (event.getPointerCount() == 1 && action == MotionEvent.ACTION_UP) {
                    // Last pointer up — emit chord (unless auto-repeat already handled it)
                    cancelAutoRepeat();
                    if (!autoRepeatActive && currentChord != 0 && outputHandler != null) {
                        outputHandler.onChord(currentChord);
                    }
                    autoRepeatActive = false;
                    currentChord = 0;
                    activePointers.clear();
                    invalidate();
                } else {
                    // Recompute chord from remaining pointers
                    int prevChord = currentChord;
                    int remaining = 0;
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        if (i != pointerIndex) {
                            int pid = event.getPointerId(i);
                            if (activePointers.contains(pid)) {
                                remaining |= hitTestKey(event.getX(i), event.getY(i));
                            }
                        }
                    }
                    currentChord = remaining;
                    // If the chord is no longer a repeatable one, cancel
                    if (currentChord != autoRepeatChord) {
                        cancelAutoRepeat();
                    }
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelAutoRepeat();
                autoRepeatActive = false;
                currentChord = 0;
                activePointers.clear();
                invalidate();
                break;
        }
        return true;
    }

    /**
     * If the chord just became a repeatable one (space or backspace),
     * schedule auto-repeat after the system long-press timeout.
     */
    private void checkAutoRepeatStart(int prevChord, int newChord) {
        boolean wasRepeatable = (prevChord == CHORD_SPACE || prevChord == CHORD_BACKSPACE);
        boolean isRepeatable = (newChord == CHORD_SPACE || newChord == CHORD_BACKSPACE);

        if (isRepeatable && !wasRepeatable) {
            autoRepeatChord = newChord;
            autoRepeatActive = false;
            long delay = ViewConfiguration.get(getContext()).getLongPressTimeout();
            repeatHandler.postDelayed(repeatRunnable, delay);
        } else if (!isRepeatable && wasRepeatable) {
            cancelAutoRepeat();
        }
    }

    private void cancelAutoRepeat() {
        repeatHandler.removeCallbacks(repeatRunnable);
        autoRepeatChord = 0;
    }


    // ── Outcome helpers ──────────────────────────────────────────────

    /** Outcome if this key were added to the current chord (progressive disclosure). */
    private String getOutcome(int keyIndex) {
        if (outcomeProvider == null) return "";
        int chord = currentChord | keyIndexToMask(keyIndex);
        String outcome = outcomeProvider.getOutcomeForChord(chord);
        return outcome != null && !outcome.isEmpty() ? outcome : "";
    }

    /** Whether adding this key to the current chord would produce an action. */
    private boolean isActionForKey(int keyIndex) {
        if (outcomeProvider == null) return false;
        return outcomeProvider.isActionOutcome(currentChord | keyIndexToMask(keyIndex));
    }

    /** Outcome for an arbitrary chord mask (current chord OR'd with extra keys). */
    private String getOutcomeForMask(int mask) {
        if (outcomeProvider == null) return "";
        String outcome = outcomeProvider.getOutcomeForChord(currentChord | mask);
        return outcome != null ? outcome : "";
    }

    /** Whether the given mask OR'd with the current chord would produce an action. */
    private boolean isActionForMask(int mask) {
        if (outcomeProvider == null) return false;
        return outcomeProvider.isActionOutcome(currentChord | mask);
    }

    /** Outcome for the current chord exactly (used for merged pressed groups). */
    private String getCurrentChordOutcome() {
        if (outcomeProvider == null || currentChord == 0) return "";
        String outcome = outcomeProvider.getOutcomeForChord(currentChord);
        return outcome != null ? outcome : "";
    }

    /** Whether the current chord exactly resolves to an action. */
    private boolean isCurrentChordAction() {
        if (outcomeProvider == null || currentChord == 0) return false;
        return outcomeProvider.isActionOutcome(currentChord);
    }

    // ── Drawing ─────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        // 1. Button shapes (with merge for adjacent pressed keys)
        drawSideButtons(canvas, 0, 3, true);
        drawSideButtons(canvas, 3, 6, false);

        // 2. Secondary outcomes (discovery hints — drawn first so primaries overlay)
        drawSecondaryOutcomes(canvas, 0, true);
        drawSecondaryOutcomes(canvas, 3, false);

        // 3. Primary outcomes (what will happen next — large, on top)
        drawSidePrimary(canvas, 0, 3, true);
        drawSidePrimary(canvas, 3, 6, false);

        // 4. Globe icon in the transparent gap
        drawGlobe(canvas);

        // 5. Mode indicator in the transparent gap (opposite the globe)
        drawModeIndicator(canvas);

        // 6. Predictive text suggestions (in the centre gap)
        drawSuggestions(canvas);

        // 7. Unicode hex input overlay (in the centre gap — overlays suggestions)
        drawUnicodeInput(canvas);
    }

    private void drawUnicodeInput(Canvas canvas) {
        if (unicodeHex == null) return;

        // The transparent centre area is between the left and right key columns
        float gapLeft = keyRects[0] != null ? keyRects[0].right : getWidth() / 3f;
        float gapRight = keyRects[3] != null ? keyRects[3].left : getWidth() * 2f / 3f;
        float gapCx = (gapLeft + gapRight) / 2f;
        float density = getResources().getDisplayMetrics().density;
        float pad = 8 * density;

        // -- Box with "U+XXXX" at the top of the gap --
        String label = "U+" + unicodeHex;
        float labelW = unicodeHexPaint.measureText(label) + pad * 4;
        float labelH = unicodeHexPaint.getTextSize() + pad * 2;
        float boxTop = pad;
        RectF box = new RectF(gapCx - labelW / 2, boxTop, gapCx + labelW / 2, boxTop + labelH);
        canvas.drawRoundRect(box, cornerRadius * 0.5f, cornerRadius * 0.5f, unicodeBoxPaint);
        canvas.drawText(label, gapCx, box.centerY() + unicodeHexPaint.getTextSize() / 3f, unicodeHexPaint);

        // -- Large preview character centred below the box --
        int codepoint = parseHexCodepoint(unicodeHex);
        if (codepoint > 0) {
            String preview = new String(Character.toChars(codepoint));
            float previewY = box.bottom + pad + unicodePreviewPaint.getTextSize();
            canvas.drawText(preview, gapCx, previewY, unicodePreviewPaint);
        }
    }

    private static int parseHexCodepoint(String hex) {
        if (hex == null || hex.isEmpty()) return 0;
        try {
            int cp = Integer.parseInt(hex, 16);
            if (cp >= 0 && cp <= 0x10FFFF && Character.isValidCodePoint(cp)) {
                return cp;
            }
        } catch (NumberFormatException ignored) {
        }
        return 0;
    }

    private void drawGlobe(Canvas canvas) {
        if (!globeVisible || globeRect == null) return;
        float cx = globeRect.centerX();
        float cy = globeRect.centerY() + globePaint.getTextSize() / 3f;
        canvas.drawText("\uD83C\uDF10", cx, cy, globePaint);  // 🌐
    }

    private void drawModeIndicator(Canvas canvas) {
        if (modeLabel == null || modeLabel.isEmpty() || modeRect == null) return;
        float cx = modeRect.centerX();
        float cy = modeRect.centerY() + modePaint.getTextSize() / 3f;
        canvas.drawText(modeLabel, cx, cy, modePaint);
    }

    private void drawSuggestions(Canvas canvas) {
        if (suggestions == null || suggestions.length == 0 || unicodeHex != null) return;
        float pillRadius = cornerRadius * 0.5f;
        for (int i = 0; i < suggestions.length && i < MAX_SUGGESTIONS; i++) {
            RectF r = suggestionRects[i];
            if (r == null) continue;
            // Pill background
            canvas.drawRoundRect(r, pillRadius, pillRadius, suggestionBgPaint);
            // Text centred in the pill
            String word = suggestions[i];
            if (word.length() > 16) word = word.substring(0, 15) + "\u2026";
            float cy = r.centerY() + suggestionTextPaint.getTextSize() / 3f;
            canvas.drawText(word, r.centerX(), cy, suggestionTextPaint);
        }
    }

    // ── Button shapes ───────────────────────────────────────────────

    private void drawSideButtons(Canvas canvas, int startIdx, int endIdx, boolean leftSide) {
        int count = endIdx - startIdx;
        boolean[] pressed = pressedFlags(startIdx, count);
        boolean[] merged = new boolean[count];

        // Merged groups of adjacent pressed keys
        int i = 0;
        while (i < count) {
            if (pressed[i]) {
                int j = i + 1;
                while (j < count && pressed[j]) j++;
                if (j - i >= 2) {
                    RectF first = keyRects[startIdx + i];
                    RectF last  = keyRects[startIdx + j - 1];
                    drawButton(canvas, new RectF(first.left, first.top, last.right, last.bottom), true);
                    for (int k = i; k < j; k++) merged[k] = true;
                }
                i = j;
            } else {
                i++;
            }
        }

        // Individual (non-merged) keys
        for (i = 0; i < count; i++) {
            if (!merged[i] && keyRects[startIdx + i] != null) {
                drawButton(canvas, keyRects[startIdx + i], pressed[i]);
            }
        }
    }

    private void drawButton(Canvas canvas, RectF rect, boolean pressed) {
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius,
                pressed ? keyPressedPaint : keyPaint);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bevelPaint);
    }

    // ── Primary outcome text ────────────────────────────────────────

    private void drawSidePrimary(Canvas canvas, int startIdx, int endIdx, boolean leftSide) {
        int count = endIdx - startIdx;
        boolean[] pressed = pressedFlags(startIdx, count);
        boolean[] merged = new boolean[count];

        // Merged groups → single outcome centred in the merged rect
        int i = 0;
        while (i < count) {
            if (pressed[i]) {
                int j = i + 1;
                while (j < count && pressed[j]) j++;
                if (j - i >= 2) {
                    RectF first = keyRects[startIdx + i];
                    RectF last  = keyRects[startIdx + j - 1];
                    RectF area  = new RectF(first.left, first.top, last.right, last.bottom);
                    drawPrimaryText(canvas, area, getCurrentChordOutcome(), leftSide,
                            isCurrentChordAction());
                    for (int k = i; k < j; k++) merged[k] = true;
                }
                i = j;
            } else {
                i++;
            }
        }

        // Individual keys
        for (i = 0; i < count; i++) {
            if (!merged[i] && keyRects[startIdx + i] != null) {
                drawPrimaryText(canvas, keyRects[startIdx + i],
                        getOutcome(startIdx + i), leftSide,
                        isActionForKey(startIdx + i));
            }
        }
    }

    /** Draw primary text with auto-scaling so long words fit.
     *  Action outcomes (mode switches, nav) are drawn in blue. */
    private void drawPrimaryText(Canvas canvas, RectF rect, String text,
                                 boolean leftSide, boolean isAction) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 14) text = text.substring(0, 13) + "\u2026";

        float maxWidth = rect.width() - textPadding * 2;
        float origSize = textPaint.getTextSize();
        float measured = textPaint.measureText(text);
        if (measured > maxWidth && maxWidth > 0) {
            textPaint.setTextSize(origSize * (maxWidth / measured));
        }

        // Swap to action colour if needed
        if (isAction) textPaint.setColor(colorAction);

        float cy = rect.centerY() + textPaint.getTextSize() / 3f;
        if (leftSide) {
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(text, rect.right - textPadding, cy, textPaint);
        } else {
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(text, rect.left + textPadding, cy, textPaint);
        }

        // Restore
        if (isAction) textPaint.setColor(colorText);
        textPaint.setTextSize(origSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    // ── Secondary outcome text (discovery) ──────────────────────────

    /**
     * Draw secondary outcomes for one side.  Four combinations per side:
     *   1. Adjacent top+mid   → between top & mid keys, toward screen centre
     *   2. Adjacent mid+bot   → between mid & bot keys, toward screen centre
     *   3. Non-adjacent top+bot → centre of mid key (Y),  in transparent gap toward centre
     *   4. All three          → centre of mid key,       toward outer edge
     */
    private void drawSecondaryOutcomes(Canvas canvas, int startIdx, boolean leftSide) {
        if (outcomeProvider == null) return;

        int topMask = keyIndexToMask(startIdx);
        int midMask = keyIndexToMask(startIdx + 1);
        int botMask = keyIndexToMask(startIdx + 2);

        RectF topRect = keyRects[startIdx];
        RectF midRect = keyRects[startIdx + 1];
        RectF botRect = keyRects[startIdx + 2];
        if (topRect == null || midRect == null || botRect == null) return;

        float halfSecondary = secondaryTextPaint.getTextSize() / 3f;
        float innerPad  = -textPadding * 0.15f;   // slightly beyond inner edge (into gap toward centre)
        float outerPad  = textPadding * 0.5f;    // near outer edge
        float gapOffset = textPadding * 0.4f;    // offset into transparent gap

        // 1. Adjacent top + mid
        int adjTM = topMask | midMask;
        if ((currentChord & adjTM) != adjTM) {
            String s = getOutcomeForMask(adjTM);
            if (!s.isEmpty()) {
                boolean act = isActionForMask(adjTM);
                float y = (topRect.bottom + midRect.top) / 2f + halfSecondary;
                drawSecondaryText(canvas, s, y, midRect, leftSide, innerPad, true, act);
            }
        }

        // 2. Adjacent mid + bottom
        int adjMB = midMask | botMask;
        if ((currentChord & adjMB) != adjMB) {
            String s = getOutcomeForMask(adjMB);
            if (!s.isEmpty()) {
                boolean act = isActionForMask(adjMB);
                float y = (midRect.bottom + botRect.top) / 2f + halfSecondary;
                drawSecondaryText(canvas, s, y, midRect, leftSide, innerPad, true, act);
            }
        }

        // 3. Non-adjacent top + bottom → centre of mid key (Y), in transparent gap toward centre
        int nonAdj = topMask | botMask;
        if ((currentChord & nonAdj) != nonAdj) {
            String s = getOutcomeForMask(nonAdj);
            if (!s.isEmpty()) {
                boolean act = isActionForMask(nonAdj);
                float y = midRect.centerY() + halfSecondary;
                drawSecondaryInGap(canvas, s, y, midRect, leftSide, gapOffset, act);
            }
        }

        // 4. All three → centre of mid key, toward outer edge
        int allThree = topMask | midMask | botMask;
        if ((currentChord & allThree) != allThree) {
            String s = getOutcomeForMask(allThree);
            if (!s.isEmpty()) {
                boolean act = isActionForMask(allThree);
                float y = midRect.centerY() + halfSecondary;
                drawSecondaryText(canvas, s, y, midRect, leftSide, outerPad, false, act);
            }
        }
    }

    /**
     * Draw secondary text inside a button rect.
     * @param towardCentre  true → inner edge (toward screen centre);
     *                      false → outer edge (toward screen edge)
     * @param isAction      true → draw in blue action colour
     */
    private void drawSecondaryText(Canvas canvas, String text, float y,
                                   RectF rect, boolean leftSide,
                                   float padding, boolean towardCentre,
                                   boolean isAction) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 12) text = text.substring(0, 11) + "\u2026";

        float maxWidth = rect.width() - padding * 2;
        scaleSecondaryIfNeeded(text, maxWidth);

        if (isAction) secondaryTextPaint.setColor(colorAction);

        if (leftSide) {
            if (towardCentre) {
                secondaryTextPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(text, rect.right - padding, y, secondaryTextPaint);
            } else {
                secondaryTextPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(text, rect.left + padding, y, secondaryTextPaint);
            }
        } else {
            if (towardCentre) {
                secondaryTextPaint.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(text, rect.left + padding, y, secondaryTextPaint);
            } else {
                secondaryTextPaint.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(text, rect.right - padding, y, secondaryTextPaint);
            }
        }

        if (isAction) secondaryTextPaint.setColor(colorSecondary);
        restoreSecondarySize();
    }

    /** Draw secondary text in the transparent gap beyond the button, toward screen centre. */
    private void drawSecondaryInGap(Canvas canvas, String text, float y,
                                    RectF rect, boolean leftSide, float offset,
                                    boolean isAction) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 12) text = text.substring(0, 11) + "\u2026";

        if (isAction) secondaryTextPaint.setColor(colorAction);

        if (leftSide) {
            secondaryTextPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(text, rect.right + offset, y, secondaryTextPaint);
        } else {
            secondaryTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(text, rect.left - offset, y, secondaryTextPaint);
        }

        if (isAction) secondaryTextPaint.setColor(colorSecondary);
    }

    /** Temporarily shrink secondary paint if text is wider than maxWidth. */
    private void scaleSecondaryIfNeeded(String text, float maxWidth) {
        float origSize = baseFontSize * 0.5f;
        secondaryTextPaint.setTextSize(origSize);
        float measured = secondaryTextPaint.measureText(text);
        if (measured > maxWidth && maxWidth > 0) {
            secondaryTextPaint.setTextSize(origSize * (maxWidth / measured));
        }
    }

    private void restoreSecondarySize() {
        secondaryTextPaint.setTextSize(baseFontSize * 0.5f);
    }

    // ── Colour scheme (light/dark mode) ─────────────────────────────

    /**
     * Detects the current UI mode and applies the appropriate colour palette
     * to all paints.  Called from {@link #init()} and when configuration changes.
     */
    private void applyColorScheme() {
        boolean dark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        float density = getResources().getDisplayMetrics().density;

        if (dark) {
            // ── Dark palette ──
            keyPaint.setColor(0xFF3A3A3A);
            keyPaint.setShadowLayer(4 * density, 0, 2 * density, 0x66000000);
            keyPressedPaint.setColor(0xFF2A4A7A);
            keyPressedPaint.setShadowLayer(2 * density, 0, 1 * density, 0x44000000);
            bevelPaint.setColor(0x22FFFFFF);
            colorText = 0xFFE8E8E8;
            colorSecondary = 0xFF999999;
            colorAction = 0xFF64B5F6;   // lighter blue for dark backgrounds
            suggestionBgPaint.setColor(0xFF2C2C2C);
            suggestionBgPaint.setShadowLayer(2 * density, 0, 1 * density, 0x40000000);
            suggestionTextPaint.setColor(0xFFE0E0E0);
            globePaint.setColor(0xFFBBBBBB);
            unicodeBoxPaint.setColor(0xFF2A2A2A);
            unicodeBoxPaint.setShadowLayer(3 * density, 0, 1 * density, 0x60000000);
            unicodeHexPaint.setColor(0xFFDDDDDD);
            unicodePreviewPaint.setColor(0xFFEEEEEE);
        } else {
            // ── Light palette (default) ──
            keyPaint.setColor(0xFFE8E8E8);
            keyPaint.setShadowLayer(4 * density, 0, 2 * density, 0x44000000);
            keyPressedPaint.setColor(0xFFB0D0FF);
            keyPressedPaint.setShadowLayer(2 * density, 0, 1 * density, 0x30000000);
            bevelPaint.setColor(0x33FFFFFF);
            colorText = 0xFF222222;
            colorSecondary = 0xFF777777;
            colorAction = 0xFF1565C0;   // Material blue 800
            suggestionBgPaint.setColor(0xFFE8E8E8);
            suggestionBgPaint.setShadowLayer(2 * density, 0, 1 * density, 0x30000000);
            suggestionTextPaint.setColor(0xFF333333);
            globePaint.setColor(0xFF999999);
            unicodeBoxPaint.setColor(0xFFFFFFFF);
            unicodeBoxPaint.setShadowLayer(3 * density, 0, 1 * density, 0x40000000);
            unicodeHexPaint.setColor(0xFF333333);
            unicodePreviewPaint.setColor(0xFF111111);
        }

        textPaint.setColor(colorText);
        secondaryTextPaint.setColor(colorSecondary);
        modePaint.setColor(colorAction);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyColorScheme();
        invalidate();
    }

    // ── Utilities ───────────────────────────────────────────────────

    private boolean[] pressedFlags(int startIdx, int count) {
        boolean[] flags = new boolean[count];
        for (int i = 0; i < count; i++) {
            flags[i] = (currentChord & keyIndexToMask(startIdx + i)) != 0;
        }
        return flags;
    }
}
