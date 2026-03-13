package org.bluezoo.gkos;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Tests for punctuation auto-space deletion rules.
 * Verifies which characters should trigger space deletion and how
 * the spacedPunctuation layout attribute affects the behaviour.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class PunctuationSpacingTest {

    private static final String CLOSING_PUNCTUATION = ".,!?;:'/" + '"';

    private Layout englishLayout;
    private Layout frenchLayout;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        LayoutEngine engine = new LayoutEngine();
        englishLayout = engine.loadFromAssets(context, "en.xml");
        frenchLayout = engine.loadFromAssets(context, "fr.xml");
    }

    private static boolean isClosingPunctuation(char c) {
        return CLOSING_PUNCTUATION.indexOf(c) >= 0;
    }

    private static boolean isSpacedPunctuation(Layout layout, char c) {
        String spaced = layout.getSpacedPunctuation();
        return spaced != null && spaced.indexOf(c) >= 0;
    }

    /**
     * Returns true if an auto-space should be deleted before the given
     * character for the given layout (ignoring the double-quote context rule).
     */
    private static boolean shouldDeleteSimple(Layout layout, char c) {
        if (!isClosingPunctuation(c)) return false;
        return !isSpacedPunctuation(layout, c);
    }

    // ── Closing punctuation detection ─────────────────────────────────

    @Test
    public void period_isClosingPunctuation() {
        assertTrue(isClosingPunctuation('.'));
    }

    @Test
    public void comma_isClosingPunctuation() {
        assertTrue(isClosingPunctuation(','));
    }

    @Test
    public void exclamation_isClosingPunctuation() {
        assertTrue(isClosingPunctuation('!'));
    }

    @Test
    public void question_isClosingPunctuation() {
        assertTrue(isClosingPunctuation('?'));
    }

    @Test
    public void semicolon_isClosingPunctuation() {
        assertTrue(isClosingPunctuation(';'));
    }

    @Test
    public void colon_isClosingPunctuation() {
        assertTrue(isClosingPunctuation(':'));
    }

    @Test
    public void apostrophe_isClosingPunctuation() {
        assertTrue(isClosingPunctuation('\''));
    }

    @Test
    public void slash_isClosingPunctuation() {
        assertTrue(isClosingPunctuation('/'));
    }

    @Test
    public void doubleQuote_isClosingPunctuation() {
        assertTrue(isClosingPunctuation('"'));
    }

    @Test
    public void hyphen_isNotClosingPunctuation() {
        assertFalse(isClosingPunctuation('-'));
    }

    @Test
    public void letter_isNotClosingPunctuation() {
        assertFalse(isClosingPunctuation('a'));
    }

    @Test
    public void digit_isNotClosingPunctuation() {
        assertFalse(isClosingPunctuation('5'));
    }

    @Test
    public void space_isNotClosingPunctuation() {
        assertFalse(isClosingPunctuation(' '));
    }

    // ── English: all closing punctuation deletes auto-space ───────────

    @Test
    public void english_period_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, '.'));
    }

    @Test
    public void english_comma_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, ','));
    }

    @Test
    public void english_exclamation_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, '!'));
    }

    @Test
    public void english_question_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, '?'));
    }

    @Test
    public void english_semicolon_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, ';'));
    }

    @Test
    public void english_colon_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, ':'));
    }

    @Test
    public void english_apostrophe_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, '\''));
    }

    @Test
    public void english_slash_deletesSpace() {
        assertTrue(shouldDeleteSimple(englishLayout, '/'));
    }

    // ── French: period/comma/apostrophe/slash delete, !?;: keep ──────

    @Test
    public void french_period_deletesSpace() {
        assertTrue(shouldDeleteSimple(frenchLayout, '.'));
    }

    @Test
    public void french_comma_deletesSpace() {
        assertTrue(shouldDeleteSimple(frenchLayout, ','));
    }

    @Test
    public void french_apostrophe_deletesSpace() {
        assertTrue(shouldDeleteSimple(frenchLayout, '\''));
    }

    @Test
    public void french_slash_deletesSpace() {
        assertTrue(shouldDeleteSimple(frenchLayout, '/'));
    }

    @Test
    public void french_exclamation_keepsSpace() {
        assertFalse(shouldDeleteSimple(frenchLayout, '!'));
    }

    @Test
    public void french_question_keepsSpace() {
        assertFalse(shouldDeleteSimple(frenchLayout, '?'));
    }

    @Test
    public void french_semicolon_keepsSpace() {
        assertFalse(shouldDeleteSimple(frenchLayout, ';'));
    }

    @Test
    public void french_colon_keepsSpace() {
        assertFalse(shouldDeleteSimple(frenchLayout, ':'));
    }

    // ── Non-punctuation never triggers deletion ──────────────────────

    @Test
    public void letter_neverDeletesSpace() {
        assertFalse(shouldDeleteSimple(englishLayout, 'a'));
        assertFalse(shouldDeleteSimple(frenchLayout, 'a'));
    }

    @Test
    public void hyphen_neverDeletesSpace() {
        assertFalse(shouldDeleteSimple(englishLayout, '-'));
        assertFalse(shouldDeleteSimple(frenchLayout, '-'));
    }

    // ── Layout spacedPunctuation attribute ────────────────────────────

    @Test
    public void englishLayout_noSpacedPunctuation() {
        assertNull(englishLayout.getSpacedPunctuation());
    }

    @Test
    public void frenchLayout_hasSpacedPunctuation() {
        assertEquals("!?;:", frenchLayout.getSpacedPunctuation());
    }
}
