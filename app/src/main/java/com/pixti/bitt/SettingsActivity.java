package com.pixti.bitt;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.content.SharedPreferences;

public class SettingsActivity extends AppCompatActivity {
    private Switch soundsSwitch;
    private Switch flashSwitch;
    private SharedPreferences preferences;
    private SharedPreferences.Editor preferencesEditor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        soundsSwitch = findViewById(R.id.switch_sounds);
        flashSwitch = findViewById(R.id.switch_flash);

        preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        preferencesEditor = preferences.edit();

        soundsSwitch.setChecked(preferences.getBoolean("soundsEnabled", true));
        flashSwitch.setChecked(preferences.getBoolean("flashEnabled", true));

        soundsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferencesEditor.putBoolean("soundsEnabled", isChecked);
                preferencesEditor.apply();
            }
        });

        flashSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                preferencesEditor.putBoolean("flashEnabled", isChecked);
                preferencesEditor.apply();
            }
        });
    }
}
