package com.chacho.streetbite;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Efecto de sonido de la interfaz.
 *
 * Hay un solo sonido para toda la app: el mismo en botones, chips, tarjetas y
 * navegación. Así el feedback es uniforme y no depende de dónde se toque.
 *
 * SoundPool.load() es asíncrono, así que se espera a que termine antes de
 * reproducir. Sin esa espera, el primer toque después de abrir la app se
 * quedaba sin sonido y se sentía como un retardo.
 */
public final class Sonidos {

    private static final String PREFS = "StreetBiteSonidos";
    private static final String CLAVE_ACTIVO = "activos";

    // El archivo ya es suave: el volumen lo deja audible sin ser molesto.
    private static final float VOLUMEN = 0.85f;

    private static SoundPool pool;
    private static int idTap;
    private static volatile boolean listo;

    private Sonidos() {
        // Evita crear instancias de esta clase.
    }

    /**
     * Carga el sonido en memoria. Se llama al crear cada Activity, pero solo
     * la primera vez hace trabajo real.
     */
    public static synchronized void iniciar(Context contexto) {
        if (pool != null) {
            return;
        }

        Context app = contexto.getApplicationContext();

        AudioAttributes atributos = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        pool = new SoundPool.Builder()
                // Varios toques seguidos no deben cortarse entre sí.
                .setMaxStreams(8)
                .setAudioAttributes(atributos)
                .build();

        pool.setOnLoadCompleteListener((soundPool, id, estado) -> {
            if (estado == 0 && id == idTap) {
                listo = true;
            }
        });

        idTap = pool.load(app, R.raw.tap, 1);
    }

    /** Reproduce el efecto, si el usuario no lo desactivó y ya está cargado. */
    public static void tocar(Context contexto) {
        if (!estaActivo(contexto)) {
            return;
        }

        iniciar(contexto);

        if (listo && idTap != 0) {
            pool.play(idTap, VOLUMEN, VOLUMEN, 1, 0, 1f);
        }
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
