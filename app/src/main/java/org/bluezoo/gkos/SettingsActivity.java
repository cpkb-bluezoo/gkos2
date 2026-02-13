/*
 * SettingsActivity.java
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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.bluezoo.gkos.R;

/**
 * Settings activity for GKOS keyboard.
 * Launched from the gear icon when the keyboard is selected in system settings.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class SettingsActivity extends Activity {

    private static final String PREFS_NAME = "gkos_prefs";
    private static final String KEY_PREFERRED_LAYOUT = "preferred_layout";

    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        setupLayoutClicks();
    }

    private void setupLayoutClicks() {
        setLayoutClick(R.id.layout_en_us, "en-US");
        setLayoutClick(R.id.layout_en_alphabetic, "en-Alphabetic");
        setLayoutClick(R.id.layout_de, "de");
        setLayoutClick(R.id.layout_fi, "fi");
        setLayoutClick(R.id.layout_ru, "ru");
        setLayoutClick(R.id.layout_el, "el");
        setLayoutClick(R.id.layout_ko, "ko");
    }

    private void setLayoutClick(int viewId, String layoutId) {
        TextView view = findViewById(viewId);
        view.setOnClickListener(v -> {
            prefs.edit().putString(KEY_PREFERRED_LAYOUT, layoutId).apply();
            Toast.makeText(this, getString(R.string.layout_selected, view.getText()), Toast.LENGTH_SHORT).show();
        });
    }
}
