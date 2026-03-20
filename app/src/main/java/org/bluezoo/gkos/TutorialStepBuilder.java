/*
 * TutorialStepBuilder.java
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
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the tutorial step sequence dynamically from the loaded layout.
 * Attempts to find a progressive tutorial word from the bundled dictionary
 * where successive letters demonstrate increasingly complex chord types.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class TutorialStepBuilder {

    private static final String TAG = "TutorialStepBuilder";

    // Fixed chord bitmasks for actions (same across all layouts)
    static final int CHORD_BACKSPACE = 0x07;   // ABC = A+B+C
    static final int CHORD_SPACE = 0x38;       // DEF = D+E+F
    static final int CHORD_ENTER = 0x3B;       // A+B+D+E+F
    static final int CHORD_SHIFT = 0x12;       // B+E
    static final int CHORD_MODE_TOGGLE = 0x3F; // all six
    static final int CHORD_APOSTROPHE = 0x0A;  // B+D
    static final int CHORD_QUESTION = 0x21;    // A+F

    enum ChordType {
        SINGLE,
        ADJACENT,
        NON_ADJACENT,
        THREE_SAME,
        CROSS_SIDE,
        COMPLEX
    }

    private TutorialStepBuilder() {}

    /**
     * Builds the complete tutorial step sequence for the given layout.
     */
    public static List<TutorialStep> build(Context context, Layout layout, String langId) {
        List<TutorialStep> steps = new ArrayList<>();

        // Build character → chord mappings from layout (ABC mode, unshifted)
        Map<Character, Integer> charToChord = new HashMap<>();
        Map<Character, ChordType> charToType = new HashMap<>();
        for (int chord = 1; chord <= 63; chord++) {
            LayoutEntry entry = layout.getEntry(chord);
            if (entry == null) continue;
            String abc = entry.getAbc();
            if (abc != null && abc.length() == 1 && !LayoutEntry.isActionValue(abc)) {
                char c = abc.charAt(0);
                if (!charToChord.containsKey(c)) {
                    charToChord.put(c, chord);
                    charToType.put(c, classifyChord(chord));
                }
            }
        }

        // Steps 1-4: Progressive word (or individual character demos)
        String word = findProgressiveWord(context, langId, charToChord, charToType);
        if (word != null) {
            addWordSteps(steps, word, charToChord, charToType);
        } else {
            addFallbackCharSteps(steps, charToChord, charToType);
        }

        // Space (and mention Backspace in the instruction text)
        steps.add(TutorialStep.chord(CHORD_SPACE, new int[]{3, 4, 5},
                R.string.tutorial_space, "\u2423"));

        // Cross-side chord (find a single-character cross-side mapping)
        addCrossSideStep(steps, charToChord, charToType);

        // Step 9: Apostrophe
        steps.add(TutorialStep.chord(CHORD_APOSTROPHE, new int[]{3, 1},
                R.string.tutorial_apostrophe, "'"));

        // Step 10: Question mark
        steps.add(TutorialStep.chord(CHORD_QUESTION, new int[]{0, 5},
                R.string.tutorial_question_mark, "?"));

        // Step 11: Enter
        steps.add(TutorialStep.chord(CHORD_ENTER, new int[]{1, 0, 3, 4, 5},
                R.string.tutorial_enter, "\u23CE"));

        // Step 12-13: Shift on then off
        steps.add(TutorialStep.chord(CHORD_SHIFT, new int[]{1, 4},
                R.string.tutorial_shift_on, "\u21E7"));
        steps.add(TutorialStep.chord(CHORD_SHIFT, new int[]{1, 4},
                R.string.tutorial_shift_off, "\u21E7"));

        // Steps 14-18: Numeric mode
        addNumericSteps(steps, layout);

        // Final: Completion
        steps.add(TutorialStep.completion(R.string.tutorial_complete));

        return steps;
    }

    /**
     * Adds steps for the letters of a progressive word.
     */
    private static void addWordSteps(List<TutorialStep> steps, String word,
                                     Map<Character, Integer> charToChord,
                                     Map<Character, ChordType> charToType) {
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            int chord = charToChord.get(c);
            ChordType type = charToType.get(c);
            int[] path = computeSwipePath(chord);

            int instrRes;
            if (i == 0) {
                instrRes = R.string.tutorial_single_key;
            } else if (i == 1) {
                instrRes = R.string.tutorial_single_key_2;
            } else if (type == ChordType.ADJACENT) {
                instrRes = R.string.tutorial_adjacent;
            } else if (type == ChordType.NON_ADJACENT) {
                instrRes = R.string.tutorial_non_adjacent;
            } else {
                instrRes = R.string.tutorial_single_key_2;
            }
            steps.add(TutorialStep.chord(chord, path, instrRes, String.valueOf(c)));
        }
    }

    /**
     * Fallback when no progressive word is found: demo individual characters.
     */
    private static void addFallbackCharSteps(List<TutorialStep> steps,
                                             Map<Character, Integer> charToChord,
                                             Map<Character, ChordType> charToType) {
        Character leftSingle = null;
        Character rightSingle = null;
        Character adjChar = null;
        Character nonAdjChar = null;

        for (Map.Entry<Character, Integer> e : charToChord.entrySet()) {
            ChordType ct = charToType.get(e.getKey());
            int chord = e.getValue();
            if (ct == ChordType.SINGLE) {
                boolean isLeft = (chord & 0x07) != 0;
                if (isLeft && leftSingle == null) leftSingle = e.getKey();
                if (!isLeft && rightSingle == null) rightSingle = e.getKey();
            } else if (ct == ChordType.ADJACENT && adjChar == null) {
                adjChar = e.getKey();
            } else if (ct == ChordType.NON_ADJACENT && nonAdjChar == null) {
                nonAdjChar = e.getKey();
            }
        }

        if (leftSingle != null) {
            int ch = charToChord.get(leftSingle);
            steps.add(TutorialStep.chord(ch, computeSwipePath(ch),
                    R.string.tutorial_single_key, String.valueOf(leftSingle)));
        }
        if (rightSingle != null) {
            int ch = charToChord.get(rightSingle);
            steps.add(TutorialStep.chord(ch, computeSwipePath(ch),
                    R.string.tutorial_single_key_2, String.valueOf(rightSingle)));
        }
        if (adjChar != null) {
            int ch = charToChord.get(adjChar);
            steps.add(TutorialStep.chord(ch, computeSwipePath(ch),
                    R.string.tutorial_adjacent, String.valueOf(adjChar)));
        }
        if (nonAdjChar != null) {
            int ch = charToChord.get(nonAdjChar);
            steps.add(TutorialStep.chord(ch, computeSwipePath(ch),
                    R.string.tutorial_non_adjacent, String.valueOf(nonAdjChar)));
        }
    }

    /**
     * Adds a cross-side chord step using the first available cross-side letter.
     */
    private static void addCrossSideStep(List<TutorialStep> steps,
                                         Map<Character, Integer> charToChord,
                                         Map<Character, ChordType> charToType) {
        for (Map.Entry<Character, Integer> e : charToChord.entrySet()) {
            if (charToType.get(e.getKey()) == ChordType.CROSS_SIDE) {
                int chord = e.getValue();
                steps.add(TutorialStep.chord(chord, computeSwipePath(chord),
                        R.string.tutorial_cross_side, String.valueOf(e.getKey())));
                return;
            }
        }
    }

    /**
     * Adds numeric mode steps: switch, type a digit, type 7, type 0, switch back.
     */
    private static void addNumericSteps(List<TutorialStep> steps, Layout layout) {
        // Switch to NUM — C->B->A across the top to D->E->F
        steps.add(TutorialStep.chord(CHORD_MODE_TOGGLE,
                new int[]{2, 1, 0, 3, 4, 5},
                R.string.tutorial_numeric_switch, "\u21C4"));

        // Type "1" — find the chord that produces "1" in NUM mode
        int chord1 = findNumChord(layout, "1");
        if (chord1 > 0) {
            steps.add(TutorialStep.chord(chord1, computeSwipePath(chord1),
                    R.string.tutorial_numeric_digit, "1"));
        }

        // Type "7" — cross-side chord in NUM mode
        int chord7 = findNumChord(layout, "7");
        if (chord7 > 0) {
            steps.add(TutorialStep.chord(chord7, computeSwipePath(chord7),
                    R.string.tutorial_numeric_789, "7"));
        }

        // Type "0" — adjacent chord in NUM mode
        int chord0 = findNumChord(layout, "0");
        if (chord0 > 0) {
            steps.add(TutorialStep.chord(chord0, computeSwipePath(chord0),
                    R.string.tutorial_numeric_0, "0"));
        }

        // Switch back to ABC — F->E->D across the top to A->B->C
        steps.add(TutorialStep.chord(CHORD_MODE_TOGGLE,
                new int[]{5, 4, 3, 0, 1, 2},
                R.string.tutorial_numeric_back, "\u21C4"));
    }

    /** Finds the chord bitmask that produces the given string in NUM mode. */
    private static int findNumChord(Layout layout, String target) {
        for (int chord = 1; chord <= 63; chord++) {
            LayoutEntry entry = layout.getEntry(chord);
            if (entry != null && target.equals(entry.getNum())) {
                return chord;
            }
        }
        return 0;
    }

    // ── Chord classification ────────────────────────────────────────

    static ChordType classifyChord(int chord) {
        int bc = Integer.bitCount(chord);
        int leftBits = chord & 0x07;
        int rightBits = (chord >> 3) & 0x07;

        if (bc == 1) return ChordType.SINGLE;

        if (bc == 2) {
            if (leftBits != 0 && rightBits != 0) return ChordType.CROSS_SIDE;
            int side = leftBits != 0 ? leftBits : rightBits;
            if (side == 0x03 || side == 0x06) return ChordType.ADJACENT;
            return ChordType.NON_ADJACENT;
        }

        if (bc == 3 && (chord == 0x07 || chord == 0x38)) return ChordType.THREE_SAME;

        if (leftBits != 0 && rightBits != 0) return ChordType.CROSS_SIDE;

        return ChordType.COMPLEX;
    }

    // ── Swipe path computation ──────────────────────────────────────

    /**
     * Computes the most ergonomic swipe path for a chord bitmask.
     * Groups same-side keys, starts from the side with fewer keys,
     * sweeps toward the cross-over point (top row), then continues
     * down the other side.
     */
    static int[] computeSwipePath(int chord) {
        List<Integer> left = new ArrayList<>();
        List<Integer> right = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if ((chord & (1 << i)) != 0) left.add(i);
        }
        for (int i = 3; i < 6; i++) {
            if ((chord & (1 << i)) != 0) right.add(i);
        }

        List<Integer> path = new ArrayList<>();

        if (right.isEmpty()) {
            path.addAll(left);
        } else if (left.isEmpty()) {
            path.addAll(right);
        } else {
            // Cross-side: start with the side that has fewer keys (or right side if equal).
            // Sweep bottom-to-top on the starting side (toward cross-over point),
            // then top-to-bottom on the other side.
            List<Integer> firstSide;
            List<Integer> secondSide;
            if (left.size() < right.size()) {
                firstSide = left;
                secondSide = right;
            } else {
                firstSide = right;
                secondSide = left;
            }
            for (int i = firstSide.size() - 1; i >= 0; i--) {
                path.add(firstSide.get(i));
            }
            path.addAll(secondSide);
        }

        int[] result = new int[path.size()];
        for (int i = 0; i < path.size(); i++) result[i] = path.get(i);
        return result;
    }

    // ── Progressive word search ─────────────────────────────────────

    /**
     * Searches the bundled word dictionary for a word whose letters
     * demonstrate the chord type progression: SINGLE, SINGLE, ADJACENT,
     * NON_ADJACENT. Returns null if no suitable word is found.
     */
    static String findProgressiveWord(Context context, String langId,
                                      Map<Character, Integer> charToChord,
                                      Map<Character, ChordType> charToType) {
        ChordType[] pattern = {
                ChordType.SINGLE, ChordType.SINGLE,
                ChordType.ADJACENT, ChordType.NON_ADJACENT
        };

        List<String> words = loadWordListSync(context, langId);
        String best = null;

        for (String word : words) {
            if (word.length() < pattern.length || word.length() > 6) continue;

            boolean matches = true;
            for (int i = 0; i < pattern.length; i++) {
                ChordType ct = charToType.get(word.charAt(i));
                if (ct != pattern[i]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;

            // Verify all remaining letters have known chords
            boolean allKnown = true;
            for (int i = pattern.length; i < word.length(); i++) {
                if (!charToChord.containsKey(word.charAt(i))) {
                    allKnown = false;
                    break;
                }
            }
            if (!allKnown) continue;

            if (best == null || word.length() < best.length()) {
                best = word;
            }
            if (best.length() == pattern.length) break;
        }

        if (best != null) {
            Log.i(TAG, "Found progressive word: " + best + " for " + langId);
        } else {
            Log.i(TAG, "No progressive word found for " + langId + ", using fallback");
        }
        return best;
    }

    private static List<String> loadWordListSync(Context context, String langId) {
        List<String> result = new ArrayList<>();
        AssetManager am = context.getAssets();
        String path = "wordlists/" + langId + ".txt";
        try (InputStream is = am.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int sep = line.indexOf(' ');
                if (sep > 0) {
                    result.add(line.substring(0, sep).toLowerCase());
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "Failed to load word list for " + langId, e);
        }
        return result;
    }
}
