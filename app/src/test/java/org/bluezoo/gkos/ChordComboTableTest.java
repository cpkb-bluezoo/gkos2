package org.bluezoo.gkos;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link ChordComboTable}: chord bitmask / ref mapping.
 */
public class ChordComboTableTest {

    /**
     * chordToRef and refToChord must be inverses for all values 1-63.
     */
    @Test
    public void roundTrip_refToChordToRef() {
        for (int ref = 1; ref <= 63; ref++) {
            int chord = ChordComboTable.refToChord(ref);
            assertTrue("refToChord(" + ref + ") should be 1-63, got " + chord,
                    chord >= 1 && chord <= 63);
            int backToRef = ChordComboTable.chordToRef(chord);
            assertEquals("Round-trip failed for ref " + ref, ref, backToRef);
        }
    }

    @Test
    public void roundTrip_chordToRefToChord() {
        for (int chord = 1; chord <= 63; chord++) {
            int ref = ChordComboTable.chordToRef(chord);
            assertTrue("chordToRef(" + chord + ") should be 1-63, got " + ref,
                    ref >= 1 && ref <= 63);
            int backToChord = ChordComboTable.refToChord(ref);
            assertEquals("Round-trip failed for chord " + chord, chord, backToChord);
        }
    }

    /**
     * Boundary values outside 1-63 must return 0.
     */
    @Test
    public void chordToRef_boundaries() {
        assertEquals(0, ChordComboTable.chordToRef(0));
        assertEquals(0, ChordComboTable.chordToRef(-1));
        assertEquals(0, ChordComboTable.chordToRef(64));
        assertEquals(0, ChordComboTable.chordToRef(100));
    }

    @Test
    public void refToChord_boundaries() {
        assertEquals(0, ChordComboTable.refToChord(0));
        assertEquals(0, ChordComboTable.refToChord(-1));
        assertEquals(0, ChordComboTable.refToChord(64));
        assertEquals(0, ChordComboTable.refToChord(100));
    }

    /**
     * Spot-check known mappings from the GKOS table.
     */
    @Test
    public void knownMappings() {
        // Single keys: ref 1-6 = A, B, C, D, E, F = chords 1, 2, 4, 8, 16, 32
        assertEquals(1,  ChordComboTable.refToChord(1));
        assertEquals(2,  ChordComboTable.refToChord(2));
        assertEquals(4,  ChordComboTable.refToChord(3));
        assertEquals(8,  ChordComboTable.refToChord(4));
        assertEquals(16, ChordComboTable.refToChord(5));
        assertEquals(32, ChordComboTable.refToChord(6));

        // Two-key: ref 7 = D+E = 24
        assertEquals(24, ChordComboTable.refToChord(7));

        // Space: ref 50 = chord 56
        assertEquals(56, ChordComboTable.refToChord(50));

        // Enter: ref 54 = chord 59
        assertEquals(59, ChordComboTable.refToChord(54));

        // All keys: ref 61 = chord 63
        assertEquals(63, ChordComboTable.refToChord(61));
    }

    /**
     * The mapping must be a bijection: every chord 1-63 maps to a unique ref.
     */
    @Test
    public void bijection() {
        boolean[] refSeen = new boolean[64];
        boolean[] chordSeen = new boolean[64];
        for (int ref = 1; ref <= 63; ref++) {
            int chord = ChordComboTable.refToChord(ref);
            assertFalse("Chord " + chord + " mapped by multiple refs", chordSeen[chord]);
            chordSeen[chord] = true;
        }
        for (int chord = 1; chord <= 63; chord++) {
            int ref = ChordComboTable.chordToRef(chord);
            assertFalse("Ref " + ref + " mapped by multiple chords", refSeen[ref]);
            refSeen[ref] = true;
        }
    }
}
