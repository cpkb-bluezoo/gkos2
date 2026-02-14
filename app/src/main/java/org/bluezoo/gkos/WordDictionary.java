/*
 * WordDictionary.java
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bundled word dictionary loaded from assets/wordlists/{lang}.txt.
 * Words are stored sorted by frequency (most frequent first) so that
 * a simple linear scan for prefix matches yields the top results by
 * frequency without any additional sorting.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class WordDictionary {

    private static final String TAG = "WordDictionary";

    /** Words sorted by descending frequency. */
    private volatile List<String> words = Collections.emptyList();

    private volatile boolean loaded = false;

    /**
     * Loads the word list for the given language from assets in a background
     * thread. The dictionary is usable (returns empty results) immediately;
     * suggestions begin appearing once loading completes.
     *
     * @param context application context
     * @param langId  ISO language code, e.g. "en"
     */
    public void loadAsync(Context context, String langId) {
        loaded = false;
        final AssetManager am = context.getAssets();
        final String path = "wordlists/" + langId + ".txt";
        new Thread(() -> {
            List<String> result = new ArrayList<>();
            try (InputStream is = am.open(path);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Format: "word frequency"
                    int sep = line.indexOf(' ');
                    if (sep > 0) {
                        result.add(line.substring(0, sep).toLowerCase());
                    }
                }
            } catch (IOException e) {
                Log.w(TAG, "Failed to load word list for " + langId, e);
            }
            words = result;
            loaded = true;
            Log.i(TAG, "Loaded " + result.size() + " words for " + langId);
        }, "WordDict-" + langId).start();
    }

    /**
     * Returns whether the dictionary has finished loading.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns up to {@code maxResults} words that start with the given prefix,
     * ordered by frequency (most frequent first).
     *
     * @param prefix     the lowercase prefix to match
     * @param maxResults maximum number of suggestions to return
     * @return list of matching words (may be empty, never null)
     */
    public List<String> getSuggestions(String prefix, int maxResults) {
        if (prefix == null || prefix.isEmpty() || maxResults <= 0) {
            return Collections.emptyList();
        }
        List<String> current = words;  // snapshot
        List<String> result = new ArrayList<>(maxResults);
        for (String word : current) {
            if (word.startsWith(prefix) && !word.equals(prefix)) {
                result.add(word);
                if (result.size() >= maxResults) break;
            }
        }
        return result;
    }
}
