package org.bluezoo.gkos;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for layout XML parsing via {@link LayoutEngine#loadFromAssets(Context, String)}.
 * Parses every layout file and verifies structural invariants.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class LayoutParsingTest {

    private static final List<String> ALL_LAYOUTS = Arrays.asList(
        "en.xml", "en-standard.xml",
        "fr.xml", "fr-standard.xml",
        "de.xml", "de-standard.xml",
        "nl.xml",
        "es.xml", "es-standard.xml",
        "pt.xml", "pt-standard.xml",
        "it.xml", "it-standard.xml",
        "da.xml", "da-standard.xml",
        "no.xml", "no-standard.xml",
        "sv.xml", "sv-standard.xml",
        "fi.xml", "fi-standard.xml",
        "el.xml", "el-standard.xml",
        "ru.xml", "ru-standard.xml",
        "ko.xml", "ko-standard.xml",
        "eo.xml", "eo-standard.xml",
        "et.xml", "et-standard.xml",
        "is.xml", "is-standard.xml",
        "uk.xml", "uk-standard.xml"
    );

    /** Chord bitmasks for refs 31-36 (punctuation). */
    private static final int[] PUNCT_CHORDS = {34, 20, 12, 33, 17, 10};
    /** Chord bitmasks for refs 42-63 (navigation/control). */
    private static final int[] NAV_CHORDS = {
        9, 36, 27, 54, 7, 15, 23, 39, 56, 57, 58, 60,
        59, 61, 31, 62, 43, 18, 45, 63, 47, 55
    };

    private Context context;
    private LayoutEngine engine;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        engine = new LayoutEngine();
    }

    /**
     * Every layout file must parse without exception.
     */
    @Test
    public void allLayouts_parseSuccessfully() throws Exception {
        for (String filename : ALL_LAYOUTS) {
            try {
                Layout layout = engine.loadFromAssets(context, filename);
                assertNotNull("Layout null for " + filename, layout);
            } catch (Exception e) {
                fail("Failed to parse " + filename + ": " + e.getMessage());
            }
        }
    }

    /**
     * Every layout must have a non-null, non-empty id and name.
     */
    @Test
    public void allLayouts_haveIdAndName() throws Exception {
        for (String filename : ALL_LAYOUTS) {
            Layout layout = engine.loadFromAssets(context, filename);
            assertNotNull("Id null for " + filename, layout.getId());
            assertFalse("Id empty for " + filename, layout.getId().isEmpty());
            assertNotNull("Name null for " + filename, layout.getName());
            assertFalse("Name empty for " + filename, layout.getName().isEmpty());
        }
    }

    /**
     * Every layout must have all 63 chord entries populated.
     */
    @Test
    public void allLayouts_have63Entries() throws Exception {
        for (String filename : ALL_LAYOUTS) {
            Layout layout = engine.loadFromAssets(context, filename);
            for (int chord = 1; chord <= 63; chord++) {
                assertNotNull("Missing chord " + chord + " in " + filename,
                        layout.getEntry(chord));
            }
        }
    }

    /**
     * Punctuation entries (refs 31-36) must be identical across all layouts.
     * Specifically, the abc and abc_shift values should match.
     */
    @Test
    public void punctuation_identicalAcrossLayouts() throws Exception {
        Layout reference = engine.loadFromAssets(context, "en.xml");
        for (String filename : ALL_LAYOUTS) {
            Layout layout = engine.loadFromAssets(context, filename);
            for (int chord : PUNCT_CHORDS) {
                LayoutEntry refEntry = reference.getEntry(chord);
                LayoutEntry testEntry = layout.getEntry(chord);
                assertNotNull("Missing punct chord " + chord + " in " + filename, testEntry);
                assertEquals("Punct abc mismatch at chord " + chord + " in " + filename,
                        refEntry.getAbc(), testEntry.getAbc());
                assertEquals("Punct abc_shift mismatch at chord " + chord + " in " + filename,
                        refEntry.getAbcShift(), testEntry.getAbcShift());
            }
        }
    }

    /**
     * Navigation/control entries (refs 42-63) must have identical NUM mode
     * values across all layouts.  The ABC mode values may differ in GKOS
     * Standard layouts (some override ref 58 etc. with language-specific
     * characters), but NUM values are universally consistent.
     */
    @Test
    public void navigation_numValues_identicalAcrossLayouts() throws Exception {
        Layout reference = engine.loadFromAssets(context, "en.xml");
        for (String filename : ALL_LAYOUTS) {
            Layout layout = engine.loadFromAssets(context, filename);
            for (int chord : NAV_CHORDS) {
                LayoutEntry refEntry = reference.getEntry(chord);
                LayoutEntry testEntry = layout.getEntry(chord);
                assertNotNull("Missing nav chord " + chord + " in " + filename, testEntry);
                // Check num (not abc, which may vary in standard layouts)
                if (refEntry.getNum() != null) {
                    assertEquals("Nav num mismatch at chord " + chord + " in " + filename,
                            refEntry.getNum(), testEntry.getNum());
                }
            }
        }
    }

    /**
     * Spot-check specific layout entries.
     */
    @Test
    public void spotCheck_english() throws Exception {
        Layout en = engine.loadFromAssets(context, "en.xml");
        assertEquals("en", en.getId());
        // Chord 1 (ref 1) = "o"
        assertEquals("o", en.getEntry(1).getAbc());
        assertEquals("O", en.getEntry(1).getAbcShift());
    }

    @Test
    public void spotCheck_german() throws Exception {
        Layout de = engine.loadFromAssets(context, "de.xml");
        assertEquals("de", de.getId());
        // Chord 16 (ref 5) = "e"
        assertEquals("e", de.getEntry(16).getAbc());
    }

    @Test
    public void spotCheck_dutch() throws Exception {
        Layout nl = engine.loadFromAssets(context, "nl.xml");
        assertEquals("nl", nl.getId());
        // Chord 5 (ref 27) = "ij"
        assertEquals("ij", nl.getEntry(5).getAbc());
        assertEquals("IJ", nl.getEntry(5).getAbcShift());
    }

    /**
     * The space chord (56) must always be an action in all layouts.
     */
    @Test
    public void spaceChord_isAction_allLayouts() throws Exception {
        for (String filename : ALL_LAYOUTS) {
            Layout layout = engine.loadFromAssets(context, filename);
            LayoutEntry space = layout.getEntry(56);
            assertNotNull("Missing space in " + filename, space);
            assertEquals("space", space.getAbc());
        }
    }
}
