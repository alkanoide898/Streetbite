package com.chacho.streetbite;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;

/**
 * Efectos de sonido de la interfaz.
 *
 * Usa SoundPool (no MediaPlayer) porque los sonidos son muy cortos y se
 * repiten todo el tiempo: se cargan una sola vez en memoria y se pueden
 * solapar sin cortarse entre sí.
 */
public final class Sonidos {

    /** Toque normal: botones, chips, navegación. */
    public static final int TAP = 0;

    /** Toque sobre una tarjeta de foodtruck (un poco más suave). */
    public static final int TARJETA = 1;

    private static final String PREFS = "StreetBiteSonidos";
    private static final String CLAVE_ACTIVO = "activos";

    // Volumen discreto: son un detalle, no deben tapar la música del usuario.
    private static final float VOLUMEN = 0.55f;

    private static SoundPool pool;
    private static int idTap;
    private static int idTarjeta;

    private Sonidos() {
        // Evita crear instancias de esta clase.
    }

    /**
     * Carga los sonidos en memoria. Se llama al crear cada Activity, pero
     * solo la primera vez hace trabajo real.
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
                .setMaxStreams(4)
                .setAudioAttributes(atributos)
                .build();

        idTap = pool.load(app, R.raw.tap, 1);
        idTarjeta = pool.load(app, R.raw.tarjeta, 1);
    }

    /**
     * Reproduce un efecto. Si el usuario los desactivó, no hace nada.
     *
     * @param sonido {@link #TAP} o {@link #TARJETA}
     */
    public static void tocar(Context contexto, int sonido) {
        if (!estaActivo(contexto)) {
            return;
        }

        iniciar(contexto);

        // Si SoundPool todavía no terminó de cargar, play() no suena y
        // devuelve 0: no pasa nada, el siguiente toque ya se escucha.
        int id = sonido == TARJETA ? idTarjeta : idTap;

        if (id != 0) {
            pool.play(id, VOLUMEN, VOLUMEN, 1, 0, 1f);
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
