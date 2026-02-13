/*
 * LayoutEngine.java
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

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads GKOS layouts from XML and resolves chord to character/action.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class LayoutEngine {

    /** Current mode: ABC (letters) or 123 (numbers). */
    public enum Mode {
        ABC,
        NUM
    }

    private Layout layout;
    private Mode mode = Mode.ABC;
    private boolean shift;
    private boolean symb;

    /**
     * Result of resolving a chord: either text to commit or an action.
     */
    public static final class ResolveResult {
        public final String text;
        public final String action;

        private ResolveResult(String text, String action) {
            this.text = text;
            this.action = action;
        }

        public static ResolveResult text(String s) {
            return new ResolveResult(s, null);
        }

        public static ResolveResult action(String a) {
            return new ResolveResult(null, a);
        }

        public boolean isAction() {
            return action != null;
        }
    }

    public void setLayout(Layout layout) {
        this.layout = layout;
    }

    public Layout getLayout() {
        return layout;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public void setShift(boolean shift) {
        this.shift = shift;
    }

    public boolean getShift() {
        return shift;
    }

    public void setSymb(boolean symb) {
        this.symb = symb;
    }

    public boolean getSymb() {
        return symb;
    }

    /**
     * Resolves chord bitmask to character or action.
     * Returns null if chord is invalid or has no mapping.
     */
    public ResolveResult resolve(int chordBitmask) {
        int ref = ChordComboTable.chordToRef(chordBitmask);
        if (ref == 0 || layout == null) {
            return null;
        }
        LayoutEntry entry = layout.getEntry(ref);
        if (entry == null) {
            return null;
        }
        String value = pickValue(entry);
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (LayoutEntry.isActionValue(value)) {
            return ResolveResult.action(value);
        }
        return ResolveResult.text(value);
    }

    private String pickValue(LayoutEntry e) {
        if (symb) {
            if (shift && e.getSymbShift() != null) return e.getSymbShift();
            if (e.getSymb() != null) return e.getSymb();
        }
        if (mode == Mode.ABC) {
            if (shift && e.getAbcShift() != null) return e.getAbcShift();
            return e.getAbc();
        }
        if (mode == Mode.NUM) {
            if (shift && e.getNumShift() != null) return e.getNumShift();
            return e.getNum();
        }
        return null;
    }

    /**
     * Loads layout from assets/layouts/{filename}.
     */
    public Layout loadFromAssets(Context context, String filename) throws IOException, XmlPullParserException {
        AssetManager am = context.getAssets();
        try (InputStream is = am.open("layouts/" + filename)) {
            return parseLayout(is);
        }
    }

    /**
     * Loads layout from app storage (for custom layouts).
     */
    public Layout loadFromFile(File file) throws IOException, XmlPullParserException {
        try (FileInputStream is = new FileInputStream(file)) {
            return parseLayout(is);
        }
    }

    private Layout parseLayout(InputStream is) throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        String id = null;
        String name = null;
        List<LayoutEntry> entries = new ArrayList<>();

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("layout".equals(tag)) {
                    id = parser.getAttributeValue(null, "id");
                    name = parser.getAttributeValue(null, "name");
                } else if ("entry".equals(tag)) {
                    int ref = parseInt(parser.getAttributeValue(null, "ref"), 0);
                    if (ref >= 1 && ref <= 63) {
                        String abc = parser.getAttributeValue(null, "abc");
                        String abcShift = parser.getAttributeValue(null, "abc_shift");
                        String num = parser.getAttributeValue(null, "num");
                        String numShift = parser.getAttributeValue(null, "num_shift");
                        String symb = parser.getAttributeValue(null, "symb");
                        String symbShift = parser.getAttributeValue(null, "symb_shift");
                        entries.add(new LayoutEntry(ref, abc, abcShift, num, numShift, symb, symbShift));
                    }
                }
            }
            event = parser.next();
        }

        return new Layout(id != null ? id : "unknown", name != null ? name : "Unknown", 
                entries.toArray(new LayoutEntry[0]));
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isEmpty()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
