/*
 * UserBigrams.java
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
 * Tracks word-pair (bigram) frequencies from user typing.  For each
 * context word (the word just finished), records which words follow it
 * and how often.  This data is used to reorder word suggestions so
 * that contextually probable completions appear first.
 * <p>
 * Persisted to {@code user_bigrams_{lang}.txt} in the app's private
 * storage.  File format: {@code contextWord\tfollowWord\tcount} per line.
 * <p>
 * Context words are stored in canonical case (proper nouns capitalised,
 * common words lowercase) matching the {@link UserDictionary} convention.
 * Lookups are case-insensitive.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class UserBigrams {

    private static final String TAG = "UserBigrams";
    private static final long SAVE_DELAY_MS = 30_000;

    /** context word -> (follow word -> count). */
    private final Map<String, Map<String, Integer>> entries = new HashMap<>();
    private File file;
    private boolean dirty = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable saveRunnable = this::saveToDisk;

    /**
     * Loads user bigrams for the given language.
     */
    public void load(Context context, String langId) {
        handler.removeCallbacks(saveRunnable);
        if (dirty) {
            saveToDisk();
        }
        entries.clear();
        file = new File(context.getFilesDir(), "user_bigrams_" + langId + ".txt");
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int sep1 = line.indexOf('\t');
                if (sep1 <= 0) continue;
                int sep2 = line.indexOf('\t', sep1 + 1);
                if (sep2 <= sep1 + 1) continue;
                String ctxWord = line.substring(0, sep1);
                String follow = line.substring(sep1 + 1, sep2);
                try {
                    int count = Integer.parseInt(line.substring(sep2 + 1).trim());
                    entries.computeIfAbsent(ctxWord, k -> new HashMap<>())
                           .put(follow, count);
                } catch (NumberFormatException ignored) {
                }
            }
            int total = 0;
            for (Map<String, Integer> m : entries.values()) total += m.size();
            Log.i(TAG, "Loaded " + total + " user bigrams for " + langId);
        } catch (IOException e) {
            Log.w(TAG, "Failed to load user bigrams for " + langId, e);
        }
    }

    /**
     * Records that {@code followWord} was typed after {@code contextWord}.
     * Both words should be in canonical case (proper nouns capitalised,
     * common words lowercase).
     */
    public void recordBigram(String contextWord, String followWord) {
        if (contextWord == null || followWord == null
                || contextWord.isEmpty() || followWord.length() < 2) {
            return;
        }
        // Case-insensitive lookup for the context key
        String contextKey = findKey(contextWord);
        if (contextKey != null) {
            if (!contextKey.equals(contextWord)
                    && Character.isUpperCase(contextWord.charAt(0))
                    && Character.isLowerCase(contextKey.charAt(0))) {
                // Upgrade context key to proper noun form
                Map<String, Integer> followers = entries.remove(contextKey);
                entries.put(contextWord, followers);
                contextKey = contextWord;
            }
        } else {
            contextKey = contextWord;
            entries.put(contextKey, new HashMap<>());
        }

        Map<String, Integer> followers = entries.get(contextKey);

        // Case-insensitive lookup for the follow word
        String followKey = findFollowKey(followers, followWord);
        if (followKey != null) {
            if (!followKey.equals(followWord)
                    && Character.isUpperCase(followWord.charAt(0))
                    && Character.isLowerCase(followKey.charAt(0))) {
                int count = followers.remove(followKey);
                followers.put(followWord, count + 1);
            } else {
                followers.merge(followKey, 1, Integer::sum);
            }
        } else {
            followers.put(followWord, 1);
        }
        scheduleSave();
    }

    /**
     * Returns words that have followed {@code contextWord} and whose
     * lowercase form starts with {@code prefix}, ordered by frequency.
     *
     * @param contextWord the preceding word (looked up case-insensitively)
     * @param prefix      lowercase prefix to match against followers
     * @param maxResults  maximum number of results
     * @return matching follower words in stored case, most frequent first
     */
    public List<String> getFollowers(String contextWord, String prefix, int maxResults) {
        if (contextWord == null || prefix == null || prefix.isEmpty() || maxResults <= 0) {
            return Collections.emptyList();
        }
        String contextKey = findKey(contextWord);
        if (contextKey == null) return Collections.emptyList();

        Map<String, Integer> followers = entries.get(contextKey);
        if (followers == null || followers.isEmpty()) return Collections.emptyList();

        String lowerPrefix = prefix.toLowerCase();
        List<Map.Entry<String, Integer>> matches = new ArrayList<>();
        for (Map.Entry<String, Integer> e : followers.entrySet()) {
            String keyLower = e.getKey().toLowerCase();
            if (keyLower.startsWith(lowerPrefix) && !keyLower.equals(lowerPrefix)) {
                matches.add(e);
            }
        }
        matches.sort(Comparator.<Map.Entry<String, Integer>>comparingInt(
                Map.Entry::getValue).reversed());

        List<String> result = new ArrayList<>(Math.min(maxResults, matches.size()));
        for (int i = 0; i < Math.min(maxResults, matches.size()); i++) {
            result.add(matches.get(i).getKey());
        }
        return result;
    }

    /**
     * Flushes pending changes and cancels scheduled saves.
     */
    public void close() {
        handler.removeCallbacks(saveRunnable);
        if (dirty) {
            saveToDisk();
        }
    }

    /** Finds the stored key matching {@code word} case-insensitively. */
    private String findKey(String word) {
        String lower = word.toLowerCase();
        for (String key : entries.keySet()) {
            if (key.toLowerCase().equals(lower)) return key;
        }
        return null;
    }

    /** Finds the stored follower key matching {@code word} case-insensitively. */
    private static String findFollowKey(Map<String, Integer> followers, String word) {
        String lower = word.toLowerCase();
        for (String key : followers.keySet()) {
            if (key.toLowerCase().equals(lower)) return key;
        }
        return null;
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
            for (Map.Entry<String, Map<String, Integer>> ctx : entries.entrySet()) {
                String contextWord = ctx.getKey();
                for (Map.Entry<String, Integer> f : ctx.getValue().entrySet()) {
                    writer.write(contextWord);
                    writer.write('\t');
                    writer.write(f.getKey());
                    writer.write('\t');
                    writer.write(Integer.toString(f.getValue()));
                    writer.newLine();
                }
            }
            dirty = false;
            int total = 0;
            for (Map<String, Integer> m : entries.values()) total += m.size();
            Log.i(TAG, "Saved " + total + " user bigrams");
        } catch (IOException e) {
            Log.w(TAG, "Failed to save user bigrams", e);
        }
    }
}
