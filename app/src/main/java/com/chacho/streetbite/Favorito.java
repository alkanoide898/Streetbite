package com.chacho.streetbite;

import org.json.JSONObject;

/**
 * Modelo de un foodtruck guardado como favorito por el usuario autenticado.
 * Corresponde a una fila de public.favoritos en Supabase.
 */
public class Favorito {

    public static final String[] ETIQUETAS = {
            "Favorito",
            "Bajón nocturno",
            "Con amigos",
            "Por probar"
    };

    public final String id;
    public final String usuarioId;
    public final String foodtruckId;
    public final String etiqueta;
    public final String nota;
    public final String createdAt;
    public final String updatedAt;

    public Favorito(String id,
                    String usuarioId,
                    String foodtruckId,
                    String etiqueta,
                    String nota,
                    String createdAt,
                    String updatedAt) {
        this.id = id == null ? "" : id;
        this.usuarioId = usuarioId == null ? "" : usuarioId;
        this.foodtruckId = foodtruckId == null ? "" : foodtruckId;
        this.etiqueta = etiqueta == null || etiqueta.trim().isEmpty()
                ? "Favorito"
                : etiqueta.trim();
        this.nota = nota == null ? "" : nota.trim();
        this.createdAt = createdAt == null ? "" : createdAt;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public static Favorito desdeJson(JSONObject fila) {
        if (fila == null || fila.length() == 0) {
            return null;
        }

        String id = texto(fila, "id");
        String foodtruckId = texto(fila, "foodtruck_id");

        if (id.isEmpty() && foodtruckId.isEmpty()) {
            return null;
        }

        return new Favorito(
                id,
                texto(fila, "usuario_id"),
                foodtruckId,
                texto(fila, "etiqueta"),
                texto(fila, "nota"),
                texto(fila, "created_at"),
                texto(fila, "updated_at")
        );
    }

    /** Devuelve true si el id tiene formato UUID válido de Supabase. */
    public static boolean esUuidValido(String valor) {
        if (valor == null) {
            return false;
        }
        return valor.trim().matches(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                        + "[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
        );
    }

    private static String texto(JSONObject fila, String columna) {
        return fila.isNull(columna) ? "" : fila.optString(columna, "");
    }
}
