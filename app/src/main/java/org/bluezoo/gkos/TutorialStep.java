/*
 * TutorialStep.java
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

/**
 * A single step in the interactive tutorial.
 * Each step specifies the target chord, visual hints, and instruction text.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class TutorialStep {

    public enum Type {
        WELCOME,
        CHORD,
        COMPLETION
    }

    private final Type type;
    private final int targetChord;
    private final int highlightMask;
    private final int[] swipePath;
    private final int instructionResId;
    private final String outcomeDisplay;

    public TutorialStep(Type type, int targetChord, int highlightMask, int[] swipePath,
                        int instructionResId, String outcomeDisplay) {
        this.type = type;
        this.targetChord = targetChord;
        this.highlightMask = highlightMask;
        this.swipePath = swipePath;
        this.instructionResId = instructionResId;
        this.outcomeDisplay = outcomeDisplay;
    }

    public Type getType() { return type; }
    public int getTargetChord() { return targetChord; }
    public int getHighlightMask() { return highlightMask; }
    public int[] getSwipePath() { return swipePath; }
    public int getInstructionResId() { return instructionResId; }
    public String getOutcomeDisplay() { return outcomeDisplay; }

    public static TutorialStep welcome(int instructionResId) {
        return new TutorialStep(Type.WELCOME, 0, 0, null, instructionResId, null);
    }

    public static TutorialStep chord(int targetChord, int[] swipePath,
                                     int instructionResId, String outcomeDisplay) {
        return new TutorialStep(Type.CHORD, targetChord, targetChord, swipePath,
                instructionResId, outcomeDisplay);
    }

    public static TutorialStep completion(int instructionResId) {
        return new TutorialStep(Type.COMPLETION, 0, 0, null, instructionResId, null);
    }
}
