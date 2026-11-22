package com.chacho.streetbite;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Efectos de sonido de la interfaz.
 *
 * Hay dos efectos y comparten un mismo SoundPool:
 *
 * - {@link #tocar(Context)}: toque suave para botones, chips y tarjetas.
 * - {@link #transicion(Context)}: soplido aireado al abrir una pantalla.
 *
 * Tres reglas evitan que se escuchen dobles o distorsionados:
 *
 * 1. Cada efecto respeta un intervalo mínimo igual a su duración. Así nunca
 *    se solapan dos veces y nunca se corta uno a mitad, que es lo que
 *    produce un clic audible.
 * 2. La transición empieza un poco después del toque, para que al navegar no
 *    suenen los dos golpes al mismo tiempo.
 * 3. SoundPool.load() es asíncrono, así que se espera a que termine antes de
 *    reproducir; sin eso el primer toque se quedaba sin sonido.
 */
public final class Sonidos {

    private static final String PREFS = "StreetBiteSonidos";
    private static final String CLAVE_ACTIVO = "activos";

    // El toque es seco y sutil; la transición es una nota suave y discreta
    // para acompañar el cambio de pantalla sin estorbar.
    private static final float VOLUMEN_TOQUE = 0.65f;
    private static final float VOLUMEN_TRANSICION = 0.45f;

    /** tap.wav dura 28 ms: antes de 45 ms no entra otro toque. */
    private static final long INTERVALO_TOQUE_MS = 45L;

    /** transicion.wav dura 140 ms: antes de 180 ms no entra otra transición. */
    private static final long INTERVALO_TRANSICION_MS = 180L;

    /**
     * La transición se retrasa un instante para que termine el toque del botón.
     * Si suenan juntos, el usuario escucha dos golpes y lo percibe como doble.
     */
    private static final long RETARDO_TRANSICION_MS = 60L;

    private static final Handler manejador =
            new Handler(Looper.getMainLooper());

    private static SoundPool pool;
    private static int idToque;
    private static int idTransicion;
    private static volatile boolean toqueListo;
    private static volatile boolean transicionLista;

    private static long ultimoToque;
    private static long ultimaTransicion;
    private static Runnable transicionPendiente;

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

    /**
     * Toque suave de botón, chip o tarjeta.
     *
     * Si llega otro toque antes de terminar el anterior, se ignora: es un
     * rebote del dedo o un doble toque demasiado rápido para percibirse como
     * dos acciones, y reproducirlo haría que se escuchara doble.
     */
    public static void tocar(Context contexto) {
        if (!estaActivo(contexto)) {
            return;
        }

        iniciar(contexto);

        long ahora = SystemClock.uptimeMillis();

        if (ahora - ultimoToque < INTERVALO_TOQUE_MS) {
            return;
        }

        if (toqueListo && idToque != 0) {
            ultimoToque = ahora;
            pool.play(idToque, VOLUMEN_TOQUE, VOLUMEN_TOQUE, 1, 0, 1f);
        }
    }

    /**
     * Soplido suave al abrir una pantalla.
     *
     * Se reproduce unos milisegundos después del toque y respeta un intervalo
     * mínimo: así no se solapa con el toque ni se corta a mitad si se navega
     * rápido.
     */
    public static void transicion(Context contexto) {
        if (!estaActivo(contexto)) {
            return;
        }

        iniciar(contexto);

        final Context app = contexto.getApplicationContext();

        // Solo queda una transición en vuelo: la anterior se descarta.
        if (transicionPendiente != null) {
            manejador.removeCallbacks(transicionPendiente);
        }

        transicionPendiente = () -> {
            transicionPendiente = null;

            if (!estaActivo(app)) {
                return;
            }

            long ahora = SystemClock.uptimeMillis();

            if (ahora - ultimaTransicion < INTERVALO_TRANSICION_MS) {
                return;
            }

            if (transicionLista && idTransicion != 0) {
                ultimaTransicion = ahora;
                pool.play(idTransicion, VOLUMEN_TRANSICION,
                        VOLUMEN_TRANSICION, 1, 0, 1f);
            }
        };

        manejador.postDelayed(transicionPendiente, RETARDO_TRANSICION_MS);
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
