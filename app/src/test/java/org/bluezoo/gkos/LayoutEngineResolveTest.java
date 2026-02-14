package org.bluezoo.gkos;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link LayoutEngine#resolve(int)} and mode-selection logic.
 * Constructs layouts programmatically -- no XML or Context needed.
 */
public class LayoutEngineResolveTest {

    private LayoutEngine engine;

    /** A layout entry at chord 1 with distinct values in every mode slot. */
    private static final LayoutEntry ENTRY_CHAR = new LayoutEntry(
            1, "a", "A", "1", "!", "@", "#");

    /** A layout entry at chord 56 that is an action in all modes. */
    private static final LayoutEntry ENTRY_ACTION = new LayoutEntry(
            56, "space", "space", "space", "space", "space", "space");

    /** A layout entry at chord 18 with action in abc, char in num. */
    private static final LayoutEntry ENTRY_MIXED = new LayoutEntry(
            18, "shift", "shift", "5", "5", "shift", "shift");

    @Before
    public void setUp() {
        engine = new LayoutEngine();
        Layout layout = new Layout("test", "Test", new LayoutEntry[] {
                ENTRY_CHAR, ENTRY_ACTION, ENTRY_MIXED
        });
        engine.setLayout(layout);
        engine.setMode(LayoutEngine.Mode.ABC);
        engine.setShift(false);
        engine.setSymb(false);
    }

    // ── ABC mode ──

    @Test
    public void abc_noShift() {
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertFalse(r.isAction());
        assertEquals("a", r.text);
    }

    @Test
    public void abc_withShift() {
        engine.setShift(true);
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertEquals("A", r.text);
    }

    // ── NUM mode ──

    @Test
    public void num_noShift() {
        engine.setMode(LayoutEngine.Mode.NUM);
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertEquals("1", r.text);
    }

    @Test
    public void num_withShift() {
        engine.setMode(LayoutEngine.Mode.NUM);
        engine.setShift(true);
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertEquals("!", r.text);
    }

    // ── SYMB mode ──

    @Test
    public void symb_noShift() {
        engine.setSymb(true);
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertEquals("@", r.text);
    }

    @Test
    public void symb_withShift() {
        engine.setSymb(true);
        engine.setShift(true);
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertEquals("#", r.text);
    }

    // ── SYMB overrides NUM mode ──

    @Test
    public void symb_overrides_numMode() {
        engine.setMode(LayoutEngine.Mode.NUM);
        engine.setSymb(true);
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        // SYMB flag takes priority over NUM mode
        assertEquals("@", r.text);
    }

    // ── Action detection ──

    @Test
    public void actionEntry_returnsIsActionTrue() {
        LayoutEngine.ResolveResult r = engine.resolve(56);
        assertNotNull(r);
        assertTrue(r.isAction());
        assertEquals("space", r.action);
        assertNull(r.text);
    }

    @Test
    public void characterEntry_returnsIsActionFalse() {
        LayoutEngine.ResolveResult r = engine.resolve(1);
        assertNotNull(r);
        assertFalse(r.isAction());
        assertNull(r.action);
    }

    @Test
    public void mixedEntry_abcIsAction_numIsChar() {
        // In ABC mode, chord 18 = "shift" (action)
        LayoutEngine.ResolveResult r1 = engine.resolve(18);
        assertNotNull(r1);
        assertTrue(r1.isAction());
        assertEquals("shift", r1.action);

        // In NUM mode, chord 18 = "5" (character)
        engine.setMode(LayoutEngine.Mode.NUM);
        LayoutEngine.ResolveResult r2 = engine.resolve(18);
        assertNotNull(r2);
        assertFalse(r2.isAction());
        assertEquals("5", r2.text);
    }

    // ── Invalid inputs ──

    @Test
    public void resolve_chord0_returnsNull() {
        assertNull(engine.resolve(0));
    }

    @Test
    public void resolve_chordNegative_returnsNull() {
        assertNull(engine.resolve(-1));
    }

    @Test
    public void resolve_chord64_returnsNull() {
        assertNull(engine.resolve(64));
    }

    @Test
    public void resolve_unmappedChord_returnsNull() {
        // Chord 2 not in our test layout
        assertNull(engine.resolve(2));
    }

    @Test
    public void resolve_nullLayout_returnsNull() {
        engine.setLayout(null);
        assertNull(engine.resolve(1));
    }

    // ── Mode/shift/symb state ──

    @Test
    public void defaultState_isAbcNoShiftNoSymb() {
        LayoutEngine fresh = new LayoutEngine();
        assertEquals(LayoutEngine.Mode.ABC, fresh.getMode());
        assertFalse(fresh.getShift());
        assertFalse(fresh.getSymb());
    }

    @Test
    public void modeToggle_stateRoundTrip() {
        engine.setMode(LayoutEngine.Mode.NUM);
        assertEquals(LayoutEngine.Mode.NUM, engine.getMode());
        engine.setMode(LayoutEngine.Mode.ABC);
        assertEquals(LayoutEngine.Mode.ABC, engine.getMode());
    }

    @Test
    public void shiftToggle_stateRoundTrip() {
        engine.setShift(true);
        assertTrue(engine.getShift());
        engine.setShift(false);
        assertFalse(engine.getShift());
    }

    @Test
    public void symbToggle_stateRoundTrip() {
        engine.setSymb(true);
        assertTrue(engine.getSymb());
        engine.setSymb(false);
        assertFalse(engine.getSymb());
    }
}
