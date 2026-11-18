package com.chacho.streetbite;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Efectos de sonido de la interfaz.
 *
 * Hay dos efectos y comparten un mismo SoundPool:
 *
 * - {@link #tocar(Context)}: toque suave para botones, chips y tarjetas.
 * - {@link #transicion(Context)}: soplido aireado al abrir una pantalla.
 *
 * SoundPool.load() es asíncrono, así que se espera a que termine antes de
 * reproducir. Sin esa espera, el primer toque después de abrir la app se
 * quedaba sin sonido y se sentía como un retardo.
 */
public final class Sonidos {

    private static final String PREFS = "StreetBiteSonidos";
    private static final String CLAVE_ACTIVO = "activos";

    // El toque es corto y destaca; el soplido dura más y va más suave para
    // que no compita con el toque ni con el video.
    private static final float VOLUMEN_TOQUE = 0.85f;
    private static final float VOLUMEN_TRANSICION = 0.65f;

    private static SoundPool pool;
    private static int idToque;
    private static int idTransicion;
    private static int streamToque;
    private static int streamTransicion;
    private static volatile boolean toqueListo;
    private static volatile boolean transicionLista;

    private Sonidos() {
        // Evita crear instancias de esta clase.
    }

    /**
     * Carga los efectos en memoria. Se llama al crear cada Activity, pero solo
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
                // Un toque y una transición a la vez, sin cortes entre sí.
                .setMaxStreams(4)
                .setAudioAttributes(atributos)
                .build();

        pool.setOnLoadCompleteListener((soundPool, id, estado) -> {
            if (estado != 0) {
                return;
            }

            if (id == idToque) {
                toqueListo = true;
            }

            if (id == idTransicion) {
                transicionLista = true;
            }
        });

        idToque = pool.load(app, R.raw.tap, 1);
        idTransicion = pool.load(app, R.raw.transicion, 1);
    }

    /** Toque suave de botón, chip o tarjeta. */
    public static void tocar(Context contexto) {
        if (!estaActivo(contexto)) {
            return;
        }

        iniciar(contexto);

        if (toqueListo && idToque != 0) {
            // Si se toca rápido, el sonido nuevo reemplaza al anterior en vez
            // de sumarse: así no se escucha doble ni se distorsiona.
            if (streamToque != 0) {
                pool.stop(streamToque);
            }

            streamToque = pool.play(
                    idToque, VOLUMEN_TOQUE, VOLUMEN_TOQUE, 1, 0, 1f);
        }
    }

    /** Soplido suave al abrir una pantalla. */
    public static void transicion(Context contexto) {
        if (!estaActivo(contexto)) {
            return;
        }

        iniciar(contexto);

        if (transicionLista && idTransicion != 0) {
            // Pantallas que cambian rápido no apilan el soplido.
            if (streamTransicion != 0) {
                pool.stop(streamTransicion);
            }

            streamTransicion = pool.play(
                    idTransicion, VOLUMEN_TRANSICION, VOLUMEN_TRANSICION,
                    1, 0, 1f);
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
