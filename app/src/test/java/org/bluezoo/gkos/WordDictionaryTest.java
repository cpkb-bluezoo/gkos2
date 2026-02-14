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
 * Tests for {@link WordDictionary}: loading and prefix suggestion.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public class WordDictionaryTest {

    private WordDictionary dict;
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        dict = new WordDictionary();
        // Load synchronously by calling loadAsync and waiting
        dict.loadAsync(context, "en");
        // Wait for background thread to finish (up to 10 seconds)
        long deadline = System.currentTimeMillis() + 10_000;
        while (!dict.isLoaded() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("Dictionary did not load in time", dict.isLoaded());
    }

    @Test
    public void getSuggestions_nullPrefix_returnsEmpty() {
        List<String> results = dict.getSuggestions(null, 3);
        assertTrue(results.isEmpty());
    }

    @Test
    public void getSuggestions_emptyPrefix_returnsEmpty() {
        List<String> results = dict.getSuggestions("", 3);
        assertTrue(results.isEmpty());
    }

    @Test
    public void getSuggestions_validPrefix_returnsMatches() {
        List<String> results = dict.getSuggestions("th", 3);
        assertFalse("Expected suggestions for 'th'", results.isEmpty());
        for (String word : results) {
            assertTrue("'" + word + "' should start with 'th'", word.startsWith("th"));
        }
    }

    @Test
    public void getSuggestions_commonPrefix_returnsFrequentWordsFirst() {
        // "the" is the most common English word starting with "th"
        List<String> results = dict.getSuggestions("th", 5);
        assertFalse(results.isEmpty());
        // "the" should be among the top results (it's the #1 word starting with "th")
        assertTrue("Expected 'the' in top results for 'th'",
                results.contains("the"));
    }

    @Test
    public void getSuggestions_respectsMaxResults() {
        List<String> results = dict.getSuggestions("a", 2);
        assertTrue("Expected at most 2 results", results.size() <= 2);
    }

    @Test
    public void getSuggestions_noMatch_returnsEmpty() {
        List<String> results = dict.getSuggestions("zzzzqqqq", 3);
        assertTrue(results.isEmpty());
    }

    @Test
    public void getSuggestions_excludesExactMatch() {
        // "the" is a word; suggestions for prefix "the" should not include "the" itself
        List<String> results = dict.getSuggestions("the", 10);
        assertFalse("'the' itself should be excluded from suggestions",
                results.contains("the"));
    }

    @Test
    public void getSuggestions_zeroMaxResults_returnsEmpty() {
        List<String> results = dict.getSuggestions("th", 0);
        assertTrue(results.isEmpty());
    }

    @Test
    public void loadAsync_nonExistentLanguage_producesEmptyDict() throws Exception {
        WordDictionary empty = new WordDictionary();
        empty.loadAsync(context, "xx_nonexistent");
        long deadline = System.currentTimeMillis() + 3_000;
        while (!empty.isLoaded() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        // Should load (with warning) but produce empty results
        List<String> results = empty.getSuggestions("a", 3);
        assertTrue(results.isEmpty());
    }
}
