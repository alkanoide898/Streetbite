package com.chacho.streetbite;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
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
 * sin tener que envolver listener por listener, y siempre es el mismo.
 *
 * Suena una vez por toque: se programa al bajar el dedo y se cancela si el
 * gesto se convierte en scroll. No depende de que el toque sea corto ni de
 * que el dedo se mantenga quieto, para que no se pierda ningún sonido.
 *
 * También deja el contenido fuera de las barras del sistema: desde Android 15
 * la app se dibuja edge-to-edge y la barra de estado se comía los toques de
 * los botones que quedaban arriba (Volver, etc.).
 */
public abstract class BaseActivity extends AppCompatActivity {

    /**
     * Si el dedo sigue apoyado después de este tiempo, el sonido suena igual.
     * Así un toque largo no se queda sin feedback.
     */
    private static final long RETARDO_SONIDO_MS = 70L;

    private final Handler manejador = new Handler(Looper.getMainLooper());

    private Runnable sonidoPendiente;
    private boolean sonidoYaReproducido;
    private float xInicial;
    private float yInicial;
    private View vistaInicial;

    private int toqueMinimo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        toqueMinimo = ViewConfiguration.get(this).getScaledTouchSlop();
        Sonidos.iniciar(this);

        if (suenaTransicion()) {
            Sonidos.transicion(this);
        }
    }

    /**
     * Suena un soplido al abrir cada pantalla. El splash lo desactiva para que
     * no se escuchen dos transiciones seguidas al arrancar la app.
     */
    protected boolean suenaTransicion() {
        return true;
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
                sonidoYaReproducido = false;
                xInicial = evento.getRawX();
                yInicial = evento.getRawY();
                vistaInicial = buscarClickeable(
                        getWindow().getDecorView(), xInicial, yInicial);

                if (vistaInicial != null) {
                    programarSonido();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // Si el gesto se convierte en scroll, se cancela el sonido.
                if (vistaInicial != null && seMovioDemasiado(evento)) {
                    cancelarSonido();
                    vistaInicial = null;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (sonidoPendiente != null) {
                    // Toque corto: suena al soltar, que es cuando Android
                    // ejecuta el click. Si el dedo se mantuvo apoyado, el
                    // sonido ya sonó desde el temporizador.
                    cancelarSonido();

                    if (!sonidoYaReproducido && vistaInicial != null
                            && !seMovioDemasiado(evento)) {
                        sonidoYaReproducido = true;
                        Sonidos.tocar(this);
                    }
                }
                vistaInicial = null;
                break;

            case MotionEvent.ACTION_CANCEL:
                cancelarSonido();
                vistaInicial = null;
                break;

            default:
                break;
        }

        return super.dispatchTouchEvent(evento);
    }

    /** Programa el sonido para que suene aunque el usuario mantenga el dedo. */
    private void programarSonido() {
        cancelarSonido();

        sonidoPendiente = () -> {
            sonidoPendiente = null;

            if (!sonidoYaReproducido) {
                sonidoYaReproducido = true;
                Sonidos.tocar(this);
            }
        };

        manejador.postDelayed(sonidoPendiente, RETARDO_SONIDO_MS);
    }

    private void cancelarSonido() {
        if (sonidoPendiente != null) {
            manejador.removeCallbacks(sonidoPendiente);
            sonidoPendiente = null;
        }
    }

    /**
     * El umbral es el doble del slop de Android: un toque con un pequeño
     * temblor sigue sonando, pero un arrastre cancela el efecto.
     */
    private boolean seMovioDemasiado(MotionEvent evento) {
        float dx = evento.getRawX() - xInicial;
        float dy = evento.getRawY() - yInicial;
        float limite = 2f * toqueMinimo;

        return dx * dx + dy * dy > limite * limite;
    }

    @Override
    protected void onPause() {
        cancelarSonido();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        cancelarSonido();
        super.onDestroy();
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

    /**
     * Los cuadros de diálogo tienen su propia ventana, así que sus botones no
     * pasan por dispatchTouchEvent de la Activity. Se les pone el mismo sonido
     * sin reemplazar sus listeners de click. Debe llamarse después de show().
     */
    protected void sonarBotonesDelDialogo(AlertDialog dialogo) {
        sonarAlTocar(
                dialogo.getButton(DialogInterface.BUTTON_POSITIVE),
                dialogo.getButton(DialogInterface.BUTTON_NEGATIVE),
                dialogo.getButton(DialogInterface.BUTTON_NEUTRAL));
    }

    /** Aplica el sonido de toque a vistas que viven fuera de la Activity. */
    protected void sonarAlTocar(View... vistas) {
        for (View vista : vistas) {
            if (vista == null) {
                continue;
            }

            vista.setOnTouchListener((v, evento) -> {
                if (evento.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    Sonidos.tocar(this);
                }
                return false;
            });
        }
    }
}
