package org.bluezoo.gkos;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link LayoutEntry}: data storage, action detection.
 */
public class LayoutEntryTest {

    @Test
    public void constructor_storesAllFields() {
        LayoutEntry e = new LayoutEntry(24, "s", "S", "0", "0", "0", "0");
        assertEquals(24, e.getChord());
        assertEquals("s", e.getAbc());
        assertEquals("S", e.getAbcShift());
        assertEquals("0", e.getNum());
        assertEquals("0", e.getNumShift());
        assertEquals("0", e.getSymb());
        assertEquals("0", e.getSymbShift());
    }

    @Test
    public void constructor_handlesNullValues() {
        LayoutEntry e = new LayoutEntry(1, "a", null, null, null, null, null);
        assertEquals("a", e.getAbc());
        assertNull(e.getAbcShift());
        assertNull(e.getNum());
    }

    /**
     * Every known action string must be recognized.
     */
    @Test
    public void isActionValue_allKnownActions() {
        String[] actions = {
            "backspace", "enter", "space", "mode_toggle", "shift", "symb",
            "ctrl", "alt", "tab", "esc", "delete",
            "UpArrow", "DownArrow", "LeftArrow", "RightArrow",
            "PageUp", "PageDown", "Home", "End", "Insert",
            "ScrollUp", "ScrollDown", "PanLeft", "PanRight",
            "PanLeftHome", "PanRightEnd", "WordLeft", "WordRight",
            "emoji", "unicode_picker"
        };
        for (String action : actions) {
            assertTrue("Expected isActionValue(\"" + action + "\") to be true",
                    LayoutEntry.isActionValue(action));
        }
    }

    /**
     * Normal characters and null must not be recognized as actions.
     */
    @Test
    public void isActionValue_nonActions() {
        assertFalse(LayoutEntry.isActionValue("a"));
        assertFalse(LayoutEntry.isActionValue("Z"));
        assertFalse(LayoutEntry.isActionValue("th"));
        assertFalse(LayoutEntry.isActionValue("."));
        assertFalse(LayoutEntry.isActionValue("ij"));
        assertFalse(LayoutEntry.isActionValue("1"));
        assertFalse(LayoutEntry.isActionValue(""));
        assertFalse(LayoutEntry.isActionValue(null));
    }

    @Test
    public void isAction_trueWhenAnyModeIsAction() {
        // abc is an action, others are characters
        LayoutEntry e = new LayoutEntry(56, "space", "space", "space", "space", "space", "space");
        assertTrue(e.isAction());

        // Only symb is an action
        LayoutEntry e2 = new LayoutEntry(45, "x", "X", "x", "X", "symb", "symb");
        assertTrue(e2.isAction());
    }

    @Test
    public void isAction_falseWhenAllCharacters() {
        LayoutEntry e = new LayoutEntry(1, "o", "O", "1", "1", "1", "1");
        assertFalse(e.isAction());
    }

    @Test
    public void isAction_falseWhenAllNull() {
        LayoutEntry e = new LayoutEntry(1, null, null, null, null, null, null);
        assertFalse(e.isAction());
    }
}
