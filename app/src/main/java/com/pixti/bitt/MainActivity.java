/**
 * Nombre de la Clase: MainActivity
 *
 * Autor: Xóchitl Cabañas (gh:@anacasx)
 * Fecha: Junio 2024
 *
 * Descripción:
 * Esta actividad es la principal de la aplicación. Se encarga de capturar imágenes
 * utilizando la cámara del dispositivo, clasificarlas mediante un modelo de TensorFlow Lite,
 * y mostrar el resultado en pantalla. Además, incluye funcionalidades de reconocimiento
 * de voz y sonido, manejo de preferencias del usuario para el uso del flash y reproducción
 * de sonidos.
 *
 * Notas adicionales:
 * - Este archivo es parte del paquete com.pixti.bitt.
 */


package com.pixti.bitt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pixti.bitt.ml.ModelUnquant;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Locale;

import android.app.Dialog;
import android.widget.Button;

/**
 * Clase principal de la actividad que maneja la cámara, realiza la clasificación de imágenes
 * utilizando un modelo de machine learning y actualiza la interfaz de usuario.
 */
public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    // Constante para la solicitud de permiso de cámara
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    // Elementos de la interfaz de usuario
    private TextureView textureView;
    private TextView result, confidence;
    private int imageSize = 224;
    // Componentes de la cámara
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;

    // Variables para el manejo de resultados
    private String lastResult = "0";
    private float lastConfidence = 0.0f;
    private long lastDetectionTime = 0;
    private final long resetInterval = 30000; // 30 segundos
    private Handler handler;

    // Reproductores de sonido
    private MediaPlayer scanningMediaPlayer;
    private MediaPlayer recognizedMediaPlayer;

    // Texto a voz
    private TextToSpeech textToSpeech;

    // Variables para el reconocimiento
    private long recognitionStartTime = 0;
    private boolean isRecognized = false;

    // Preferencias compartidas
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializa los elementos de la interfaz de usuario
        result = findViewById(R.id.result);
        confidence = findViewById(R.id.confidence);
        textureView = findViewById(R.id.textureView);
        ImageButton settingsButton = findViewById(R.id.settings_button);
        ImageButton newIconButton = findViewById(R.id.info_button);

        // Inicializa los reproductores de sonido
        scanningMediaPlayer = MediaPlayer.create(this, R.raw.scanning_sound);
        recognizedMediaPlayer = MediaPlayer.create(this, R.raw.recognized_sound);

        // Inicializa TextToSpeech
        textToSpeech = new TextToSpeech(this, this);

        // Establece el listener para el TextureView
        textureView.setSurfaceTextureListener(textureListener);

        // Solicita permiso de cámara si no está otorgado
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }

        handler = new Handler(Looper.getMainLooper());

        // Programa la reproducción del sonido de escaneo
        handler.postDelayed(scanningSoundRunnable, 1000); // Inicia en 1 segundo

        // Establece el listener para el botón de configuración
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        newIconButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog();
            }
        });

        // Carga las preferencias compartidas
        preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
    }


    private void showFloatingWindow() {
        // Crear y mostrar el diálogo
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.floating_window); // Asumimos que el layout de la ventana flotante se llama "floating_window"
        dialog.setCancelable(false);  // Evita que el usuario lo cierre tocando fuera de la ventana

        // Configurar el botón de continuar
        Button continueButton = dialog.findViewById(R.id.continue_button);
        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();  // Cerrar el diálogo
                startScanningProcess();  // Continuar con el proceso de la aplicación
            }
        });

        // Mostrar el diálogo
        dialog.show();

        // Leer en voz alta el mensaje
        speakOut("Para usar la aplicación, coloca el billete frente a la cámara. Para continuar, presiona en cualquier lugar de la pantalla.");
    }

    private void startScanningProcess() {
        // Aquí podrías iniciar el proceso de escaneo, si es necesario
        // O simplemente continuar con el flujo normal de la aplicación
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Carga las preferencias y ajusta según sea necesario
        boolean soundsEnabled = preferences.getBoolean("soundsEnabled", true);
        boolean flashEnabled = preferences.getBoolean("flashEnabled", true);

        if (!soundsEnabled) {
            scanningMediaPlayer.setVolume(0, 0);
            recognizedMediaPlayer.setVolume(0, 0);
        } else {
            scanningMediaPlayer.setVolume(1, 1);
            recognizedMediaPlayer.setVolume(1, 1);
        }

        if (cameraDevice != null) {
            createCameraPreview(); // Actualiza la vista previa de la cámara
        }
    }

    /**
     * Runnable para reproducir el sonido de escaneo a intervalos regulares.
     */
    private final Runnable scanningSoundRunnable = new Runnable() {
        @Override
        public void run() {
            if (!scanningMediaPlayer.isPlaying()) {
                scanningMediaPlayer.start();
            }
            handler.postDelayed(this, 1500); // Repite cada 1.5 segundos
        }
    };

    /**
     * Listener para el TextureView que maneja los eventos del SurfaceTexture.
     */
    private final TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(); // Abre la cámara cuando el SurfaceTexture está disponible
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            // Obtiene el bitmap de la cámara y lo redimensiona para el modelo
            Bitmap bitmap = textureView.getBitmap();
            bitmap = Bitmap.createScaledBitmap(bitmap, imageSize, imageSize, false);
            classifyImage(bitmap); // Clasifica la imagen capturada
        }
    };

    /**
     * Abre la cámara y prepara la vista previa.
     */
    private void openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = cameraManager.getCameraIdList()[0]; // Obtiene el ID de la cámara
            cameraManager.openCamera(cameraId, stateCallback, null); // Abre la cámara
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Callback para el estado de la cámara.
     */
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview(); // Crea la vista previa de la cámara
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    /**
     * Crea la vista previa de la cámara y configura el flash según las preferencias del usuario.
     */
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(textureView.getWidth(), textureView.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            // Configura el flash según la preferencia
            boolean flashEnabled = preferences.getBoolean("flashEnabled", true);
            if (flashEnabled) {
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            } else {
                captureRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (cameraDevice == null) return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview(); // Actualiza la vista previa
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Actualiza la vista previa de la cámara.
     */
    private void updatePreview() {
        if (cameraDevice == null) {
            Log.e("MainActivity", "Update preview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Clasifica la imagen capturada utilizando un modelo de machine learning.
     * @param image El bitmap de la imagen capturada.
     */
    public void classifyImage(Bitmap image) {
        try {
            // Carga el modelo TFLite
            ModelUnquant model = ModelUnquant.newInstance(getApplicationContext());

            // Prepara la entrada del modelo
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());

            int pixel = 0;
            for (int i = 0; i < imageSize; i++) {
                for (int j = 0; j < imageSize; j++) {
                    int val = intValues[pixel++]; //RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 255.f));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 255.f));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Realiza la inferencia del modelo y obtiene el resultado
            ModelUnquant.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidences = outputFeature0.getFloatArray();

            // Encuentra la clase con la mayor confianza
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            // Asigna etiquetas a cada clase (ejemplo)
            String[] classes = {
                    "0 20ar",
                            "1 20aa",
                            "2 20br",
                            "3 20ba",
                            "4 50br",
                            "5 500ba",
                            "6 500br",
                            "7 50ba"
            };





            float confidenceThreshold = 0.99f; // Umbral de confianza

            // Verifica si la confianza es mayor al umbral y el resultado se mantiene durante 2 segundos
            if (maxConfidence >= confidenceThreshold) {
                if (!isRecognized) {
                    isRecognized = true;
                    recognitionStartTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - recognitionStartTime >= 2000) {
                    //lastResult = classes[maxPos];
                    lastResult = convertClassToValue(classes[maxPos]);
                    lastConfidence = maxConfidence;
                    lastDetectionTime = System.currentTimeMillis();
                    updateUI();
                    resetAfterInterval();
                    // Reproduce el sonido de reconocimiento
                    scanningMediaPlayer.pause();
                    recognizedMediaPlayer.start();
                    // Lee el resultado en voz alta
                    speakOut(lastResult);
                    isRecognized = false; // Resetea el flag después de reconocimiento
                }
            } else {
                isRecognized = false;
            }

            model.close();
        } catch (IOException e) {
            // Manejar la excepción
            e.printStackTrace();
        }
    }

    // Convierte la clase detectada en un valor en pesos

    private String convertClassToValue(String detectedClass) {
        switch (detectedClass) {
            case "20aa":
            case "20ar":
            case "20ba":
            case "20br":
                return "20 pesos";
            /*case "50aa":
            case "50ar":*/
            case "50ba":
            case "50br":
                return "50 pesos";
            /*case "100aa":
            case "100ar":
            case "100ba":
            case "100br":
                return "100 pesos";
            case "200aa":
            case "200ar":
            case "200ba":
            case "200br":
                return "200 pesos";
            case "500aa":
            case "500ar":*/
            case "500ba":
            case "500br":
                return "500 pesos";
            /*case "1000ba":
            case "1000br":
                return "1000 pesos";*/
            default:
                return "0";
        }
    }

    // Actualiza la interfaz de usuario
    private void updateUI() {
        result.setText(lastResult);
        confidence.setText(String.format("%.2f%%", lastConfidence * 100));
    }

    // Restablece los valores después del intervalo
    private void resetAfterInterval() {
        handler.removeCallbacks(resetRunnable);
        handler.postDelayed(resetRunnable, resetInterval);
    }

    private final Runnable resetRunnable = new Runnable() {
        @Override
        public void run() {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastDetectionTime >= resetInterval) {
                lastResult = "0";
                lastConfidence = 0.0f;
                updateUI();
            }
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show();
            } else {
                openCamera();
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = textToSpeech.setLanguage(new Locale("es", "MX"));
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("MainActivity", "Este Lenguaje no está soportado");
            }
        } else {
            Log.e("MainActivity", "Falló la inicialización de TextToSpeech");
        }
    }

    private void speakOut(String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (scanningMediaPlayer != null) {
            scanningMediaPlayer.release();
        }
        if (recognizedMediaPlayer != null) {
            recognizedMediaPlayer.release();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }



    /*
    *
    * Pruebas
    *
    * */
    public void pauseRecognition() {
        // Pausar sonidos
        if (scanningMediaPlayer.isPlaying()) {
            scanningMediaPlayer.pause();
        }
        if (recognizedMediaPlayer.isPlaying()) {
            recognizedMediaPlayer.pause();
        }

        // Pausar el reconocimiento
        handler.removeCallbacks(scanningSoundRunnable);
    }

    public void resumeRecognition() {
        // Reanudar sonidos
        scanningMediaPlayer.start();

        // Reanudar el reconocimiento
        handler.postDelayed(scanningSoundRunnable, 1000); // Reanuda en 1 segundo
    }

    // Método para mostrar la ventana emergente
    private void showDialog() {
        CustomDialogFragment dialogFragment = new CustomDialogFragment(textToSpeech);
        dialogFragment.show(getSupportFragmentManager(), "customDialog");
    }
}