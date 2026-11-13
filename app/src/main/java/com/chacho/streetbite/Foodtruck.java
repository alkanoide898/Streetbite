package com.chacho.streetbite;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import org.json.JSONObject;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modelo único de foodtruck. Lo usan HomeActivity y MapActivity,
 * así los datos y la lógica de búsqueda existen en un solo lugar.
 *
 * Los datos reales vienen de public.foodtrucks (Supabase). El campo
 * "imagen" es el nombre del drawable local: tacos, burguer, pizza, completos.
 *
 * "categoria" puede contener varias categorías separadas por coma
 * (por ejemplo "hamburguesas, completos, churrascos"). La app las
 * separa para generar los chips y para filtrar.
 */
public class Foodtruck {

    public final String id;
    public final String nombre;
    public final String descripcion;
    public final String categoria;
    public final double rating;
    public final double lat;
    public final double lon;
    public final boolean abierto;
    public final String imagenNombre;
    public final int imagen;
    public final String direccion;
    public final String horario;
    public final String telefono;
    public final String instagram;

    public Foodtruck(String id,
                     String nombre,
                     String descripcion,
                     String categoria,
                     double rating,
                     double lat,
                     double lon,
                     boolean abierto,
                     String imagenNombre,
                     String direccion,
                     String horario,
                     String telefono,
                     String instagram) {
        this.id = id == null ? "" : id;
        this.nombre = nombre == null ? "" : nombre;
        this.descripcion = descripcion == null ? "" : descripcion;
        this.categoria = categoria == null ? "" : categoria;
        this.rating = rating;
        this.lat = lat;
        this.lon = lon;
        this.abierto = abierto;
        this.imagenNombre = imagenNombre == null ? "tacos" : imagenNombre;
        this.imagen = drawableDe(this.imagenNombre);
        this.direccion = direccion == null ? "" : direccion;
        this.horario = horario == null ? "" : horario;
        this.telefono = telefono == null ? "" : telefono;
        this.instagram = instagram == null ? "" : instagram;
    }

    /**
     * Construye un Foodtruck a partir de una fila de PostgREST.
     * Tolera columnas nulas y valores faltantes.
     */
    public static Foodtruck desdeJson(JSONObject fila) {
        if (fila == null) {
            return null;
        }

        return new Foodtruck(
                texto(fila, "id"),
                texto(fila, "nombre"),
                texto(fila, "descripcion"),
                texto(fila, "categoria"),
                fila.optDouble("rating", 0),
                fila.optDouble("lat", 0),
                fila.optDouble("lon", 0),
                fila.optBoolean("abierto", true),
                texto(fila, "imagen"),
                texto(fila, "direccion"),
                texto(fila, "horario"),
                texto(fila, "telefono"),
                texto(fila, "instagram")
        );
    }

    private static String texto(JSONObject fila, String columna) {
        return fila.isNull(columna) ? "" : fila.optString(columna, "");
    }

    /** Traduce el nombre guardado en la BD al drawable local. */
    public static int drawableDe(String nombre) {
        String valor = nombre == null
                ? ""
                : nombre.trim().toLowerCase(Locale.ROOT);

        switch (valor) {
            case "burguer":
            case "burger":
            case "hamburguesa":
            case "hamburguesas":
                return R.drawable.burguer;
            case "pizza":
            case "pizzas":
                return R.drawable.pizza;
            case "completos":
            case "completo":
                return R.drawable.completos;
            case "tacos":
            case "taco":
            default:
                return R.drawable.tacos;
        }
    }

    /** Lista de categorías separadas por coma, ya limpias. */
    public List<String> categorias() {
        List<String> lista = new ArrayList<>();

        if (categoria == null || categoria.trim().isEmpty()) {
            return lista;
        }

        for (String parte : categoria.split(",")) {

            String limpia = parte.trim();

            if (!limpia.isEmpty()) {
                lista.add(limpia);
            }
        }

        return lista;
    }

    /** true si el foodtruck pertenece a la categoría indicada. */
    public boolean tieneCategoria(String buscada) {
        if (buscada == null) {
            return true;
        }

        String objetivo = normalizar(buscada);

        for (String categoria : categorias()) {
            if (normalizar(categoria).equals(objetivo)) {
                return true;
            }
        }

        return false;
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

    /** Enlace de WhatsApp listo para abrir, o null si no hay teléfono. */
    public String whatsappUrl() {
        String digitos = telefono.replaceAll("[^0-9]", "");

        if (digitos.isEmpty()) {
            return null;
        }

        return "https://wa.me/" + digitos;
    }

    /** Enlace del perfil de Instagram, o null si no hay usuario. */
    public String instagramUrl() {
        String usuario = instagram.trim();

        if (usuario.startsWith("@")) {
            usuario = usuario.substring(1);
        }

        if (usuario.isEmpty()) {
            return null;
        }

        return "https://instagram.com/" + usuario;
    }

    /** Intent listo para abrir DetalleFoodtruckActivity con los mismos extras de siempre. */
    public Intent crearIntentDetalle(Context contexto, Location usuario) {
        Intent intent = new Intent(contexto, DetalleFoodtruckActivity.class);

        intent.putExtra("id", id);
        intent.putExtra("nombre", nombre);
        intent.putExtra("descripcion", descripcion);
        intent.putExtra("categoria", categoria);
        intent.putExtra("rating", ratingTexto());
        intent.putExtra("distancia", distanciaTexto(usuario));
        intent.putExtra("estado", estadoTexto());
        intent.putExtra("imagen", imagen);
        intent.putExtra("direccion", direccion);
        intent.putExtra("horario", horario);
        intent.putExtra("telefono", telefono);
        intent.putExtra("instagram", instagram);

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
