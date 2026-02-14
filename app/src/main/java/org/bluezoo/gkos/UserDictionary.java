/*
 * UserDictionary.java
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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User dictionary that tracks words the user types.
 * Words are persisted to a plain text file in the app's private storage
 * ({@code user_dict_{lang}.txt}).  The file format is {@code word\tcount}
 * per line.
 * <p>
 * Saving is debounced: changes are written to disk at most every
 * {@link #SAVE_DELAY_MS} milliseconds, and always on {@link #close()}.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class UserDictionary {

    private static final String TAG = "UserDictionary";
    private static final long SAVE_DELAY_MS = 30_000;  // 30 seconds
    private static final int MIN_WORD_LENGTH = 2;

    private final Map<String, Integer> entries = new HashMap<>();
    private File file;
    private boolean dirty = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::saveToDisk;

    /**
     * Loads the user dictionary for the given language.
     *
     * @param context application context
     * @param langId  ISO language code, e.g. "en"
     */
    public void load(Context context, String langId) {
        handler.removeCallbacks(saveRunnable);
        if (dirty) {
            saveToDisk();
        }
        entries.clear();
        file = new File(context.getFilesDir(), "user_dict_" + langId + ".txt");
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int sep = line.indexOf('\t');
                if (sep > 0) {
                    String word = line.substring(0, sep);
                    try {
                        int count = Integer.parseInt(line.substring(sep + 1).trim());
                        entries.put(word, count);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            Log.i(TAG, "Loaded " + entries.size() + " user words for " + langId);
        } catch (IOException e) {
            Log.w(TAG, "Failed to load user dictionary for " + langId, e);
        }
    }

    /**
     * Records a completed word.  Increments its frequency count and
     * schedules a debounced save.
     *
     * @param word the word (will be lowercased)
     */
    public void recordWord(String word) {
        if (word == null || word.length() < MIN_WORD_LENGTH) return;
        word = word.toLowerCase();
        entries.merge(word, 1, Integer::sum);
        scheduleSave();
    }

    /**
     * Returns up to {@code maxResults} user-dictionary words that start
     * with the given prefix, ordered by frequency (most frequent first).
     *
     * @param prefix     lowercase prefix to match
     * @param maxResults maximum number of results
     * @return list of matching words (may be empty, never null)
     */
    public List<String> getMatches(String prefix, int maxResults) {
        if (prefix == null || prefix.isEmpty() || maxResults <= 0) {
            return Collections.emptyList();
        }
        List<Map.Entry<String, Integer>> matches = new ArrayList<>();
        for (Map.Entry<String, Integer> e : entries.entrySet()) {
            if (e.getKey().startsWith(prefix) && !e.getKey().equals(prefix)) {
                matches.add(e);
            }
        }
        // Sort by frequency descending
        matches.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed());
        List<String> result = new ArrayList<>(Math.min(maxResults, matches.size()));
        for (int i = 0; i < Math.min(maxResults, matches.size()); i++) {
            result.add(matches.get(i).getKey());
        }
        return result;
    }

    /**
     * Flushes any pending changes to disk and cancels scheduled saves.
     * Call from {@code onDestroy()}.
     */
    public void close() {
        handler.removeCallbacks(saveRunnable);
        if (dirty) {
            saveToDisk();
        }
    }

    private void scheduleSave() {
        dirty = true;
        handler.removeCallbacks(saveRunnable);
        handler.postDelayed(saveRunnable, SAVE_DELAY_MS);
    }

    private void saveToDisk() {
        if (file == null) return;
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            for (Map.Entry<String, Integer> e : entries.entrySet()) {
                writer.write(e.getKey());
                writer.write('\t');
                writer.write(Integer.toString(e.getValue()));
                writer.newLine();
            }
            dirty = false;
            Log.i(TAG, "Saved " + entries.size() + " user words");
        } catch (IOException e) {
            Log.w(TAG, "Failed to save user dictionary", e);
        }
    }
}
