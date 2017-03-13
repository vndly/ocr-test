/*
 * Copyright (C) 2008 ZXing authors
 * Copyright 2011 Robert Theis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.mauriciotogneri.ocrtest;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

/**
 * Class to handle preferences that are saved across sessions of the app. Shows
 * a hierarchy of preferences to the user, organized into sections. These
 * preferences are displayed in the options menu that is shown when the user
 * presses the MENU button.
 * <p>
 * The code for this class was adapted from the ZXing project: http://code.google.com/p/zxing
 */
public class PreferencesActivity extends PreferenceActivity
{

    // Preference keys not carried over from ZXing project
    public static final String KEY_SOURCE_LANGUAGE_PREFERENCE = "sourceLanguageCodeOcrPref";
    public static final String KEY_TOGGLE_TRANSLATION = "preference_translation_toggle_translation";
    public static final String KEY_CONTINUOUS_PREVIEW = "preference_capture_continuous";
    public static final String KEY_PAGE_SEGMENTATION_MODE = "preference_page_segmentation_mode";
    public static final String KEY_OCR_ENGINE_MODE = "preference_ocr_engine_mode";
    public static final String KEY_TOGGLE_LIGHT = "preference_toggle_light";
    public static final String KEY_TRANSLATOR = "preference_translator";

    // Preference keys carried over from ZXing project
    public static final String KEY_AUTO_FOCUS = "preferences_auto_focus";
    public static final String KEY_DISABLE_CONTINUOUS_FOCUS = "preferences_disable_continuous_focus";
    public static final String KEY_HELP_VERSION_SHOWN = "preferences_help_version_shown";
    public static final String KEY_NOT_OUR_RESULTS_SHOWN = "preferences_not_our_results_shown";
    public static final String KEY_REVERSE_IMAGE = "preferences_reverse_image";
    public static final String KEY_VIBRATE = "preferences_vibrate";

    public static final String TRANSLATOR_BING = "Bing Translator";
    public static final String TRANSLATOR_GOOGLE = "Google Translate";

    private ListPreference listPreferenceOcrEngineMode;
    private ListPreference listPreferencePageSegmentationMode;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        listPreferenceOcrEngineMode = (ListPreference) getPreferenceScreen().findPreference(KEY_OCR_ENGINE_MODE);
        listPreferencePageSegmentationMode = (ListPreference) getPreferenceScreen().findPreference(KEY_PAGE_SEGMENTATION_MODE);
    }

    /**
     * Interface definition for a callback to be invoked when a shared
     * preference is changed. Sets summary text for the app's preferences. Summary text values show the
     * current settings for the values.
     *
     * @param sharedPreferences the Android.content.SharedPreferences that received the change
     * @param key               the key of the preference that was changed, added, or removed
     */
    //    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
    //                                          String key)
    //    {
    //        if (key.equals(KEY_PAGE_SEGMENTATION_MODE))
    //        {
    //            listPreferencePageSegmentationMode.setSummary(sharedPreferences.getString(key, CaptureActivity.DEFAULT_PAGE_SEGMENTATION_MODE));
    //        }
    //        else if (key.equals(KEY_OCR_ENGINE_MODE))
    //        {
    //            listPreferenceOcrEngineMode.setSummary(sharedPreferences.getString(key, CaptureActivity.DEFAULT_OCR_ENGINE_MODE));
    //        }
    //    }

    /**
     * Sets up initial preference summary text
     * values and registers the OnSharedPreferenceChangeListener.
     */
    @Override
    protected void onResume()
    {
        super.onResume();
        // Set up a listener whenever a key changes
        //getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    /**
     * Called when Activity is about to lose focus. Unregisters the
     * OnSharedPreferenceChangeListener.
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        //getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}