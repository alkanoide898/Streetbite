package com.chacho.streetbite;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.Keyframe;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

/**
 * Animaciones de entrada de la interfaz, con APIs nativas de {@link View}.
 *
 * Todas se basan en mover y difuminar vistas, sin tocar el ancho/alto del
 * layout. Así el desplazamiento va por el hilo de renderizado y se ve fluido,
 * y no interrumpe el foco ni la apertura del teclado.
 *
 * Si el usuario desactivó las animaciones en las opciones de desarrollador,
 * {@link ValueAnimator#areAnimatorsEnabled()} es falso y las vistas quedan
 * directamente en su posición final, visibles.
 */
public final class Animaciones {

    /** Caída del login: amplia, escalonada y con un rebote al aterrizar. */
    private static final long DURACION_CAIDA_MS = 720L;
    private static final long ESCALON_CAIDA_MS = 95L;
    private static final int DISTANCIA_CAIDA_DP = 110;

    /** Entrada del resto de pantallas: subida corta y suave. */
    private static final long DURACION_ENTRADA_MS = 520L;
    private static final long ESCALON_ENTRADA_MS = 75L;
    private static final int DISTANCIA_ENTRADA_DP = 34;

    private Animaciones() {
        // Evita crear instancias de esta clase.
    }

    /**
     * Las vistas caen desde arriba una detrás de otra. Cada una entra desde
     * fuera de la pantalla, pasa un poco su posición final y rebota antes de
     * quedarse quieta, para que el movimiento se note sin resultar brusco.
     */
    public static void caerDesdeArriba(View... vistas) {
        caerDesdeArriba(ESCALON_CAIDA_MS, vistas);
    }

    /** Igual que {@link #caerDesdeArriba(View...)} con un escalonamiento propio. */
    public static void caerDesdeArriba(long escalonMs, View... vistas) {
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

            vista.animate().cancel();
            vista.setAlpha(0f);
            vista.setTranslationY(-distancia);

            // Mientras se mueve, la vista se dibuja una sola vez en una capa
            // y solo se desplaza esa capa. Sin esto, una tarjeta con sombra
            // se vuelve a dibujar en cada frame y la animación se traba.
            vista.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            PropertyValuesHolder ejeY = PropertyValuesHolder.ofKeyframe(
                    View.TRANSLATION_Y,
                    Keyframe.ofFloat(0f, -distancia),
                    Keyframe.ofFloat(0.55f, 12f * dp),
                    Keyframe.ofFloat(0.75f, -6f * dp),
                    Keyframe.ofFloat(1f, 0f));

            PropertyValuesHolder opacidad = PropertyValuesHolder.ofKeyframe(
                    View.ALPHA,
                    Keyframe.ofFloat(0f, 0f),
                    Keyframe.ofFloat(0.28f, 1f),
                    Keyframe.ofFloat(1f, 1f));

            ObjectAnimator animador = ObjectAnimator.ofPropertyValuesHolder(
                    vista, ejeY, opacidad);
            animador.setDuration(DURACION_CAIDA_MS);
            animador.setStartDelay(i * escalonMs);
            animador.setInterpolator(new AccelerateDecelerateInterpolator());
            animador.addListener(liberarCapa(vista));
            animador.start();
        }
    }

    /**
     * Giro leve mientras cae, pensado para el logo del login. Se anima por
     * separado para no competir con la caída del mismo {@link View}.
     */
    public static void giroAlCaer(View vista, float gradosIniciales) {
        if (!ValueAnimator.areAnimatorsEnabled() || vista == null) {
            return;
        }

        vista.setRotation(gradosIniciales);

        ObjectAnimator giro = ObjectAnimator.ofFloat(
                vista, View.ROTATION, gradosIniciales, 0f);
        giro.setDuration(DURACION_CAIDA_MS);
        giro.setInterpolator(new OvershootInterpolator(1.4f));
        giro.start();
    }

    /**
     * Entrada por defecto del resto de pantallas: el contenido sube desde
     * abajo con un pequeño rebote y va apareciendo en cascada.
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
            vista.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            PropertyValuesHolder ejeY = PropertyValuesHolder.ofKeyframe(
                    View.TRANSLATION_Y,
                    Keyframe.ofFloat(0f, distancia),
                    Keyframe.ofFloat(0.72f, -7f * dp),
                    Keyframe.ofFloat(1f, 0f));

            PropertyValuesHolder opacidad = PropertyValuesHolder.ofKeyframe(
                    View.ALPHA,
                    Keyframe.ofFloat(0f, 0f),
                    Keyframe.ofFloat(0.35f, 1f),
                    Keyframe.ofFloat(1f, 1f));

            ObjectAnimator animador = ObjectAnimator.ofPropertyValuesHolder(
                    vista, ejeY, opacidad);
            animador.setDuration(DURACION_ENTRADA_MS);
            animador.setStartDelay(i * ESCALON_ENTRADA_MS);
            animador.setInterpolator(new DecelerateInterpolator(1.4f));
            animador.addListener(liberarCapa(vista));
            animador.start();
        }
    }

    /** Devuelve la vista a su dibujado normal al terminar o cancelar. */
    private static AnimatorListenerAdapter liberarCapa(View vista) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animacion) {
                vista.setLayerType(View.LAYER_TYPE_NONE, null);
            }

            @Override
            public void onAnimationCancel(Animator animacion) {
                vista.setLayerType(View.LAYER_TYPE_NONE, null);
            }
        };
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
