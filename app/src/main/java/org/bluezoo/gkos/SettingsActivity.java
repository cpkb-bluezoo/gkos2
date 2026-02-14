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
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings activity for GKOS keyboard.
 * Shows all available language layouts with a toggle between
 * "GKOS Standard" and "Optimized" variants.
 *
 * @author <a href='mailto:dog@gnu.org'>Chris Burdess</a>
 */
public class SettingsActivity extends Activity {

    static final String PREFS_NAME = "gkos_prefs";
    static final String KEY_PREFERRED_LAYOUT = "preferred_layout";
    static final String KEY_VARIANT_PREFIX = "variant_";



    /** All supported languages: {iso_code, string_resource_name, has_standard} */
    private static final String[][] LANGUAGES = {
        {"en", "English",    "true"},
        {"fr", "French",     "true"},
        {"de", "German",     "true"},
        {"es", "Spanish",    "true"},
        {"pt", "Portuguese", "true"},
        {"it", "Italian",    "true"},
        {"da", "Danish",     "true"},
        {"no", "Norwegian",  "true"},
        {"sv", "Swedish",    "true"},
        {"fi", "Finnish",    "true"},
        {"el", "Greek",      "true"},
        {"ru", "Russian",    "true"},
        {"ko", "Korean",     "true"},
        {"eo", "Esperanto",  "true"},
        {"et", "Estonian",   "true"},
        {"is", "Icelandic",  "true"},
        {"uk", "Ukrainian",  "true"},
    };

    // Light mode
    private static final int COLOR_SELECTED_BG_LIGHT = Color.parseColor("#E3F2FD");
    private static final int COLOR_SELECTED_BORDER_LIGHT = Color.parseColor("#1976D2");
    // Dark mode
    private static final int COLOR_SELECTED_BG_DARK = Color.parseColor("#1A3A5C");
    private static final int COLOR_SELECTED_BORDER_DARK = Color.parseColor("#64B5F6");

    private int colorSelectedBg;
    private int colorSelectedBorder;

    private SharedPreferences prefs;
    /** Maps language ISO code to its row container, for highlight updates. */
    private final Map<String, LinearLayout> languageRows = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setTitle(R.string.settings_title);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Detect dark/light by resolving the actual theme background colour
        boolean dark = isThemeDark();
        colorSelectedBg = dark ? COLOR_SELECTED_BG_DARK : COLOR_SELECTED_BG_LIGHT;
        colorSelectedBorder = dark ? COLOR_SELECTED_BORDER_DARK : COLOR_SELECTED_BORDER_LIGHT;

        LinearLayout container = findViewById(R.id.layouts_container);
        buildLayoutList(container);
    }

    private void buildLayoutList(LinearLayout container) {
        String currentLayout = prefs.getString(KEY_PREFERRED_LAYOUT, "en");
        float density = getResources().getDisplayMetrics().density;
        int pad = (int) (10 * density);
        int cornerRadius = (int) (8 * density);

        for (String[] lang : LANGUAGES) {
            String iso = lang[0];
            String name = lang[1];
            boolean hasStandard = Boolean.parseBoolean(lang[2]);

            // Verify the layout files actually exist in assets
            boolean standardExists = hasStandard && assetExists("layouts/" + iso + "-standard.xml");
            boolean optimizedExists = assetExists("layouts/" + iso + ".xml");

            if (!standardExists && !optimizedExists) {
                continue;  // Skip languages with no layout files
            }

            // Row container for highlight
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(pad, pad, pad, pad);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowParams.setMargins(0, (int) (2 * density), 0, (int) (2 * density));
            row.setLayoutParams(rowParams);
            languageRows.put(iso, row);

            // Language header
            TextView header = new TextView(this);
            header.setText(name);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            header.setTypeface(null, Typeface.BOLD);
            header.setPadding(0, 0, 0, (int) (4 * density));
            row.addView(header);

            String currentVariant = prefs.getString(KEY_VARIANT_PREFIX + iso, "optimized");

            // Variant radio group
            RadioGroup radioGroup = new RadioGroup(this);
            radioGroup.setOrientation(RadioGroup.HORIZONTAL);
            radioGroup.setPadding((int) (8 * density), 0, 0, 0);

            if (standardExists) {
                RadioButton stdBtn = new RadioButton(this);
                stdBtn.setText(R.string.variant_standard);
                stdBtn.setId(View.generateViewId());
                stdBtn.setChecked("standard".equals(currentVariant));
                radioGroup.addView(stdBtn);

                stdBtn.setOnClickListener(v -> selectVariant(iso, name, "standard"));
            }

            if (optimizedExists) {
                RadioButton optBtn = new RadioButton(this);
                optBtn.setText(R.string.variant_optimized);
                optBtn.setId(View.generateViewId());
                optBtn.setChecked("optimized".equals(currentVariant));
                radioGroup.addView(optBtn);

                optBtn.setOnClickListener(v -> selectVariant(iso, name, "optimized"));
            }

            row.addView(radioGroup);

            // Apply background: highlight if this is the active layout
            applyRowBackground(row, iso.equals(currentLayout), cornerRadius);

            container.addView(row);
        }
    }

    private void selectVariant(String iso, String name, String variant) {
        // Save the variant preference for this language
        prefs.edit()
                .putString(KEY_VARIANT_PREFIX + iso, variant)
                .putString(KEY_PREFERRED_LAYOUT, iso)  // Also set as active layout
                .apply();

        // Update row highlights
        float density = getResources().getDisplayMetrics().density;
        int cornerRadius = (int) (8 * density);
        for (Map.Entry<String, LinearLayout> entry : languageRows.entrySet()) {
            applyRowBackground(entry.getValue(), entry.getKey().equals(iso), cornerRadius);
        }

        String variantLabel = "standard".equals(variant)
                ? getString(R.string.variant_standard)
                : getString(R.string.variant_optimized);
        Toast.makeText(this, getString(R.string.layout_selected, name, variantLabel),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Sets the background drawable on a language row.
     * Selected rows get a light blue fill with a blue left border accent.
     * Unselected rows are transparent.
     */
    private void applyRowBackground(LinearLayout row, boolean selected, int cornerRadius) {
        if (selected) {
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(colorSelectedBg);
            bg.setCornerRadius(cornerRadius);
            bg.setStroke((int) (2 * getResources().getDisplayMetrics().density), colorSelectedBorder);
            row.setBackground(bg);
        } else {
            row.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * Determines whether the current theme is dark by resolving
     * {@code android.R.attr.colorBackground} and checking its luminance.
     * More reliable than {@code UI_MODE_NIGHT_MASK} which some themes don't honour.
     */
    private boolean isThemeDark() {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            int bg = tv.data;
            return Color.luminance(bg) < 0.5f;
        }
        return false;
    }

    private boolean assetExists(String path) {
        try {
            AssetManager am = getAssets();
            am.open(path).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
