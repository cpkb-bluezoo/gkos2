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
 * GKOS layout: id, name, and 63 entries (ref 1–63).
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class Layout {

    private final String id;
    private final String name;
    private final LayoutEntry[] entries; // index 0 = ref 1, ..., index 62 = ref 63

    public Layout(String id, String name, LayoutEntry[] entries) {
        this.id = id;
        this.name = name;
        this.entries = new LayoutEntry[63];
        if (entries != null) {
            for (LayoutEntry e : entries) {
                if (e != null && e.getRef() >= 1 && e.getRef() <= 63) {
                    this.entries[e.getRef() - 1] = e;
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

    public LayoutEntry getEntry(int ref) {
        if (ref < 1 || ref > 63) {
            return null;
        }
        return entries[ref - 1];
    }
}
