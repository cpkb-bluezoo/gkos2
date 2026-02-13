/*
 * LayoutEntry.java
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
 * Single entry in a GKOS layout: ref to character/action per mode.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public final class LayoutEntry {

    private final int ref;
    private final String abc;
    private final String abcShift;
    private final String num;
    private final String numShift;
    private final String symb;
    private final String symbShift;

    public LayoutEntry(int ref, String abc, String abcShift, String num, String numShift,
                      String symb, String symbShift) {
        this.ref = ref;
        this.abc = abc;
        this.abcShift = abcShift;
        this.num = num;
        this.numShift = numShift;
        this.symb = symb;
        this.symbShift = symbShift;
    }

    public int getRef() {
        return ref;
    }

    public String getAbc() {
        return abc;
    }

    public String getAbcShift() {
        return abcShift;
    }

    public String getNum() {
        return num;
    }

    public String getNumShift() {
        return numShift;
    }

    public String getSymb() {
        return symb;
    }

    public String getSymbShift() {
        return symbShift;
    }

    public boolean isAction() {
        return isActionValue(abc) || isActionValue(abcShift) || isActionValue(num)
                || isActionValue(numShift) || isActionValue(symb) || isActionValue(symbShift);
    }

    public static boolean isActionValue(String s) {
        return s != null && (s.equals("backspace") || s.equals("enter") || s.equals("space")
                || s.equals("mode_toggle") || s.equals("shift") || s.equals("symb")
                || s.equals("ctrl") || s.equals("alt") || s.equals("tab") || s.equals("esc")
                || s.equals("delete") || s.equals("UpArrow") || s.equals("DownArrow")
                || s.equals("PageUp") || s.equals("PageDown") || s.equals("LeftArrow")
                || s.equals("WordLeft") || s.equals("Home") || s.equals("RightArrow")
                || s.equals("WordRight") || s.equals("End") || s.equals("Insert")
                || s.equals("ScrollUp") || s.equals("ScrollDown") || s.equals("PanLeft")
                || s.equals("PanLeftHome") || s.equals("PanRight") || s.equals("PanRightEnd"));
    }
}
