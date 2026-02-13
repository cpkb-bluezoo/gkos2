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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

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
    private RectF globeRect;

    private float cornerRadius;
    private float keyGap;
    private float textPadding;
    private float baseFontSize;

    private int currentChord = 0;
    private Set<Integer> activePointers = new HashSet<>();
    private ChordOutputHandler outputHandler;
    private OutcomeProvider outcomeProvider;
    private GlobeClickListener globeClickListener;

    public interface ChordOutputHandler {
        void onChord(int chord);
    }

    /** Provides the outcome string for a chord (for progressive disclosure). */
    public interface OutcomeProvider {
        String getOutcomeForChord(int chordBitmask);
    }

    /** Called when the globe icon is tapped (switch keyboard). */
    public interface GlobeClickListener {
        void onGlobeClick();
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

        // Unpressed key fill with drop shadow
        keyPaint.setColor(0xFFE8E8E8);
        keyPaint.setStyle(Paint.Style.FILL);
        keyPaint.setShadowLayer(4 * density, 0, 2 * density, 0x44000000);

        // Pressed key fill with subtler shadow (appears "pushed in")
        keyPressedPaint.setColor(0xFFB0D0FF);
        keyPressedPaint.setStyle(Paint.Style.FILL);
        keyPressedPaint.setShadowLayer(2 * density, 0, 1 * density, 0x30000000);

        // Subtle highlight stroke for bevel effect
        bevelPaint.setStyle(Paint.Style.STROKE);
        bevelPaint.setStrokeWidth(1.5f * density);
        bevelPaint.setColor(0x33FFFFFF);

        // Primary outcome text — large and bold
        textPaint.setColor(0xFF222222);
        textPaint.setTextSize(baseFontSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);

        // Secondary outcome text — half the size, lighter colour
        secondaryTextPaint.setColor(0xFF777777);
        secondaryTextPaint.setTextSize(baseFontSize * 0.5f);
        secondaryTextPaint.setTextAlign(Paint.Align.CENTER);
        secondaryTextPaint.setTypeface(Typeface.DEFAULT);

        // Globe icon paint
        globePaint.setColor(0xFF999999);
        globePaint.setTextSize(20 * density);
        globePaint.setTextAlign(Paint.Align.CENTER);
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

    public void onStartInput(android.view.inputmethod.EditorInfo info) {
        // Reset state when starting new input
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
        float globeSize = 36 * getResources().getDisplayMetrics().density;
        float gRight = w - colW - g;         // just left of right column
        float gBottom = h - g;
        globeRect = new RectF(gRight - globeSize, gBottom - globeSize, gRight, gBottom);
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
                // Check globe tap first (single-finger only)
                if (globeRect != null && globeRect.contains(x, y)) {
                    if (globeClickListener != null) {
                        globeClickListener.onGlobeClick();
                    }
                    return true;
                }
                // fall through
            case MotionEvent.ACTION_POINTER_DOWN:
                int mask = hitTestKey(x, y);
                if (mask != 0) {
                    activePointers.add(pointerId);
                    currentChord |= mask;
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_MOVE:
                // For swipe: ACCUMULATE keys touched (don't replace — keep keys passed through)
                for (int i = 0; i < event.getPointerCount(); i++) {
                    int pid = event.getPointerId(i);
                    if (activePointers.contains(pid)) {
                        int m = hitTestKey(event.getX(i), event.getY(i));
                        if (m != 0) currentChord |= m;
                    }
                }
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                activePointers.remove(pointerId);
                if (event.getPointerCount() == 1 && action == MotionEvent.ACTION_UP) {
                    // Last pointer up — emit chord
                    if (currentChord != 0 && outputHandler != null) {
                        outputHandler.onChord(currentChord);
                    }
                    currentChord = 0;
                    activePointers.clear();
                    invalidate();
                } else {
                    // Recompute chord from remaining pointers
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
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                currentChord = 0;
                activePointers.clear();
                invalidate();
                break;
        }
        return true;
    }


    // ── Outcome helpers ──────────────────────────────────────────────

    /** Outcome if this key were added to the current chord (progressive disclosure). */
    private String getOutcome(int keyIndex) {
        if (outcomeProvider == null) return "";
        int chord = currentChord | keyIndexToMask(keyIndex);
        String outcome = outcomeProvider.getOutcomeForChord(chord);
        return outcome != null && !outcome.isEmpty() ? outcome : "";
    }

    /** Outcome for an arbitrary chord mask (current chord OR'd with extra keys). */
    private String getOutcomeForMask(int mask) {
        if (outcomeProvider == null) return "";
        String outcome = outcomeProvider.getOutcomeForChord(currentChord | mask);
        return outcome != null ? outcome : "";
    }

    /** Outcome for the current chord exactly (used for merged pressed groups). */
    private String getCurrentChordOutcome() {
        if (outcomeProvider == null || currentChord == 0) return "";
        String outcome = outcomeProvider.getOutcomeForChord(currentChord);
        return outcome != null ? outcome : "";
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
    }

    private void drawGlobe(Canvas canvas) {
        if (globeRect == null) return;
        float cx = globeRect.centerX();
        float cy = globeRect.centerY() + globePaint.getTextSize() / 3f;
        canvas.drawText("\uD83C\uDF10", cx, cy, globePaint);  // 🌐
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
                    drawPrimaryText(canvas, area, getCurrentChordOutcome(), leftSide);
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
                drawPrimaryText(canvas, keyRects[startIdx + i], getOutcome(startIdx + i), leftSide);
            }
        }
    }

    /** Draw primary text with auto-scaling so long words like "UpArrow" fit. */
    private void drawPrimaryText(Canvas canvas, RectF rect, String text, boolean leftSide) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 14) text = text.substring(0, 13) + "…";

        float maxWidth = rect.width() - textPadding * 2;
        float origSize = textPaint.getTextSize();
        float measured = textPaint.measureText(text);
        if (measured > maxWidth && maxWidth > 0) {
            textPaint.setTextSize(origSize * (maxWidth / measured));
        }

        float cy = rect.centerY() + textPaint.getTextSize() / 3f;
        if (leftSide) {
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(text, rect.right - textPadding, cy, textPaint);
        } else {
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(text, rect.left + textPadding, cy, textPaint);
        }
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
                float y = (topRect.bottom + midRect.top) / 2f + halfSecondary;
                drawSecondaryText(canvas, s, y, midRect, leftSide, innerPad, true);
            }
        }

        // 2. Adjacent mid + bottom
        int adjMB = midMask | botMask;
        if ((currentChord & adjMB) != adjMB) {
            String s = getOutcomeForMask(adjMB);
            if (!s.isEmpty()) {
                float y = (midRect.bottom + botRect.top) / 2f + halfSecondary;
                drawSecondaryText(canvas, s, y, midRect, leftSide, innerPad, true);
            }
        }

        // 3. Non-adjacent top + bottom → centre of mid key (Y), in transparent gap toward centre
        int nonAdj = topMask | botMask;
        if ((currentChord & nonAdj) != nonAdj) {
            String s = getOutcomeForMask(nonAdj);
            if (!s.isEmpty()) {
                float y = midRect.centerY() + halfSecondary;
                drawSecondaryInGap(canvas, s, y, midRect, leftSide, gapOffset);
            }
        }

        // 4. All three → centre of mid key, toward outer edge
        int allThree = topMask | midMask | botMask;
        if ((currentChord & allThree) != allThree) {
            String s = getOutcomeForMask(allThree);
            if (!s.isEmpty()) {
                float y = midRect.centerY() + halfSecondary;
                drawSecondaryText(canvas, s, y, midRect, leftSide, outerPad, false);
            }
        }
    }

    /**
     * Draw secondary text inside a button rect.
     * @param towardCentre  true → inner edge (toward screen centre);
     *                      false → outer edge (toward screen edge)
     */
    private void drawSecondaryText(Canvas canvas, String text, float y,
                                   RectF rect, boolean leftSide,
                                   float padding, boolean towardCentre) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 12) text = text.substring(0, 11) + "…";

        float maxWidth = rect.width() - padding * 2;
        scaleSecondaryIfNeeded(text, maxWidth);

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
        restoreSecondarySize();
    }

    /** Draw secondary text in the transparent gap beyond the button, toward screen centre. */
    private void drawSecondaryInGap(Canvas canvas, String text, float y,
                                    RectF rect, boolean leftSide, float offset) {
        if (text == null || text.isEmpty()) return;
        if (text.length() > 12) text = text.substring(0, 11) + "…";

        if (leftSide) {
            secondaryTextPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(text, rect.right + offset, y, secondaryTextPaint);
        } else {
            secondaryTextPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(text, rect.left - offset, y, secondaryTextPaint);
        }
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

    // ── Utilities ───────────────────────────────────────────────────

    private boolean[] pressedFlags(int startIdx, int count) {
        boolean[] flags = new boolean[count];
        for (int i = 0; i < count; i++) {
            flags[i] = (currentChord & keyIndexToMask(startIdx + i)) != 0;
        }
        return flags;
    }
}
