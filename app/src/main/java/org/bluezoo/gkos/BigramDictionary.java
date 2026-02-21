/*
 * BigramDictionary.java
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Bundled bigram dictionary loaded from {@code assets/bigrams/{lang}.gz}.
 * Provides baseline word-pair frequency data so that contextually
 * appropriate suggestions appear even before the user has typed enough
 * to build up {@link UserBigrams} data.
 * <p>
 * File format (gzip-compressed text):
 * {@code contextWord\tfollower1,follower2,...,followerN} per line.
 * Followers are in corpus frequency order (most frequent first);
 * counts are omitted since only ordering matters.
 * <p>
 * Loading is performed on a background thread (same pattern as
 * {@link WordDictionary}).  The dictionary returns empty results until
 * loading completes.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class BigramDictionary {

    private static final String TAG = "BigramDictionary";

    /** context word (lowercase) -> followers in frequency order. */
    private volatile Map<String, List<String>> entries = Collections.emptyMap();
    private volatile boolean loaded = false;

    /**
     * Loads bigram data for the given language from assets in a background
     * thread.  If no bigram file exists for the language, the dictionary
     * remains empty (returns no results).
     */
    public void loadAsync(Context context, String langId) {
        loaded = false;
        final AssetManager am = context.getAssets();
        final String path = "bigrams/" + langId + ".gz";
        new Thread(() -> {
            Map<String, List<String>> result = new HashMap<>();
            try (InputStream is = am.open(path);
                 GZIPInputStream gis = new GZIPInputStream(is);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(gis, "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int sep = line.indexOf('\t');
                    if (sep <= 0 || sep >= line.length() - 1) continue;
                    String ctxWord = line.substring(0, sep);
                    String[] followers = line.substring(sep + 1).split(",");
                    result.put(ctxWord, Arrays.asList(followers));
                }
            } catch (IOException e) {
                Log.i(TAG, "No bundled bigrams for " + langId + " (this is normal)");
            }
            entries = result;
            loaded = true;
            int total = 0;
            for (List<String> v : result.values()) total += v.size();
            Log.i(TAG, "Loaded " + total + " bundled bigrams for " + langId);
        }, "BigramDict-" + langId).start();
    }

    /**
     * Returns whether the dictionary has finished loading.
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Returns words that follow {@code contextWord} and whose lowercase
     * form starts with {@code prefix}, in corpus frequency order.
     *
     * @param contextWord the preceding word (looked up case-insensitively)
     * @param prefix      lowercase prefix to match against followers
     * @param maxResults  maximum number of results
     * @return matching words, most frequent first (may be empty, never null)
     */
    public List<String> getFollowers(String contextWord, String prefix, int maxResults) {
        if (contextWord == null || prefix == null || prefix.isEmpty() || maxResults <= 0) {
            return Collections.emptyList();
        }
        String lowerContext = contextWord.toLowerCase();
        List<String> followers = entries.get(lowerContext);
        if (followers == null) return Collections.emptyList();

        String lowerPrefix = prefix.toLowerCase();
        List<String> result = new ArrayList<>(maxResults);
        for (String word : followers) {
            if (word.startsWith(lowerPrefix) && !word.equals(lowerPrefix)) {
                result.add(word);
                if (result.size() >= maxResults) break;
            }
        }
        return result;
    }
}
