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



    /**
     * All supported languages.
     * Columns: iso_code, native_name, has_standard, standard_label, optimized_label
     */
    private static final String[][] LANGUAGES = {
        {"en", "English",     "true",  "GKOS Standard",  "Optimized"},
        {"fr", "Fran\u00e7ais",  "true",  "GKOS Standard",  "Optimis\u00e9"},
        {"de", "Deutsch",     "true",  "GKOS Standard",  "Optimiert"},
        {"nl", "Nederlands",  "false", "",                "Geoptimaliseerd"},
        {"es", "Espa\u00f1ol",   "true",  "GKOS Est\u00e1ndar", "Optimizado"},
        {"pt", "Portugu\u00eas", "true",  "GKOS Padr\u00e3o",   "Otimizado"},
        {"it", "Italiano",    "true",  "GKOS Standard",  "Ottimizzato"},
        {"da", "Dansk",       "true",  "GKOS Standard",  "Optimeret"},
        {"no", "Norsk",       "true",  "GKOS Standard",  "Optimalisert"},
        {"sv", "Svenska",     "true",  "GKOS Standard",  "Optimerad"},
        {"fi", "Suomi",       "true",  "GKOS Standardi", "Optimoitu"},
        {"el", "\u0395\u03bb\u03bb\u03b7\u03bd\u03b9\u03ba\u03ac", "true",  "GKOS \u03a4\u03c5\u03c0\u03b9\u03ba\u03cc",        "\u0392\u03b5\u03bb\u03c4\u03b9\u03c3\u03c4\u03bf\u03c0\u03bf\u03b9\u03b7\u03bc\u03ad\u03bd\u03bf"},
        {"ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439",       "true",  "GKOS \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442", "\u041e\u043f\u0442\u0438\u043c\u0438\u0437\u0438\u0440\u043e\u0432\u0430\u043d\u043d\u0430\u044f"},
        {"ko", "\ud55c\uad6d\uc5b4",   "true",  "GKOS \ud45c\uc900", "\ucd5c\uc801\ud654"},
        {"eo", "Esperanto",   "true",  "GKOS Norma",     "Optimumigita"},
        {"et", "Eesti",       "true",  "GKOS Standard",  "Optimeeritud"},
        {"is", "\u00cdslenska",  "true",  "GKOS Sta\u00f0la\u00f0", "Hagn\u00fdt"},
        {"uk", "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430", "true", "GKOS \u0421\u0442\u0430\u043d\u0434\u0430\u0440\u0442", "\u041e\u043f\u0442\u0438\u043c\u0456\u0437\u043e\u0432\u0430\u043d\u0430"},
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
            String standardLabel = lang[3];
            String optimizedLabel = lang[4];

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
                stdBtn.setText(standardLabel);
                stdBtn.setId(View.generateViewId());
                stdBtn.setChecked("standard".equals(currentVariant));
                radioGroup.addView(stdBtn);

                final String stdLbl = standardLabel;
                final String optLbl = optimizedLabel;
                stdBtn.setOnClickListener(v -> selectVariant(iso, name, "standard", stdLbl, optLbl));
            }

            if (optimizedExists) {
                RadioButton optBtn = new RadioButton(this);
                optBtn.setText(optimizedLabel);
                optBtn.setId(View.generateViewId());
                optBtn.setChecked("optimized".equals(currentVariant));
                radioGroup.addView(optBtn);

                final String stdLbl = standardLabel;
                final String optLbl = optimizedLabel;
                optBtn.setOnClickListener(v -> selectVariant(iso, name, "optimized", stdLbl, optLbl));
            }

            row.addView(radioGroup);

            // Apply background: highlight if this is the active layout
            applyRowBackground(row, iso.equals(currentLayout), cornerRadius);

            container.addView(row);
        }
    }

    private void selectVariant(String iso, String name, String variant,
                               String standardLabel, String optimizedLabel) {
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

        String variantLabel = "standard".equals(variant) ? standardLabel : optimizedLabel;
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
