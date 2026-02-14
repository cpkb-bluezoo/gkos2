package org.bluezoo.gkos;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link Layout}: chord-indexed entry storage and lookup.
 */
public class LayoutTest {

    private Layout createTestLayout() {
        LayoutEntry[] entries = new LayoutEntry[] {
            new LayoutEntry(1, "o", "O", "1", "1", "1", "1"),
            new LayoutEntry(2, "t", "T", "2", "2", "2", "2"),
            new LayoutEntry(24, "s", "S", "0", "0", "0", "0"),
            new LayoutEntry(56, "space", "space", "space", "space", "space", "space"),
        };
        return new Layout("test", "Test Layout", entries);
    }

    @Test
    public void getId_returnsConstructorValue() {
        Layout layout = createTestLayout();
        assertEquals("test", layout.getId());
    }

    @Test
    public void getName_returnsConstructorValue() {
        Layout layout = createTestLayout();
        assertEquals("Test Layout", layout.getName());
    }

    @Test
    public void getEntry_validChord_returnsCorrectEntry() {
        Layout layout = createTestLayout();
        LayoutEntry e1 = layout.getEntry(1);
        assertNotNull(e1);
        assertEquals("o", e1.getAbc());

        LayoutEntry e24 = layout.getEntry(24);
        assertNotNull(e24);
        assertEquals("s", e24.getAbc());
    }

    @Test
    public void getEntry_unmappedChord_returnsNull() {
        Layout layout = createTestLayout();
        // Chord 3 was not added
        assertNull(layout.getEntry(3));
    }

    @Test
    public void getEntry_boundaryValues_returnsNull() {
        Layout layout = createTestLayout();
        assertNull(layout.getEntry(0));
        assertNull(layout.getEntry(-1));
        assertNull(layout.getEntry(64));
        assertNull(layout.getEntry(100));
    }

    @Test
    public void constructor_nullEntries_producesEmptyLayout() {
        Layout layout = new Layout("empty", "Empty", null);
        assertEquals("empty", layout.getId());
        for (int i = 1; i <= 63; i++) {
            assertNull(layout.getEntry(i));
        }
    }

    @Test
    public void constructor_filtersInvalidChords() {
        LayoutEntry[] entries = new LayoutEntry[] {
            new LayoutEntry(0, "bad", "BAD", null, null, null, null),
            new LayoutEntry(64, "bad2", "BAD2", null, null, null, null),
            new LayoutEntry(5, "ok", "OK", null, null, null, null),
        };
        Layout layout = new Layout("filter", "Filter Test", entries);
        assertNull(layout.getEntry(0));
        assertNotNull(layout.getEntry(5));
        assertEquals("ok", layout.getEntry(5).getAbc());
    }

    @Test
    public void constructor_duplicateChord_lastWins() {
        LayoutEntry[] entries = new LayoutEntry[] {
            new LayoutEntry(10, "first", "FIRST", null, null, null, null),
            new LayoutEntry(10, "second", "SECOND", null, null, null, null),
        };
        Layout layout = new Layout("dup", "Dup Test", entries);
        assertEquals("second", layout.getEntry(10).getAbc());
    }
}
