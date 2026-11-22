package com.chacho.streetbite;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/**
 * Animaciones de interfaz sincronizadas y ligeras, pensadas para verse fluidas
 * a 60 fps incluso en dispositivos con poca RAM.
 *
 * Principios:
 * - Sincronizadas: todos los elementos entran juntos (sin escalonamiento
 *   lento uno detrás de otro).
 * - Curva Material 3 (Emphasized Decelerate): rápida al inicio y suave al
 *   detenerse, sin rebotes artificiales.
 * - Hardware layer temporal (.withLayer()) que la GPU compone sin redibujar
 *   degradados ni textos en cada frame y se libera automáticamente al acabar.
 * - Si el usuario desactivó las animaciones del sistema, no se anima nada.
 */
public final class Animaciones {

    /** Curva suave estilo Material 3 (Emphasized Decelerate). */
    public static final Interpolator CURVA_SUAVE =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    private static final long DURACION_ACCESO_MS = 280L;
    private static final int DISTANCIA_ACCESO_DP = 18;

    private static final long DURACION_ENTRADA_MS = 230L;
    private static final int DISTANCIA_ENTRADA_DP = 12;

    private Animaciones() {
    }

    /** Entrada sincronizada para pantallas de acceso (Registro). */
    public static void caerDesdeArriba(View... vistas) {
        entrarSincronizado(DURACION_ACCESO_MS, DISTANCIA_ACCESO_DP, vistas);
    }

    /** Sobrecarga mantenida por compatibilidad; entra sincronizado sin delay. */
    public static void caerDesdeArriba(long escalonMs, View... vistas) {
        entrarSincronizado(DURACION_ACCESO_MS, DISTANCIA_ACCESO_DP, vistas);
    }

    /** Entrada sincronizada para Login (logo + contenido juntos, sin tirones). */
    public static void caerLogoDesdeArriba(View logo, View... resto) {
        View[] vistas = new View[resto.length + 1];
        vistas[0] = logo;
        System.arraycopy(resto, 0, vistas, 1, resto.length);
        entrarSincronizado(DURACION_ACCESO_MS, DISTANCIA_ACCESO_DP, vistas);
    }

    /**
     * Entrada por defecto del resto de pantallas: toda la vista entra como un
     * solo bloque fluido y sincronizado.
     */
    public static void entrarDesdeAbajo(View... vistas) {
        entrarSincronizado(DURACION_ENTRADA_MS, DISTANCIA_ENTRADA_DP, vistas);
    }

    private static void entrarSincronizado(
            long duracionMs,
            int distanciaDp,
            View... vistas) {

        View referencia = primeraVista(vistas);

        if (!ValueAnimator.areAnimatorsEnabled() || referencia == null) {
            return;
        }

        float distancia = distanciaDp * densidad(referencia);

        // Preparamos todas las vistas antes del primer frame.
        for (View vista : vistas) {
            if (vista == null) {
                continue;
            }
            vista.animate().cancel();
            vista.setAlpha(0f);
            vista.setTranslationY(distancia);
        }

        // Disparamos todas a la vez justo cuando la ventana ya midió el layout,
        // evitando perder frames durante el inflado inicial.
        referencia.post(() -> {
            for (View vista : vistas) {
                if (vista == null) {
                    continue;
                }
                ViewPropertyAnimator animador = vista.animate()
                        .alpha(1f)
                        .translationY(0f)
                        .setDuration(duracionMs)
                        .setInterpolator(CURVA_SUAVE)
                        .withLayer();
                animador.start();
            }
        });
    }

    private static View primeraVista(View[] vistas) {
        for (View vista : vistas) {
            if (vista != null) {
                return vista;
            }
        }
        return null;
    }

    private static float densidad(View vista) {
        return vista.getResources().getDisplayMetrics().density;
    }
}
