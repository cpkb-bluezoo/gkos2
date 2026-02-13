/*
 * ChordComboTable.java
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
 * Official GKOS chord bitmask / ref (1-63) mapping.
 * Source: <a href="https://gkos.com/gkos/table/index.html">gkos.com/table</a>
 * Keys: A=0x01, B=0x02, C=0x04, D=0x08, E=0x10, F=0x20.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class ChordComboTable {

    private ChordComboTable() {}

    /**
     * Official GKOS combo table: ref (1–63) → chord bitmask.
     * Extracted from gkos.com/gkos/table/index.html Combo column.
     */
    private static final int[] REF_TO_BITMASK = {
        0,  1,  2,  4,  8,  16, 32, 24, 25, 26, 28, 48, 49, 50, 52,  3, 11, 19, 35,  6, 14, 22, 38, 40, 41, 42, 44,
        5, 13, 21, 37, 34, 20, 12, 33, 17, 10, 51, 30, 53, 46, 29,  9, 36, 27, 54,  7, 15, 23, 39, 56, 57, 58, 60,
        59, 61, 31, 62, 43, 18, 45, 63, 47, 55
    };

    /** Chord bitmask (1–63) → ref. Built from REF_TO_BITMASK inverse. */
    private static final int[] BITMASK_TO_REF = buildBitmaskToRef();

    private static int[] buildBitmaskToRef() {
        int[] map = new int[64];
        for (int ref = 1; ref <= 63; ref++) {
            int mask = REF_TO_BITMASK[ref];
            if (mask >= 1 && mask < 64) {
                map[mask] = ref;
            }
        }
        return map;
    }

    /**
     * Converts chord bitmask to GKOS ref (1–63).
     * Returns 0 if chord is invalid.
     */
    public static int chordToRef(int chordBitmask) {
        if (chordBitmask <= 0 || chordBitmask >= 64) {
            return 0;
        }
        return BITMASK_TO_REF[chordBitmask];
    }

    /**
     * Converts GKOS ref (1–63) to chord bitmask.
     * Returns 0 if ref is invalid.
     */
    public static int refToChord(int ref) {
        if (ref < 1 || ref > 63) {
            return 0;
        }
        return REF_TO_BITMASK[ref];
    }
}
