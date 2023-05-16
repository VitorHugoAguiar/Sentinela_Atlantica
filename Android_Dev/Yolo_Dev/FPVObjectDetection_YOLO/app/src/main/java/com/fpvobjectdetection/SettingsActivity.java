package com.fpvobjectdetection;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            this.finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            CheckBoxPreference modelN = findPreference("ModelN");
            CheckBoxPreference modelS = findPreference("ModelS");
            CheckBoxPreference modelM = findPreference("ModelM");

            Preference.OnPreferenceChangeListener modelListener = (preference, newValue) -> {
                if ((Boolean) newValue) {
                    CheckBoxPreference changedPreference = (CheckBoxPreference) preference;

                    if (changedPreference == modelN) {
                        modelS.setChecked(false);
                        modelM.setChecked(false);
                    } else if (changedPreference == modelS) {
                        modelN.setChecked(false);
                        modelM.setChecked(false);
                    } else if (changedPreference == modelM) {
                        modelN.setChecked(false);
                        modelS.setChecked(false);
                    }
                }
                return true;
            };

            modelN.setOnPreferenceChangeListener(modelListener);
            modelS.setOnPreferenceChangeListener(modelListener);
            modelM.setOnPreferenceChangeListener(modelListener);

        }
    }
}