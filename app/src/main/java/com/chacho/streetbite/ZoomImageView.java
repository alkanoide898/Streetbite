package com.chacho.streetbite;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.appcompat.widget.AppCompatImageView;

/**
 * Imagen a pantalla completa con pellizco para acercar y arrastre para mover.
 * Se usa en el visor de las fotos del menú.
 */
public class ZoomImageView extends AppCompatImageView {

    private static final float ESCALA_MAXIMA = 4f;
    private static final float ESCALA_DOBLE_TOQUE = 2.5f;

    private final Matrix matriz = new Matrix();
    private final ScaleGestureDetector detectorEscala;
    private final GestureDetector detectorGestos;

    private float escala = 1f;

    private float ultimoX;
    private float ultimoY;
    private float inicioX;
    private float inicioY;

    private boolean moviendo;

    public ZoomImageView(Context contexto) {
        this(contexto, null);
    }

    public ZoomImageView(Context contexto, AttributeSet atributos) {
        super(contexto, atributos);

        setScaleType(ScaleType.MATRIX);

        detectorEscala = new ScaleGestureDetector(
                contexto,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {

                        float factor = detector.getScaleFactor();
                        float destino = escala * factor;

                        if (destino < 1f) {
                            factor = 1f / escala;
                            destino = 1f;
                        } else if (destino > ESCALA_MAXIMA) {
                            factor = ESCALA_MAXIMA / escala;
                            destino = ESCALA_MAXIMA;
                        }

                        escala = destino;

                        matriz.postScale(
                                factor,
                                factor,
                                detector.getFocusX(),
                                detector.getFocusY()
                        );

                        ajustarLimites();
                        setImageMatrix(matriz);

                        return true;
                    }
                }
        );

        detectorGestos = new GestureDetector(
                contexto,
                new GestureDetector.SimpleOnGestureListener() {

                    @Override
                    public boolean onDoubleTap(MotionEvent evento) {

                        if (escala > 1.05f) {
                            reiniciar();
                        } else {
                            aplicarZoom(
                                    ESCALA_DOBLE_TOQUE,
                                    evento.getX(),
                                    evento.getY()
                            );
                        }

                        return true;
                    }
                }
        );
    }

    @Override
    public void setImageBitmap(android.graphics.Bitmap bitmap) {
        super.setImageBitmap(bitmap);
        post(this::reiniciar);
    }

    @Override
    protected void onSizeChanged(int ancho, int alto,
                                 int anchoAnterior, int altoAnterior) {
        super.onSizeChanged(ancho, alto, anchoAnterior, altoAnterior);
        reiniciar();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent evento) {

        detectorEscala.onTouchEvent(evento);
        detectorGestos.onTouchEvent(evento);

        switch (evento.getActionMasked()) {

            case MotionEvent.ACTION_DOWN:
                ultimoX = evento.getX();
                ultimoY = evento.getY();
                inicioX = evento.getX();
                inicioY = evento.getY();
                moviendo = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!detectorEscala.isInProgress()) {

                    float dx = evento.getX() - ultimoX;
                    float dy = evento.getY() - ultimoY;

                    matriz.postTranslate(dx, dy);
                    ajustarLimites();
                    setImageMatrix(matriz);

                    ultimoX = evento.getX();
                    ultimoY = evento.getY();

                    if (Math.abs(evento.getX() - inicioX) > 12
                            || Math.abs(evento.getY() - inicioY) > 12) {
                        moviendo = true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!moviendo) {
                    performClick();
                }
                break;

            default:
                break;
        }

        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** Vuelve a mostrar la imagen completa y centrada. */
    private void reiniciar() {

        escala = 1f;
        matriz.reset();

        Drawable imagen = getDrawable();

        if (imagen == null || getWidth() == 0 || getHeight() == 0) {
            setImageMatrix(matriz);
            return;
        }

        float anchoImagen = imagen.getIntrinsicWidth();
        float altoImagen = imagen.getIntrinsicHeight();

        if (anchoImagen <= 0 || altoImagen <= 0) {
            setImageMatrix(matriz);
            return;
        }

        float ajuste = Math.min(
                getWidth() / anchoImagen,
                getHeight() / altoImagen
        );

        float dx = (getWidth() - anchoImagen * ajuste) / 2f;
        float dy = (getHeight() - altoImagen * ajuste) / 2f;

        matriz.setScale(ajuste, ajuste);
        matriz.postTranslate(dx, dy);

        setImageMatrix(matriz);
    }

    private void aplicarZoom(float destino, float focoX, float focoY) {

        float factor = destino / escala;
        escala = destino;

        matriz.postScale(factor, factor, focoX, focoY);
        ajustarLimites();
        setImageMatrix(matriz);
    }

    /**
     * Si la imagen es más chica que la pantalla, la centra; si es más grande,
     * impide que se arrastre dejando bordes vacíos.
     */
    private void ajustarLimites() {

        Drawable imagen = getDrawable();

        if (imagen == null) {
            return;
        }

        RectF area = new RectF(
                0,
                0,
                imagen.getIntrinsicWidth(),
                imagen.getIntrinsicHeight()
        );

        matriz.mapRect(area);

        float dx = 0;
        float dy = 0;

        if (area.width() <= getWidth()) {
            dx = (getWidth() - area.width()) / 2f - area.left;
        } else if (area.left > 0) {
            dx = -area.left;
        } else if (area.right < getWidth()) {
            dx = getWidth() - area.right;
        }

        if (area.height() <= getHeight()) {
            dy = (getHeight() - area.height()) / 2f - area.top;
        } else if (area.top > 0) {
            dy = -area.top;
        } else if (area.bottom < getHeight()) {
            dy = getHeight() - area.bottom;
        }

        if (dx != 0 || dy != 0) {
            matriz.postTranslate(dx, dy);
        }
    }
}
