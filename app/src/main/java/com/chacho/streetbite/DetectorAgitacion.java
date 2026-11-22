package com.chacho.streetbite;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;

/**
 * Detector de agitación mediante el acelerómetro del dispositivo
 * ({@link Sensor#TYPE_ACCELEROMETER}).
 *
 * Se registra solo mientras HomeActivity está visible (onResume / onPause)
 * para no consumir batería cuando la pantalla no está en uso.
 */
public class DetectorAgitacion implements SensorEventListener {

    public interface OnAgitarListener {
        void alAgitar();
    }

    private static final String PREFS = "StreetBiteSensores";
    private static final String CLAVE_ACTIVO = "agitar_activo";

    // Fuerza G umbral calibrada para sacudida natural y Virtual Sensors.
    private static final float UMBRAL_G = 2.15f;
    private static final long COOLDOWN_MS = 1200L;

    private final Context contexto;
    private final SensorManager sensorManager;
    private final Sensor acelerometro;
    private final OnAgitarListener listener;

    private boolean escuchando = false;
    private long ultimoDisparo = 0L;

    public DetectorAgitacion(Context contexto, OnAgitarListener listener) {
        this.contexto = contexto.getApplicationContext();
        this.listener = listener;
        this.sensorManager = (SensorManager) this.contexto
                .getSystemService(Context.SENSOR_SERVICE);
        this.acelerometro = this.sensorManager != null
                ? this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                : null;
    }

    public boolean haySensor() {
        return acelerometro != null;
    }

    public void iniciar() {
        if (escuchando || sensorManager == null || acelerometro == null) {
            return;
        }
        if (!estaActivo(contexto)) {
            return;
        }

        escuchando = sensorManager.registerListener(
                this,
                acelerometro,
                SensorManager.SENSOR_DELAY_UI
        );
    }

    public void detener() {
        if (!escuchando || sensorManager == null) {
            return;
        }
        sensorManager.unregisterListener(this);
        escuchando = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length < 3) {
            return;
        }

        float gx = event.values[0] / SensorManager.GRAVITY_EARTH;
        float gy = event.values[1] / SensorManager.GRAVITY_EARTH;
        float gz = event.values[2] / SensorManager.GRAVITY_EARTH;

        double gForce = Math.sqrt(gx * gx + gy * gy + gz * gz);
        if (gForce <= UMBRAL_G) {
            return;
        }

        long ahora = SystemClock.uptimeMillis();
        if (ahora - ultimoDisparo < COOLDOWN_MS) {
            return;
        }

        ultimoDisparo = ahora;
        if (listener != null) {
            listener.alAgitar();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No requiere acción.
    }

    public static boolean estaActivo(Context contexto) {
        return preferencias(contexto).getBoolean(CLAVE_ACTIVO, true);
    }

    public static void activar(Context contexto, boolean activo) {
        preferencias(contexto)
                .edit()
                .putBoolean(CLAVE_ACTIVO, activo)
                .apply();
    }

    private static SharedPreferences preferencias(Context contexto) {
        return contexto.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
