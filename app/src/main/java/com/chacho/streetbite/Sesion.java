package com.chacho.streetbite;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/**
 * Único punto de acceso a la sesión de Supabase Auth.
 *
 * Guarda el access_token, refresh_token, la expiración y los datos básicos
 * del usuario. Así las Activities no repiten las mismas claves de
 * SharedPreferences por todos lados.
 */
public final class Sesion {

    private static final String PREFS = "StreetBiteAuth";

    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String EXPIRA_EN = "expires_at";
    private static final String USER_ID = "user_id";
    private static final String USER_NOMBRE = "user_nombre";
    private static final String USER_CORREO = "user_correo";

    // Renovar unos segundos antes evita usar un token que vence en el camino.
    private static final long MARGEN_EXPIRACION_MS = 60_000L;

    private Sesion() {
    }

    private static SharedPreferences preferencias(Context contexto) {
        return contexto.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Guarda la respuesta de /auth/v1/token, /auth/v1/signup o /auth/v1/user.
     * Si la respuesta no trae "user", conserva los datos ya guardados.
     */
    public static void guardarRespuestaAuth(Context contexto, JSONObject respuesta) {
        if (respuesta == null) {
            return;
        }

        SharedPreferences.Editor editor = preferencias(contexto).edit();

        String accessToken = respuesta.optString("access_token", "");
        String refreshToken = respuesta.optString("refresh_token", "");

        if (!accessToken.isEmpty()) {
            editor.putString(ACCESS_TOKEN, accessToken);
        }

        if (!refreshToken.isEmpty()) {
            editor.putString(REFRESH_TOKEN, refreshToken);
        }

        long expiraEnSegundos = respuesta.optLong("expires_in", 0);

        if (expiraEnSegundos > 0 && !accessToken.isEmpty()) {
            long expiraEn = System.currentTimeMillis()
                    + (expiraEnSegundos * 1000L);

            editor.putLong(EXPIRA_EN, expiraEn);
        }

        editor.apply();

        JSONObject usuario = respuesta.optJSONObject("user");

        // /auth/v1/user devuelve el usuario directamente, sin envolverlo.
        if (usuario == null && respuesta.has("id")) {
            usuario = respuesta;
        }

        if (usuario != null) {
            guardarUsuario(contexto, usuario);
        }
    }

    /**
     * Guarda los datos del usuario que vienen dentro de una respuesta de Auth.
     */
    public static void guardarUsuario(Context contexto, JSONObject usuario) {
        if (usuario == null) {
            return;
        }

        String id = usuario.optString("id", "");
        String correo = usuario.optString("email", "");

        JSONObject metadatos = usuario.optJSONObject("user_metadata");
        String nombre = "";

        if (metadatos != null) {
            nombre = metadatos.optString(
                    "full_name",
                    metadatos.optString("name", "")
            );
        }

        if (nombre.isEmpty()) {
            nombre = usuario.optString("nombre", "");
        }

        SharedPreferences.Editor editor = preferencias(contexto).edit();

        if (!id.isEmpty()) {
            editor.putString(USER_ID, id);
        }

        if (!nombre.isEmpty()) {
            editor.putString(USER_NOMBRE, nombre);
        }

        if (!correo.isEmpty()) {
            editor.putString(USER_CORREO, correo);
        }

        editor.apply();
    }

    /**
     * Guarda una fila de la tabla public.usuarios.
     */
    public static void guardarPerfil(Context contexto, JSONObject perfil) {
        if (perfil == null) {
            return;
        }

        String id = perfil.optString(
                "usuario_id",
                perfil.optString("id", "")
        );

        String nombre = perfil.optString("nombre", "");
        String correo = perfil.optString("correo", "");

        SharedPreferences.Editor editor = preferencias(contexto).edit();

        if (!id.isEmpty()) {
            editor.putString(USER_ID, id);
        }

        if (!nombre.isEmpty()) {
            editor.putString(USER_NOMBRE, nombre);
        }

        if (!correo.isEmpty()) {
            editor.putString(USER_CORREO, correo);
        }

        editor.apply();
    }

    public static boolean haySesion(Context contexto) {
        return !accessToken(contexto).isEmpty()
                && !refreshToken(contexto).isEmpty();
    }

    public static boolean estaExpirada(Context contexto) {
        long expiraEn = preferencias(contexto).getLong(EXPIRA_EN, 0L);

        if (expiraEn <= 0L) {
            // Si no conocemos la expiración, intentamos usar el token.
            return false;
        }

        return System.currentTimeMillis() >= (expiraEn - MARGEN_EXPIRACION_MS);
    }

    public static String accessToken(Context contexto) {
        return preferencias(contexto).getString(ACCESS_TOKEN, "");
    }

    public static String refreshToken(Context contexto) {
        return preferencias(contexto).getString(REFRESH_TOKEN, "");
    }

    public static String usuarioId(Context contexto) {
        return preferencias(contexto).getString(USER_ID, "");
    }

    public static String nombre(Context contexto) {
        return preferencias(contexto).getString(USER_NOMBRE, "");
    }

    public static String correo(Context contexto) {
        return preferencias(contexto).getString(USER_CORREO, "");
    }

    public static void limpiar(Context contexto) {
        preferencias(contexto).edit().clear().apply();
    }
}
