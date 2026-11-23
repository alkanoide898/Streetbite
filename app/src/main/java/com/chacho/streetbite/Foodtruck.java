package com.chacho.streetbite;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONArray;
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
 * "imagen" es el nombre del drawable de respaldo (tacos, burguer, pizza,
 * completos). "logo" y "menu" apuntan a las fotos reales incluidas en la app.
 *
 * "categoria" puede contener varias categorías separadas por coma
 * (por ejemplo "hamburguesas, completos, churrascos"). La app las
 * separa para generar los chips y para filtrar.
 */
public class Foodtruck {

    /** Centro de San Carlos, Ñuble y radio válido para distancias reales. */
    public static final double SAN_CARLOS_LAT = -36.4248;
    public static final double SAN_CARLOS_LON = -71.9581;
    public static final float RADIO_SAN_CARLOS_METROS = 25000f;

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
    public final String logoNombre;
    public final List<String> menu;
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
                     String logoNombre,
                     List<String> menu,
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
        this.logoNombre = logoNombre == null ? "" : logoNombre.trim();
        this.menu = menu == null
                ? new ArrayList<>()
                : new ArrayList<>(menu);
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
                texto(fila, "logo"),
                menuDesde(fila),
                texto(fila, "direccion"),
                texto(fila, "horario"),
                texto(fila, "telefono"),
                texto(fila, "instagram")
        );
    }

    private static String texto(JSONObject fila, String columna) {
        return fila.isNull(columna) ? "" : fila.optString(columna, "");
    }

    /**
     * PostgREST devuelve text[] como arreglo JSON. También se tolera una
     * cadena separada por comas por si la fila se cargó a mano.
     */
    private static List<String> menuDesde(JSONObject fila) {

        List<String> fotos = new ArrayList<>();

        if (fila == null || fila.isNull("menu")) {
            return fotos;
        }

        Object valor = fila.opt("menu");

        if (valor instanceof JSONArray) {

            JSONArray arreglo = (JSONArray) valor;

            for (int i = 0; i < arreglo.length(); i++) {
                String foto = arreglo.optString(i, "").trim();

                if (!foto.isEmpty()) {
                    fotos.add(foto);
                }
            }

            return fotos;
        }

        for (String foto : fila.optString("menu", "").split(",")) {

            String limpia = foto.trim();

            if (!limpia.isEmpty()) {
                fotos.add(limpia);
            }
        }

        return fotos;
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
        return String.format(Locale.getDefault(), "%.1f", rating);
    }

    public String estadoTexto() {
        return abierto ? "Abierto" : "Cerrado";
    }

    /**
     * Devuelve el GPS si está dentro de 25 km de San Carlos; si es nulo o está
     * fuera (ej. emulador en otra ciudad/país), usa el centro de San Carlos.
     */
    public static Location ubicacionEnSanCarlos(Location gps) {
        Location sanCarlos = new Location("sancarlos");
        sanCarlos.setLatitude(SAN_CARLOS_LAT);
        sanCarlos.setLongitude(SAN_CARLOS_LON);

        if (gps == null) {
            return sanCarlos;
        }

        float[] resultado = new float[1];
        Location.distanceBetween(
                gps.getLatitude(), gps.getLongitude(),
                SAN_CARLOS_LAT, SAN_CARLOS_LON,
                resultado
        );

        return resultado[0] <= RADIO_SAN_CARLOS_METROS ? gps : sanCarlos;
    }

    /** Distancia en metros desde el usuario (o centro de San Carlos). */
    public float distanciaMetros(Location usuario) {
        Location origen = ubicacionEnSanCarlos(usuario);
        float[] resultado = new float[1];
        Location.distanceBetween(
                origen.getLatitude(), origen.getLongitude(),
                lat, lon,
                resultado
        );
        return resultado[0];
    }

    /** Abre la app de mapas (Google Maps / Waze) con ruta hasta el local. */
    public static void abrirComoLlegar(
            Context contexto,
            double lat,
            double lon,
            String nombre) {

        String etiqueta = (nombre == null || nombre.trim().isEmpty())
                ? "Foodtruck"
                : nombre.trim();

        Uri geoUri = Uri.parse(
                "geo:" + lat + "," + lon
                        + "?q=" + lat + "," + lon
                        + "(" + Uri.encode(etiqueta) + ")"
        );

        try {
            contexto.startActivity(new Intent(Intent.ACTION_VIEW, geoUri));
        } catch (Exception e) {
            try {
                Uri webUri = Uri.parse(
                        "https://www.google.com/maps/dir/?api=1&destination="
                                + lat + "," + lon
                );
                contexto.startActivity(new Intent(Intent.ACTION_VIEW, webUri));
            } catch (Exception ex) {
                Toast.makeText(
                        contexto,
                        "No se pudo abrir la navegación.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        }
    }

    /** Distancia real entre el usuario y el foodtruck. Si no hay ubicación, muestra "--". */
    public String distanciaTexto(Location usuario) {
        if (usuario == null) {
            return "-- km";
        }

        float[] resultado = new float[1];
        Location.distanceBetween(
                usuario.getLatitude(), usuario.getLongitude(),
                lat, lon,
                resultado
        );

        float metros = resultado[0];

        if (metros < 1000) {
            return Math.round(metros) + " m";
        }

        return String.format(Locale.getDefault(), "%.1f km", metros / 1000f);
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
        intent.putExtra("logo", logoNombre);
        intent.putExtra("menu", menu.toArray(new String[0]));
        intent.putExtra("direccion", direccion);
        intent.putExtra("horario", horario);
        intent.putExtra("telefono", telefono);
        intent.putExtra("instagram", instagram);
        intent.putExtra("lat", lat);
        intent.putExtra("lon", lon);

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
