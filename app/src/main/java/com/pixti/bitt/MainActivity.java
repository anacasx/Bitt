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

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private TextureView textureView;
    private TextView result, confidence;
    private int imageSize = 224;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;

    private String lastResult = "0";
    private float lastConfidence = 0.0f;
    private long lastDetectionTime = 0;
    private final long resetInterval = 30000; // 30 segundos
    private Handler handler;

    private MediaPlayer scanningMediaPlayer;
    private MediaPlayer recognizedMediaPlayer;

    private TextToSpeech textToSpeech;

    private long recognitionStartTime = 0;
    private boolean isRecognized = false;

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

        // Inicializa los MediaPlayer
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

        preferences = getSharedPreferences("AppPreferences", MODE_PRIVATE);
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

    // Runnable para reproducir el sonido de escaneo a intervalos regulares
    private final Runnable scanningSoundRunnable = new Runnable() {
        @Override
        public void run() {
            if (!scanningMediaPlayer.isPlaying()) {
                scanningMediaPlayer.start();
            }
            handler.postDelayed(this, 1500); // Repite cada 1.5 segundos
        }
    };

    // Listener para el TextureView
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

    // Abre la cámara
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

    // Callback para el estado de la cámara
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

    // Crea la vista previa de la cámara
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

    // Actualiza la vista previa de la cámara
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

    // Clasifica la imagen capturada
    public void classifyImage(Bitmap image) {
        try {
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
                    int val = intValues[pixel++];
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

            //String[] classes = {"20aa", "20ar", "20ba", "20br", "50aa", "50ar", "50ba", "50br", "100aa", "100ar", "100ba", "100br", "200aa", "200ar", "200ba", "200br", "500aa", "500ar", "500ba", "500br", "1000ba", "1000br"}; // Clases de tu modelo
            //Clase nueva

            String[] classes = {"20-a", "20-b", "50-a", "50-b", "100-a", "100-b", "200-a", "200-b", "500-a", "500-b", "1000"}; // Clases de tu modelo




            float confidenceThreshold = 0.99f; // Umbral de confianza

            // Verifica si la confianza es mayor al umbral y el resultado se mantiene durante 2 segundos
            if (maxConfidence >= confidenceThreshold) {
                if (!isRecognized) {
                    isRecognized = true;
                    recognitionStartTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - recognitionStartTime >= 2000) {
                    lastResult = classes[maxPos];
                    //lastResult = convertClassToValue(classes[maxPos]);
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
        }
    }

    // Convierte la clase detectada en un valor en pesos

   /* private String convertClassToValue(String detectedClass) {
        switch (detectedClass) {
            case "20-a":
            case "20-b":
                return "20 pesos";
            case "50-a":
            case "50-b":
                return "50 pesos";
            case "100-a":
            case "100-b":
                return "100 pesos";
            case "200-a":
            case "200-b":
                return "200 pesos";
            case "500-a":
            case "500-b":
                return "500 pesos";
            case "1000":
                return "1000 pesos";
            default:
                return "0";
        }
    }*/

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
}
