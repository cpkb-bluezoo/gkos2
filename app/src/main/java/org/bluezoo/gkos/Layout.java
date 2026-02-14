/*
 * Layout.java
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

/**
 * GKOS layout: id, name, and entries indexed by chord bitmask (1-63).
 * <p>
 * The entries array is indexed directly by the chord bitmask, so lookup
 * is a simple array access with no intermediate ref conversion needed.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class Layout {

    private final String id;
    private final String name;
    private final LayoutEntry[] entries; // index 0 unused, index 1-63 = combo bitmask

    public Layout(String id, String name, LayoutEntry[] entries) {
        this.id = id;
        this.name = name;
        this.entries = new LayoutEntry[64]; // indices 0-63, 0 unused
        if (entries != null) {
            for (LayoutEntry e : entries) {
                if (e != null && e.getChord() >= 1 && e.getChord() <= 63) {
                    this.entries[e.getChord()] = e;
                }
            }
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Look up an entry by chord bitmask (1-63).
     * Returns null if the combo is invalid or has no entry.
     */
    public LayoutEntry getEntry(int combo) {
        if (combo < 1 || combo > 63) {
            return null;
        }
        return entries[combo];
    }
}
