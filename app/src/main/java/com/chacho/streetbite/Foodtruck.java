package com.chacho.streetbite;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Modelo único de foodtruck. Lo usan HomeActivity y MapActivity,
 * así los datos y la lógica de búsqueda existen en un solo lugar.
 */
public class Foodtruck {

    public final String nombre;
    public final String descripcion;
    public final String categoria; // tacos, hamburguesas, pizza, completos
    public final double rating;
    public final double lat;
    public final double lon;
    public final boolean abierto;
    public final int imagen;

    public Foodtruck(String nombre,
                     String descripcion,
                     String categoria,
                     double rating,
                     double lat,
                     double lon,
                     boolean abierto,
                     int imagen) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.rating = rating;
        this.lat = lat;
        this.lon = lon;
        this.abierto = abierto;
        this.imagen = imagen;
    }

    public String ratingTexto() {
        return String.format(Locale.getDefault(), "⭐ %.1f", rating);
    }

    public String estadoTexto() {
        return abierto ? "🟢 Abierto" : "🔴 Cerrado";
    }

    /** Distancia real entre el usuario y el foodtruck. Si no hay ubicación, muestra "--". */
    public String distanciaTexto(Location usuario) {
        if (usuario == null) {
            return "📍 -- km";
        }

        float[] resultado = new float[1];
        Location.distanceBetween(
                usuario.getLatitude(), usuario.getLongitude(),
                lat, lon,
                resultado
        );

        float metros = resultado[0];

        if (metros < 1000) {
            return "📍 " + Math.round(metros) + " m";
        }

        return String.format(Locale.getDefault(), "📍 %.1f km", metros / 1000f);
    }

    /** Busca por nombre, descripción o categoría, sin importar tildes ni mayúsculas. */
    public boolean coincideCon(String consulta) {
        String q = normalizar(consulta);

        if (q.isEmpty()) {
            return true;
        }

        return normalizar(nombre).contains(q)
                || normalizar(descripcion).contains(q)
                || normalizar(categoria).contains(q);
    }

    /** Intent listo para abrir DetalleFoodtruckActivity con los mismos extras de siempre. */
    public Intent crearIntentDetalle(Context contexto, Location usuario) {
        Intent intent = new Intent(contexto, DetalleFoodtruckActivity.class);

        intent.putExtra("nombre", nombre);
        intent.putExtra("descripcion", descripcion);
        intent.putExtra("rating", ratingTexto());
        intent.putExtra("distancia", distanciaTexto(usuario));
        intent.putExtra("estado", estadoTexto());
        intent.putExtra("imagen", imagen);

        return intent;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }

        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return sinTildes.toLowerCase(Locale.ROOT).trim();
    }
}
