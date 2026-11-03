package com.chacho.streetbite;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Comunicación con Supabase.
 *
 * Autenticación: /auth/v1/... (Supabase Auth).
 * Perfil:        /rest/v1/usuarios (PostgREST), usando el access_token del
 *                usuario para que las políticas RLS se apliquen.
 */
public final class SupabaseHelper {

    private static final String TAG = "SupabaseHelper";

    private static final String TABLA_USUARIOS = "/rest/v1/usuarios";

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private static final MediaType JSON =
            MediaType.get("application/json; charset=utf-8");

    private SupabaseHelper() {
    }

    public interface AuthCallback {
        void onSuccess(JSONObject respuesta);

        void onError(String mensaje);

        /**
         * Se llama cuando no hay conexión con el servidor.
         * Por defecto se trata como un error normal.
         */
        default void onSinConexion(String mensaje) {
            onError(mensaje);
        }
    }

    // ============================================================
    // AUTENTICACIÓN (Supabase Auth / GoTrue)
    // ============================================================

    public static void registrarUsuario(
            String nombre,
            String correo,
            String password,
            AuthCallback callback) {

        try {
            JSONObject datosUsuario = new JSONObject();
            datosUsuario.put("full_name", nombre);

            JSONObject solicitud = new JSONObject();
            solicitud.put("email", correo);
            solicitud.put("password", password);
            solicitud.put("data", datosUsuario);

            enviarPeticionAuth("/auth/v1/signup", solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo preparar el registro.");
        }
    }

    public static void iniciarSesion(
            String correo,
            String password,
            AuthCallback callback) {

        try {
            JSONObject solicitud = new JSONObject();
            solicitud.put("email", correo);
            solicitud.put("password", password);

            enviarPeticionAuth(
                    "/auth/v1/token?grant_type=password",
                    solicitud,
                    callback
            );

        } catch (Exception e) {
            callback.onError("No se pudo preparar el inicio de sesión.");
        }
    }

    public static void intercambiarCodigoPkce(
            String codigo,
            String verificador,
            AuthCallback callback) {

        try {
            JSONObject solicitud = new JSONObject();
            solicitud.put("auth_code", codigo);
            solicitud.put("code_verifier", verificador);

            enviarPeticionAuth(
                    "/auth/v1/token?grant_type=pkce",
                    solicitud,
                    callback
            );

        } catch (Exception e) {
            callback.onError(
                    "No se pudo completar el inicio de sesión con Discord."
            );
        }
    }

    public static void refrescarSesion(
            String refreshToken,
            AuthCallback callback) {

        try {
            JSONObject solicitud = new JSONObject();
            solicitud.put("refresh_token", refreshToken);

            enviarPeticionAuth(
                    "/auth/v1/token?grant_type=refresh_token",
                    solicitud,
                    callback
            );

        } catch (Exception e) {
            callback.onError("No se pudo renovar la sesión.");
        }
    }

    public static void recuperarContrasena(
            String correo,
            AuthCallback callback) {

        try {
            JSONObject solicitud = new JSONObject();
            solicitud.put("email", correo);

            enviarPeticionAuth("/auth/v1/recover", solicitud, callback);

        } catch (Exception e) {
            callback.onError(
                    "No se pudo enviar el correo de recuperación."
            );
        }
    }

    public static void cerrarSesion(
            String accessToken,
            AuthCallback callback) {

        Request solicitud = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/logout")
                .post(RequestBody.create(new byte[0], JSON))
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        ejecutar(solicitud, callback);
    }

    // ============================================================
    // PERFIL (PostgREST / tabla public.usuarios)
    // ============================================================

    /**
     * Obtiene el usuario autenticado desde /auth/v1/user.
     * Sirve para completar la sesión cuando la respuesta no trae "user".
     */
    public static void obtenerUsuario(
            String accessToken,
            AuthCallback callback) {

        Request solicitud = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        ejecutar(solicitud, callback);
    }

    public static void obtenerPerfil(
            String accessToken,
            String usuarioId,
            AuthCallback callback) {

        String url = SupabaseConfig.SUPABASE_URL
                + TABLA_USUARIOS
                + "?usuario_id=eq." + usuarioId
                + "&select=usuario_id,nombre,correo,created_at";

        Request solicitud = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        ejecutar(solicitud, callback);
    }

    /**
     * Crea o actualiza la fila del usuario autenticado.
     * El usuario_id debe coincidir con auth.uid() para que RLS lo permita.
     */
    public static void guardarPerfil(
            String accessToken,
            String usuarioId,
            String nombre,
            String correo,
            AuthCallback callback) {

        try {
            JSONObject perfil = new JSONObject();
            perfil.put("usuario_id", usuarioId);
            perfil.put("nombre", nombre);
            perfil.put("correo", correo);

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL
                            + TABLA_USUARIOS
                            + "?on_conflict=usuario_id")
                    .post(RequestBody.create(perfil.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader(
                            "Prefer",
                            "resolution=merge-duplicates,return=representation"
                    )
                    .build();

            ejecutar(solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo preparar el perfil.");
        }
    }

    /**
     * Toma los datos de una respuesta de Auth (signup, login, /user) y los
     * guarda en public.usuarios. Si algo falla, la sesión sigue siendo válida.
     */
    public static void sincronizarPerfil(
            String accessToken,
            JSONObject respuestaAuth,
            AuthCallback callback) {

        JSONObject perfil = extraerDatosPerfil(respuestaAuth);

        if (perfil == null) {
            callback.onError(
                    "No se pudieron obtener los datos del usuario."
            );
            return;
        }

        guardarPerfil(
                accessToken,
                perfil.optString("usuario_id", ""),
                perfil.optString("nombre", ""),
                perfil.optString("correo", ""),
                callback
        );
    }

    /**
     * Devuelve {usuario_id, nombre, correo} a partir de una respuesta de Auth.
     * Acepta tanto la respuesta con "user" como la de /auth/v1/user.
     */
    public static JSONObject extraerDatosPerfil(JSONObject respuestaAuth) {
        if (respuestaAuth == null) {
            return null;
        }

        JSONObject usuario = respuestaAuth.optJSONObject("user");

        if (usuario == null) {
            usuario = respuestaAuth;
        }

        String id = usuario.optString("id", "");
        String correo = usuario.optString("email", "");

        if (id.isEmpty() || correo.isEmpty()) {
            return null;
        }

        JSONObject metadatos = usuario.optJSONObject("user_metadata");
        String nombre = "";

        if (metadatos != null) {
            nombre = metadatos.optString(
                    "full_name",
                    metadatos.optString("name", "")
            );
        }

        if (nombre.isEmpty()) {
            int arroba = correo.indexOf('@');
            nombre = arroba > 0 ? correo.substring(0, arroba) : correo;
        }

        try {
            JSONObject perfil = new JSONObject();
            perfil.put("usuario_id", id);
            perfil.put("nombre", nombre);
            perfil.put("correo", correo);
            return perfil;
        } catch (Exception e) {
            return null;
        }
    }

    // ============================================================
    // HTTP
    // ============================================================

    private static void enviarPeticionAuth(
            String endpoint,
            JSONObject datos,
            AuthCallback callback) {

        Request solicitud = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + endpoint)
                .post(RequestBody.create(datos.toString(), JSON))
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        ejecutar(solicitud, callback);
    }

    private static void ejecutar(Request solicitud, AuthCallback callback) {
        CLIENT.newCall(solicitud).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error de conexión con Supabase", e);

                callback.onSinConexion(
                        "No se pudo conectar con Supabase. "
                                + "Revisa tu conexión."
                );
            }

            @Override
            public void onResponse(Call call, Response response)
                    throws IOException {

                try (Response respuesta = response) {
                    String contenido = respuesta.body() != null
                            ? respuesta.body().string()
                            : "";

                    JSONObject resultado = parsear(contenido);

                    if (respuesta.isSuccessful()) {
                        callback.onSuccess(resultado);
                    } else {
                        callback.onError(
                                mensajeError(resultado, respuesta.code())
                        );
                    }
                }
            }
        });
    }

    private static JSONObject parsear(String contenido) {
        if (contenido == null || contenido.trim().isEmpty()) {
            return new JSONObject();
        }

        try {
            String limpio = contenido.trim();

            // PostgREST devuelve un arreglo; usamos la primera fila.
            if (limpio.startsWith("[")) {
                JSONArray arreglo = new JSONArray(limpio);

                if (arreglo.length() > 0) {
                    return arreglo.getJSONObject(0);
                }

                return new JSONObject();
            }

            if (limpio.startsWith("{")) {
                return new JSONObject(limpio);
            }
        } catch (Exception e) {
            Log.w(TAG, "Respuesta no JSON de Supabase", e);
        }

        return new JSONObject();
    }

    private static String mensajeError(JSONObject resultado, int codigo) {
        String mensaje = resultado.optString(
                "msg",
                resultado.optString(
                        "message",
                        resultado.optString(
                                "error_description",
                                resultado.optString(
                                        "error",
                                        "No se pudo completar la solicitud."
                                )
                        )
                )
        );

        String minusculas = mensaje.toLowerCase(Locale.ROOT);

        if (minusculas.contains("invalid login credentials")) {
            return "Correo o contraseña incorrectos.";
        }

        if (minusculas.contains("user already registered")
                || minusculas.contains("already been registered")) {
            return "Ya existe una cuenta con este correo.";
        }

        if (minusculas.contains("email not confirmed")) {
            return "Debes confirmar tu correo antes de iniciar sesión.";
        }

        if (minusculas.contains("password should be at least")) {
            return "La contraseña es demasiado corta.";
        }

        if (codigo == 401) {
            return "Tu sesión expiró. Vuelve a iniciar sesión.";
        }

        if (codigo == 429) {
            return "Demasiados intentos. Espera un momento.";
        }

        return mensaje;
    }
}
