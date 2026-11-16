package com.chacho.streetbite;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carga imágenes locales sin bloquear la interfaz.
 *
 * Las fotos reales de los locales viven en res/drawable-nodpi y se referencian
 * por nombre desde Supabase. Acá se decodifican al tamaño que necesita cada
 * vista, se cachean en memoria y se descartan si la vista ya se recicló.
 *
 * Se evita una librería externa a propósito: son pocas imágenes y todas
 * vienen incluidas en la app.
 */
public final class Imagenes {

    private static final ExecutorService EJECUTOR =
            Executors.newFixedThreadPool(2);

    private static final Handler PRINCIPAL =
            new Handler(Looper.getMainLooper());

    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(tamanoCacheKb()) {

                @Override
                protected int sizeOf(String clave, Bitmap bitmap) {
                    return bitmap.getAllocationByteCount() / 1024;
                }
            };

    private Imagenes() {
    }

    /** Carga una imagen local ajustada al tamaño de la vista, en dp. */
    public static void cargar(ImageView vista, String nombre,
                              int anchoDp, int altoDp) {

        if (vista == null) {
            return;
        }

        float densidad = vista.getResources()
                .getDisplayMetrics().density;

        cargarPx(
                vista,
                nombre,
                Math.max(1, Math.round(anchoDp * densidad)),
                Math.max(1, Math.round(altoDp * densidad))
        );
    }

    /** Variante en píxeles, para pantallas completas. */
    public static void cargarPx(ImageView vista, String nombre,
                                int anchoPx, int altoPx) {

        if (vista == null) {
            return;
        }

        if (nombre == null || nombre.trim().isEmpty()) {
            limpiar(vista);
            return;
        }

        final String limpio = nombre.trim().toLowerCase(Locale.ROOT);
        final String clave = limpio + "@" + anchoPx + "x" + altoPx;

        Bitmap enMemoria = CACHE.get(clave);

        if (enMemoria != null) {
            vista.setTag(R.id.tag_imagen, clave);
            vista.setImageBitmap(enMemoria);
            return;
        }

        // Marca esta carga como la vigente para esta vista.
        vista.setTag(R.id.tag_imagen, clave);

        final Resources recursos = vista.getResources();
        final String paquete = vista.getContext().getPackageName();

        EJECUTOR.execute(() -> {

            Bitmap bitmap = decodificar(
                    recursos, paquete, limpio, anchoPx, altoPx
            );

            if (bitmap == null) {
                return;
            }

            CACHE.put(clave, bitmap);

            PRINCIPAL.post(() -> {
                if (clave.equals(vista.getTag(R.id.tag_imagen))) {
                    vista.setImageBitmap(bitmap);
                }
            });
        });
    }

    /**
     * Carga inmediata para una vista que debe estar lista antes de arrancar
     * una transición compartida. Solo se usa con imágenes pequeñas.
     */
    public static void cargarAhora(ImageView vista, String nombre,
                                   int anchoDp, int altoDp) {

        if (vista == null) {
            return;
        }

        if (nombre == null || nombre.trim().isEmpty()) {
            limpiar(vista);
            return;
        }

        float densidad = vista.getResources()
                .getDisplayMetrics().density;

        int anchoPx = Math.max(1, Math.round(anchoDp * densidad));
        int altoPx = Math.max(1, Math.round(altoDp * densidad));

        String limpio = nombre.trim().toLowerCase(Locale.ROOT);
        String clave = limpio + "@" + anchoPx + "x" + altoPx;

        Bitmap enMemoria = CACHE.get(clave);

        if (enMemoria != null) {
            vista.setTag(R.id.tag_imagen, clave);
            vista.setImageBitmap(enMemoria);
            return;
        }

        Bitmap bitmap = decodificar(
                vista.getResources(),
                vista.getContext().getPackageName(),
                limpio,
                anchoPx,
                altoPx
        );

        if (bitmap == null) {
            return;
        }

        CACHE.put(clave, bitmap);
        vista.setTag(R.id.tag_imagen, clave);
        vista.setImageBitmap(bitmap);
    }

    /** Descarta una carga pendiente cuando la vista se recicla. */
    public static void limpiar(ImageView vista) {

        if (vista == null) {
            return;
        }

        vista.setTag(R.id.tag_imagen, null);
        vista.setImageDrawable(null);
    }

    /** Resuelve el nombre guardado en Supabase a un drawable local. */
    public static int recurso(Context contexto, String nombre) {

        if (contexto == null || nombre == null || nombre.trim().isEmpty()) {
            return 0;
        }

        return recurso(
                contexto.getResources(),
                contexto.getPackageName(),
                nombre.trim().toLowerCase(Locale.ROOT)
        );
    }

    private static int recurso(Resources recursos, String paquete,
                               String nombre) {

        int id = recursos.getIdentifier(nombre, "drawable", paquete);

        if (id != 0) {
            return id;
        }

        // Compatibilidad con los nombres de categoría anteriores.
        return Foodtruck.drawableDe(nombre);
    }

    private static Bitmap decodificar(Resources recursos, String paquete,
                                      String nombre, int anchoPx, int altoPx) {

        int id = recursos.getIdentifier(nombre, "drawable", paquete);

        if (id == 0) {
            return null;
        }

        BitmapFactory.Options medidas = new BitmapFactory.Options();
        medidas.inJustDecodeBounds = true;
        medidas.inScaled = false;
        BitmapFactory.decodeResource(recursos, id, medidas);

        int muestra = 1;

        while ((medidas.outWidth / (muestra * 2)) >= anchoPx
                && (medidas.outHeight / (muestra * 2)) >= altoPx) {
            muestra *= 2;
        }

        BitmapFactory.Options opciones = new BitmapFactory.Options();
        opciones.inSampleSize = muestra;
        opciones.inScaled = false;

        try {
            return BitmapFactory.decodeResource(recursos, id, opciones);
        } catch (OutOfMemoryError e) {
            CACHE.evictAll();
            return null;
        }
    }

    private static int tamanoCacheKb() {

        int maximoKb = (int) (Runtime.getRuntime().maxMemory() / 1024);
        return Math.max(8 * 1024, maximoKb / 8);
    }
}
