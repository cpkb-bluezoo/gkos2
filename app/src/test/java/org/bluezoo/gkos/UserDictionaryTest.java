package org.bluezoo.gkos;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link UserDictionary}: recording, matching, frequency, persistence.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class UserDictionaryTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private UserDictionary freshDictionary(String langId) {
        UserDictionary dict = new UserDictionary();
        dict.load(context, langId);
        return dict;
    }

    @Test
    public void recordWord_thenGetMatches() {
        UserDictionary dict = freshDictionary("test_basic");
        dict.recordWord("hello");
        dict.recordWord("help");

        List<String> matches = dict.getMatches("hel", 5);
        assertEquals(2, matches.size());
        assertTrue(matches.contains("hello"));
        assertTrue(matches.contains("help"));
    }

    @Test
    public void getMatches_excludesExactPrefix() {
        UserDictionary dict = freshDictionary("test_exact");
        dict.recordWord("test");
        dict.recordWord("testing");

        List<String> matches = dict.getMatches("test", 5);
        assertFalse("Exact prefix 'test' should be excluded", matches.contains("test"));
        assertTrue(matches.contains("testing"));
    }

    @Test
    public void frequencyOrdering_higherCountFirst() {
        UserDictionary dict = freshDictionary("test_freq");
        dict.recordWord("apple");
        dict.recordWord("application");
        // Record "apple" 4 more times (total 5)
        for (int i = 0; i < 4; i++) {
            dict.recordWord("apple");
        }

        List<String> matches = dict.getMatches("app", 5);
        assertTrue(matches.size() >= 2);
        // "apple" (count=5) should appear before "application" (count=1)
        int appleIdx = matches.indexOf("apple");
        int appIdx = matches.indexOf("application");
        assertTrue("apple should rank before application",
                appleIdx >= 0 && appIdx >= 0 && appleIdx < appIdx);
    }

    @Test
    public void recordWord_shortWord_ignored() {
        UserDictionary dict = freshDictionary("test_short");
        dict.recordWord("a");  // single char, should be ignored
        dict.recordWord("I");  // single char

        List<String> matches = dict.getMatches("a", 5);
        assertTrue("Single-char words should not be recorded", matches.isEmpty());
    }

    @Test
    public void recordWord_nullWord_ignored() {
        UserDictionary dict = freshDictionary("test_null");
        dict.recordWord(null);
        // No crash, no entries
        List<String> matches = dict.getMatches("", 5);
        assertTrue(matches.isEmpty());
    }

    @Test
    public void getMatches_nullPrefix_returnsEmpty() {
        UserDictionary dict = freshDictionary("test_nullpfx");
        dict.recordWord("hello");
        assertTrue(dict.getMatches(null, 5).isEmpty());
    }

    @Test
    public void getMatches_emptyPrefix_returnsEmpty() {
        UserDictionary dict = freshDictionary("test_emptypfx");
        dict.recordWord("hello");
        assertTrue(dict.getMatches("", 5).isEmpty());
    }

    @Test
    public void getMatches_respectsMaxResults() {
        UserDictionary dict = freshDictionary("test_max");
        dict.recordWord("test1x");
        dict.recordWord("test2x");
        dict.recordWord("test3x");
        dict.recordWord("test4x");

        List<String> matches = dict.getMatches("test", 2);
        assertEquals(2, matches.size());
    }

    @Test
    public void getMatches_noMatch_returnsEmpty() {
        UserDictionary dict = freshDictionary("test_nomatch");
        dict.recordWord("hello");
        assertTrue(dict.getMatches("xyz", 5).isEmpty());
    }

    @Test
    public void persistence_roundTrip() {
        String lang = "test_persist";

        // Write some words and close
        UserDictionary dict1 = freshDictionary(lang);
        dict1.recordWord("persistent");
        dict1.recordWord("persistently");
        dict1.close();

        // Load fresh and verify
        UserDictionary dict2 = freshDictionary(lang);
        List<String> matches = dict2.getMatches("persist", 5);
        assertTrue(matches.contains("persistent"));
        assertTrue(matches.contains("persistently"));
        dict2.close();
    }

    @Test
    public void recordWord_lowercasesInput() {
        UserDictionary dict = freshDictionary("test_case");
        dict.recordWord("Hello");
        dict.recordWord("WORLD");

        List<String> matches1 = dict.getMatches("hel", 5);
        assertTrue(matches1.contains("hello"));

        List<String> matches2 = dict.getMatches("wor", 5);
        assertTrue(matches2.contains("world"));
    }
}
