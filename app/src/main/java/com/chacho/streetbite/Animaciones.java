package com.chacho.streetbite;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.OvershootInterpolator;

/**
 * Animaciones de entrada de la interfaz, con APIs nativas de {@link View}.
 *
 * Están pensadas para verse claras sin castigar al dispositivo: usan el
 * animador propio de la vista, que mueve y difumina por el hilo de
 * renderizado, y no tocan el ancho/alto del layout. Por eso no interrumpen el
 * foco ni la apertura del teclado.
 *
 * Si el usuario desactivó las animaciones en las opciones de desarrollador,
 * {@link ValueAnimator#areAnimatorsEnabled()} es falso y las vistas quedan
 * directamente en su posición final, visibles.
 */
public final class Animaciones {

    /** Caída del login: corta, escalonada y con un rebote discreto. */
    private static final long DURACION_CAIDA_MS = 460L;
    private static final long ESCALON_CAIDA_MS = 60L;
    private static final int DISTANCIA_CAIDA_DP = 70;
    private static final float REBOTE_CAIDA = 1.2f;

    /** Entrada del resto de pantallas: subida breve y suave. */
    private static final long DURACION_ENTRADA_MS = 360L;
    private static final long ESCALON_ENTRADA_MS = 45L;
    private static final int DISTANCIA_ENTRADA_DP = 20;
    private static final float REBOTE_ENTRADA = 1f;

    private Animaciones() {
        // Evita crear instancias de esta clase.
    }

    /** Caída por defecto, usada por el registro. */
    public static void caerDesdeArriba(View... vistas) {
        caer(ESCALON_CAIDA_MS, false, vistas);
    }

    /** Caída con un escalonamiento propio, para formularios largos. */
    public static void caerDesdeArriba(long escalonMs, View... vistas) {
        caer(escalonMs, false, vistas);
    }

    /**
     * Caída del login: el primer bloque es el logo y entra con un giro leve.
     * El giro va en la misma animación que la caída para no abrir un segundo
     * animador sobre la misma vista.
     */
    public static void caerLogoDesdeArriba(View logo, View... resto) {
        View[] vistas = new View[resto.length + 1];
        vistas[0] = logo;
        System.arraycopy(resto, 0, vistas, 1, resto.length);

        caer(ESCALON_CAIDA_MS, true, vistas);
    }

    private static void caer(long escalonMs, boolean girarLogo, View... vistas) {
        View referencia = primeraVista(vistas);

        if (!ValueAnimator.areAnimatorsEnabled() || referencia == null) {
            return;
        }

        float dp = densidad(referencia);
        float distancia = DISTANCIA_CAIDA_DP * dp;

        for (int i = 0; i < vistas.length; i++) {
            View vista = vistas[i];

            if (vista == null) {
                continue;
            }

            boolean esLogo = girarLogo && i == 0;

            vista.animate().cancel();
            vista.setAlpha(0f);
            vista.setTranslationY(-distancia);

            if (esLogo) {
                vista.setRotation(-10f);
            }

            ViewPropertyAnimator animador = vista.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DURACION_CAIDA_MS)
                    .setStartDelay(i * escalonMs)
                    .setInterpolator(new OvershootInterpolator(REBOTE_CAIDA));

            if (esLogo) {
                animador.rotation(0f);
            }

            animador.start();
        }
    }

    /**
     * Entrada por defecto del resto de pantallas: los bloques suben un poco y
     * aparecen en cascada, con un recorrido más corto que el del login.
     */
    public static void entrarDesdeAbajo(View... vistas) {
        View referencia = primeraVista(vistas);

        if (!ValueAnimator.areAnimatorsEnabled() || referencia == null) {
            return;
        }

        float dp = densidad(referencia);
        float distancia = DISTANCIA_ENTRADA_DP * dp;

        for (int i = 0; i < vistas.length; i++) {
            View vista = vistas[i];

            if (vista == null) {
                continue;
            }

            vista.animate().cancel();
            vista.setAlpha(0f);
            vista.setTranslationY(distancia);

            vista.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(DURACION_ENTRADA_MS)
                    .setStartDelay(i * ESCALON_ENTRADA_MS)
                    .setInterpolator(
                            new OvershootInterpolator(REBOTE_ENTRADA))
                    .start();
        }
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
