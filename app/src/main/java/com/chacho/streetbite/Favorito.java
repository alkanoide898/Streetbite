package com.chacho.streetbite;

import org.json.JSONObject;

/**
 * Modelo de un foodtruck guardado como favorito por el usuario autenticado
 * (o compartido con él por otro usuario registrado).
 * Corresponde a una fila de public.favoritos en Supabase.
 */
public class Favorito {

    public static final String[] ETIQUETAS = {
            "Para almorzar",
            "Bajón nocturno",
            "Con amigos",
            "Por probar"
    };

    public final String id;
    public final String usuarioId;
    public final String foodtruckId;
    public final String etiqueta;
    public final String nota;
    public final String duenoNombre;
    public final String compartidoConId;
    public final String compartidoConNombre;
    public final String compartidoConCorreo;
    public final boolean compartido;
    public final String createdAt;
    public final String updatedAt;

    public Favorito(String id,
                    String usuarioId,
                    String foodtruckId,
                    String etiqueta,
                    String nota,
                    String duenoNombre,
                    String compartidoConId,
                    String compartidoConNombre,
                    String compartidoConCorreo,
                    String createdAt,
                    String updatedAt) {
        this(
                id,
                usuarioId,
                foodtruckId,
                etiqueta,
                nota,
                duenoNombre,
                compartidoConId,
                compartidoConNombre,
                compartidoConCorreo,
                false,
                createdAt,
                updatedAt
        );
    }

    public Favorito(String id,
                    String usuarioId,
                    String foodtruckId,
                    String etiqueta,
                    String nota,
                    String duenoNombre,
                    String compartidoConId,
                    String compartidoConNombre,
                    String compartidoConCorreo,
                    boolean estaCompartidoFlag,
                    String createdAt,
                    String updatedAt) {
        this.id = id == null ? "" : id;
        this.usuarioId = usuarioId == null ? "" : usuarioId;
        this.foodtruckId = foodtruckId == null ? "" : foodtruckId;
        this.etiqueta = etiqueta == null || etiqueta.trim().isEmpty()
                ? "Para almorzar"
                : etiqueta.trim();
        this.nota = nota == null ? "" : nota.trim();
        this.duenoNombre = duenoNombre == null ? "" : duenoNombre.trim();
        this.compartidoConId = compartidoConId == null ? "" : compartidoConId.trim();
        this.compartidoConNombre = compartidoConNombre == null
                ? ""
                : compartidoConNombre.trim();
        this.compartidoConCorreo = compartidoConCorreo == null
                ? ""
                : compartidoConCorreo.trim();
        this.compartido = estaCompartidoFlag
                || !this.compartidoConId.isEmpty()
                || !this.compartidoConNombre.isEmpty()
                || !this.compartidoConCorreo.isEmpty();
        this.createdAt = createdAt == null ? "" : createdAt;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public boolean esDueno(String miUsuarioId) {
        return miUsuarioId != null
                && !miUsuarioId.isEmpty()
                && usuarioId.equals(miUsuarioId);
    }

    public boolean estaCompartido() {
        return compartido;
    }

    /**
     * Devuelve el nombre del otro usuario con quien está compartido el
     * favorito, según quién haya iniciado sesión en este dispositivo.
     */
    public String nombreCompanero(String miUsuarioId) {
        if (!estaCompartido()) {
            return "";
        }

        if (esDueno(miUsuarioId)) {
            if (!compartidoConNombre.isEmpty()) {
                return compartidoConNombre;
            }
            return compartidoConCorreo.isEmpty() ? "un amigo" : compartidoConCorreo;
        }

        return duenoNombre.isEmpty() ? "un amigo" : duenoNombre;
    }

    public static Favorito desdeJson(JSONObject fila) {
        if (fila == null || fila.length() == 0 || fila.optBoolean("retirado", false)) {
            return null;
        }

        String id = texto(fila, "id");
        String foodtruckId = texto(fila, "foodtruck_id");

        if (id.isEmpty() || foodtruckId.isEmpty()) {
            return null;
        }

        return new Favorito(
                id,
                texto(fila, "usuario_id"),
                foodtruckId,
                texto(fila, "etiqueta"),
                texto(fila, "nota"),
                texto(fila, "dueno_nombre"),
                texto(fila, "compartido_con_id"),
                texto(fila, "compartido_con_nombre"),
                texto(fila, "compartido_con_correo"),
                fila.optBoolean("esta_compartido", false),
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
