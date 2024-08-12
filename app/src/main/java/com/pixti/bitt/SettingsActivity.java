/**
 * Nombre de la Clase: SettingsActivity
 *
 * Autor: Xóchitl Cabañas (gh:@anacasx)
 * Fecha: Agosto 2024
 *
 * Descripción:
 * Esta actividad permite al usuario ajustar las preferencias de la aplicación, específicamente
 * las opciones relacionadas con sonidos y el uso del flash. Utiliza `SharedPreferences` para
 * guardar estos ajustes de manera persistente, asegurando que las preferencias del usuario se
 * mantengan incluso después de cerrar la aplicación.
 *
 * Funcionalidades:
 * - Activar o desactivar sonidos dentro de la aplicación.
 * - Activar o desactivar el uso del flash de la cámara.
 *
 * Notas adicionales:
 * - Los ajustes se cargan y se aplican al iniciar la actividad.
 * - Los cambios en las preferencias se guardan de forma asíncrona usando `apply()`.
 *
 * Requerimientos:
 * - `activity_settings.xml` debe contener dos switches con los IDs `switch_sounds` y `switch_flash`.
 */

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

        // Inicializa los switches para sonidos y flash
        soundsSwitch = findViewById(R.id.switch_sounds);
        flashSwitch = findViewById(R.id.switch_flash);

        // Obtiene las preferencias compartidas
        preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        preferencesEditor = preferences.edit();

        // Configura el estado inicial de los switches basándose en las preferencias almacenadas
        soundsSwitch.setChecked(preferences.getBoolean("soundsEnabled", true));
        flashSwitch.setChecked(preferences.getBoolean("flashEnabled", true));

        // Configura un listener para el switch de sonidos
        soundsSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Guarda el nuevo estado del switch de sonidos en las preferencias
                preferencesEditor.putBoolean("soundsEnabled", isChecked);
                preferencesEditor.apply(); // Aplica los cambios de forma asíncrona
            }
        });

        // Configura un listener para el switch de flash
        flashSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // Guarda el nuevo estado del switch de flash en las preferencias
                preferencesEditor.putBoolean("flashEnabled", isChecked);
                preferencesEditor.apply(); // Aplica los cambios de forma asíncrona
            }
        });
    }
}
