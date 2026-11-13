package com.chacho.streetbite;

import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.osmdroid.views.MapView;

/**
 * Activity base con el efecto de sonido de la interfaz.
 *
 * El sonido se dispara desde dispatchTouchEvent y no desde cada
 * setOnClickListener. Así funciona en todos los botones, chips y tarjetas
 * sin tener que envolver listener por listener.
 *
 * Solo suena cuando el dedo baja y sube sobre la MISMA vista clickeable,
 * con poco movimiento y poco tiempo. Al hacer scroll en una lista el
 * gesto se cancela o se mueve demasiado, así que no suena.
 *
 * También deja el contenido fuera de las barras del sistema: desde Android 15
 * la app se dibuja edge-to-edge y la barra de estado se comía los toques de
 * los botones que quedaban arriba (Volver, etc.).
 */
public abstract class BaseActivity extends AppCompatActivity {

    private static final long DURACION_MAXIMA_MS = 600L;

    private float xInicial;
    private float yInicial;
    private long tiempoInicial;
    private View vistaInicial;

    private int toqueMinimo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        toqueMinimo = ViewConfiguration.get(this).getScaledTouchSlop();
        Sonidos.iniciar(this);
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        ajustarBarrasDelSistema();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        ajustarBarrasDelSistema();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        ajustarBarrasDelSistema();
    }

    /**
     * Permite que una pantalla se dibuje a pantalla completa sin este ajuste.
     * El splash lo desactiva porque su fondo ya está pensado para ocupar
     * toda la pantalla.
     */
    protected boolean usaBarrasDelSistema() {
        return true;
    }

    /**
     * Suma el alto de las barras del sistema al padding de la vista raíz del
     * layout. Se aplica sobre la raíz y no sobre android.R.id.content para
     * que el fondo (por ejemplo sb_crema) siga pintando detrás de la barra
     * de estado, sin una banda de otro color.
     *
     * Los paddings originales del layout se guardan, así el ajuste no se
     * acumula cuando los insets se recalculan (rotar, abrir el teclado...).
     */
    private void ajustarBarrasDelSistema() {
        if (!usaBarrasDelSistema()) {
            return;
        }

        ViewGroup contenido = findViewById(android.R.id.content);

        if (contenido == null || contenido.getChildCount() == 0) {
            return;
        }

        final View raiz = contenido.getChildAt(0);

        final int padIzquierda = raiz.getPaddingLeft();
        final int padArriba = raiz.getPaddingTop();
        final int padDerecha = raiz.getPaddingRight();
        final int padAbajo = raiz.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            vista.setPadding(
                    padIzquierda + barras.left,
                    padArriba + barras.top,
                    padDerecha + barras.right,
                    padAbajo + barras.bottom);

            return insets;
        });

        ViewCompat.requestApplyInsets(raiz);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent evento) {
        switch (evento.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                xInicial = evento.getRawX();
                yInicial = evento.getRawY();
                tiempoInicial = SystemClock.uptimeMillis();
                vistaInicial = buscarClickeable(
                        getWindow().getDecorView(), xInicial, yInicial);
                break;

            case MotionEvent.ACTION_UP:
                if (esToqueValido(evento)) {
                    int sonido = dentroDeLista(vistaInicial)
                            ? Sonidos.TARJETA
                            : Sonidos.TAP;
                    Sonidos.tocar(this, sonido);
                }
                vistaInicial = null;
                break;

            case MotionEvent.ACTION_CANCEL:
                vistaInicial = null;
                break;

            default:
                break;
        }

        return super.dispatchTouchEvent(evento);
    }

    /**
     * Comprueba que el gesto sea un toque y no un arrastre. No se usa
     * isPressed(): dentro de un RecyclerView (o un scroll horizontal)
     * Android retrasa el estado "pressed" y llega en false al ACTION_UP.
     * Al hacer scroll la vista recibe ACTION_CANCEL, así que no suena.
     */
    private boolean esToqueValido(MotionEvent evento) {
        if (vistaInicial == null) {
            return false;
        }

        long duracion = SystemClock.uptimeMillis() - tiempoInicial;
        if (duracion > DURACION_MAXIMA_MS) {
            return false;
        }

        float dx = evento.getRawX() - xInicial;
        float dy = evento.getRawY() - yInicial;

        return (dx * dx + dy * dy) <= (float) toqueMinimo * toqueMinimo;
    }

    /**
     * Busca la vista clickeable más profunda bajo el dedo. Si el hijo no es
     * clickeable, devuelve el padre más cercano que sí lo sea, igual que
     * hace Android al repartir el toque.
     */
    private View buscarClickeable(View vista, float x, float y) {
        if (vista == null
                || vista.getVisibility() != View.VISIBLE
                || !vista.isEnabled()) {
            return null;
        }

        int[] posicion = new int[2];
        vista.getLocationOnScreen(posicion);

        boolean dentro = x >= posicion[0]
                && x <= posicion[0] + vista.getWidth()
                && y >= posicion[1]
                && y <= posicion[1] + vista.getHeight();

        if (!dentro) {
            return null;
        }

        if (vista instanceof ViewGroup) {
            ViewGroup grupo = (ViewGroup) vista;

            for (int i = grupo.getChildCount() - 1; i >= 0; i--) {
                View hijo = buscarClickeable(grupo.getChildAt(i), x, y);

                if (hijo != null) {
                    return hijo;
                }
            }
        }

        // El mapa y el fondo de una lista no son "botones": mejor sin sonido.
        if (vista instanceof MapView || vista instanceof RecyclerView) {
            return null;
        }

        // Los campos de texto no son botones: no suenan al enfocarlos.
        if (vista instanceof EditText) {
            return null;
        }

        // Los switches no usan OnClickListener, pero sí son controles.
        boolean interactiva = vista.hasOnClickListeners()
                || vista instanceof CompoundButton;

        return interactiva ? vista : null;
    }

    /** true si la vista está dentro de una lista (tarjeta de foodtruck). */
    private boolean dentroDeLista(View vista) {
        View actual = vista;

        while (actual != null) {
            if (actual instanceof RecyclerView) {
                return true;
            }

            ViewParent padre = actual.getParent();
            actual = padre instanceof View ? (View) padre : null;
        }

        return false;
    }
}
