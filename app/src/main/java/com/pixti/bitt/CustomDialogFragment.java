/**
 * Nombre de la Clase: CustomDialogFragment
 *
 * Autor: Xóchitl Cabañas (gh:@anacasx)
 * Fecha: Agosto 2024
 *
 * Descripción:
 * Esta clase representa un fragmento de diálogo personalizado que se utiliza en la
 * aplicación para mostrar mensajes importantes al usuario. La ventana emergente
 * se cierra al hacer clic en cualquier parte de la pantalla, y pausa las actividades
 * en la ventana principal, como el reconocimiento de objetos y la reproducción de
 * sonidos, para permitir que el mensaje sea escuchado claramente a través de TextToSpeech.
 *
 * Notas adicionales:
 * - Este archivo es parte del paquete com.pixti.bitt.
 */

package com.pixti.bitt;

import android.content.DialogInterface;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

public class CustomDialogFragment extends DialogFragment {

    private TextToSpeech textToSpeech;

    public CustomDialogFragment(TextToSpeech textToSpeech) {
        this.textToSpeech = textToSpeech;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_custom, container, false);

        // Configurar el diálogo para que se cierre al tocar en cualquier lugar de la pantalla
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();  // Cerrar el diálogo
            }
        });

        // Texto que se leerá al mostrar la ventana emergente
        String text = "Para usar la aplicación, coloca el billete frente a la cámara. Para continuar, presiona en cualquier lugar de la pantalla.";
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);

        // Pausar la detección y sonidos aquí
        ((MainActivity) getActivity()).pauseRecognition();

        return view;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        // Reanudar la detección y sonidos cuando se cierre el diálogo
        ((MainActivity) getActivity()).resumeRecognition();
    }
}
