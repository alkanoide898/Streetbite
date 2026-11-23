package com.chacho.streetbite;

import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private static final String TABLA_FAVORITOS = "/rest/v1/favoritos";
    private static final String CAMPOS_FAVORITO =
            "id,usuario_id,foodtruck_id,etiqueta,nota,dueno_nombre,"
                    + "compartido_con_id,compartido_con_nombre,"
                    + "compartido_con_correo,created_at,updated_at";

    // Columnas del catálogo. Las fotos reales se agregaron después, así que
    // existe una versión sin ellas para bases que todavía no se migraron.
    private static final String CAMPOS_BASE =
            "id,nombre,descripcion,categoria,rating,lat,lon,"
                    + "abierto,imagen,direccion,horario,telefono,instagram";

    private static final String CAMPOS_CON_FOTOS =
            "id,nombre,descripcion,categoria,rating,lat,lon,"
                    + "abierto,imagen,logo,menu,direccion,horario,"
                    + "telefono,instagram";

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

    public interface FoodtrucksCallback {
        void onSuccess(List<Foodtruck> foodtrucks);

        void onError(String mensaje);

        default void onSinConexion(String mensaje) {
            onError(mensaje);
        }
    }

    public interface FavoritosCallback {
        void onSuccess(List<Favorito> favoritos);

        void onError(String mensaje);

        default void onSinConexion(String mensaje) {
            onError(mensaje);
        }
    }

    public interface FavoritoCallback {
        void onSuccess(Favorito favorito);

        void onError(String mensaje);

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

            String endpoint = "/auth/v1/recover?redirect_to="
                    + Uri.encode("streetbite://login-callback");
            enviarPeticionAuth(endpoint, solicitud, callback);

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
     * Crea la fila del perfil SOLO si todavía no existe.
     * Se usa al registrarse o al iniciar sesión para no pisar los datos
     * que el usuario ya haya editado desde la pantalla de perfil.
     */
    public static void crearPerfilSiFalta(
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
                            "resolution=ignore-duplicates,return=representation"
                    )
                    .build();

            ejecutar(solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo preparar el perfil.");
        }
    }

    /**
     * Actualiza el nombre en Supabase Auth (user_metadata.full_name).
     * Así el nombre se conserva al cerrar sesión y volver a entrar.
     */
    public static void actualizarNombreAuth(
            String accessToken,
            String nombre,
            AuthCallback callback) {

        try {
            JSONObject metadatos = new JSONObject();
            metadatos.put("full_name", nombre);

            JSONObject datos = new JSONObject();
            datos.put("data", metadatos);

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .put(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .build();

            ejecutar(solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo actualizar el nombre de la cuenta.");
        }
    }

    /**
     * Cambia el correo de la cuenta en Supabase Auth.
     * El correo de auth.users es el que se usa para iniciar sesión; después
     * se actualiza la fila de public.usuarios con el mismo valor.
     */
    public static void cambiarCorreoAuth(
            String accessToken,
            String correo,
            AuthCallback callback) {

        try {
            JSONObject datos = new JSONObject();
            datos.put("email", correo);

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .put(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .build();

            ejecutar(solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo preparar el cambio de correo.");
        }
    }

    /**
     * Cambia la contraseña de la cuenta en Supabase Auth (PUT /auth/v1/user).
     * Sirve tanto con la sesión activa (desde Mi información) como con el
     * access_token de recuperación que entrega el enlace del correo.
     */
    public static void cambiarContrasenaAuth(
            String accessToken,
            String nuevaContrasena,
            AuthCallback callback) {

        try {
            JSONObject datos = new JSONObject();
            datos.put("password", nuevaContrasena);

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + "/auth/v1/user")
                    .put(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .build();

            ejecutar(solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo preparar el cambio de contraseña.");
        }
    }

    /**
     * Crea o actualiza el perfil propio (upsert atómico).
     * Usa POST con resolution=merge-duplicates sobre usuario_id,
     * que tiene un índice único en public.usuarios.
     */
    public static void guardarOActualizarPerfil(
            String accessToken,
            String usuarioId,
            String nombre,
            String correo,
            AuthCallback callback) {

        guardarPerfil(accessToken, usuarioId, nombre, correo, callback);
    }

    /**
     * Elimina la cuenta completa: perfil publico y usuario de Supabase Auth.
     * La app no puede borrar auth.users con el token del usuario, por eso
     * llama a la funcion public.eliminar_mi_cuenta() (SECURITY DEFINER) del
     * archivo supabase/usuarios.sql. El servidor comprueba auth.uid(), asi
     * que cada usuario solo puede borrar su propia cuenta.
     */
    public static void eliminarCuenta(
            String accessToken,
            AuthCallback callback) {

        try {
            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL
                            + "/rest/v1/rpc/eliminar_mi_cuenta")
                    .post(RequestBody.create("{}", JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .build();

            ejecutar(solicitud, callback);

        } catch (Exception e) {
            callback.onError("No se pudo preparar la eliminación.");
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

        crearPerfilSiFalta(
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
    // FOODTRUCKS (PostgREST / tabla public.foodtrucks)
    // ============================================================

    /**
     * Devuelve el catálogo completo desde public.foodtrucks.
     * La lectura es pública (RLS permite SELECT a anon y authenticated),
     * por eso usa la publishable key y no el access_token del usuario.
     */
    public static void listarFoodtrucks(FoodtrucksCallback callback) {
        consultarFoodtrucks(CAMPOS_CON_FOTOS, callback, true);
    }

    /**
     * Pide el catálogo con el listado de columnas indicado.
     *
     * Si la base todavía no tiene las columnas de fotos (código 42703),
     * se reintenta una vez sin ellas para no perder el catálogo remoto.
     */
    private static void consultarFoodtrucks(String campos,
                                            FoodtrucksCallback callback,
                                            boolean permiteReintento) {

        String url = SupabaseConfig.SUPABASE_URL
                + "/rest/v1/foodtrucks"
                + "?select=" + campos
                + "&order=rating.desc,nombre.asc";

        Request solicitud = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader(
                        "Authorization",
                        "Bearer " + SupabaseConfig.SUPABASE_PUBLISHABLE_KEY
                )
                .build();

        CLIENT.newCall(solicitud).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error al cargar foodtrucks", e);

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

                    if (!respuesta.isSuccessful()) {

                        // 400: la tabla aún no tiene logo/menu. Se reintenta
                        // con las columnas base para conservar los datos reales.
                        if (permiteReintento && respuesta.code() == 400) {
                            Log.w(
                                    TAG,
                                    "public.foodtrucks no tiene logo/menu. "
                                            + "Ejecuta supabase/foodtrucks.sql. "
                                            + "Se carga el catálogo sin fotos."
                            );
                            consultarFoodtrucks(
                                    CAMPOS_BASE,
                                    callback,
                                    false
                            );
                            return;
                        }

                        callback.onError(
                                mensajeError(
                                        parsear(contenido),
                                        respuesta.code()
                                )
                        );
                        return;
                    }

                    List<Foodtruck> foodtrucks = new ArrayList<>();

                    try {
                        JSONArray arreglo = new JSONArray(contenido);

                        for (int i = 0; i < arreglo.length(); i++) {
                            Foodtruck foodtruck = Foodtruck.desdeJson(
                                    arreglo.optJSONObject(i)
                            );

                            if (foodtruck != null) {
                                foodtrucks.add(foodtruck);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Respuesta de foodtrucks no válida", e);
                        callback.onError(
                                "No se pudo leer el catálogo de foodtrucks."
                        );
                        return;
                    }

                    callback.onSuccess(foodtrucks);
                }
            }
        });
    }

    // ============================================================
    // FAVORITOS (PostgREST / tabla public.favoritos)
    // ============================================================

    /**
     * Lista los favoritos del usuario autenticado, ordenados por última
     * actualización. RLS garantiza que solo se devuelven sus propias filas.
     */
    public static void listarFavoritos(
            String accessToken,
            String usuarioId,
            FavoritosCallback callback) {

        String url = SupabaseConfig.SUPABASE_URL
                + TABLA_FAVORITOS
                + "?or=(usuario_id.eq." + usuarioId + ",compartido_con_id.eq." + usuarioId + ")"
                + "&select=" + CAMPOS_FAVORITO
                + "&order=updated_at.desc";

        Request solicitud = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        CLIENT.newCall(solicitud).enqueue(new Callback() {

            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error al cargar favoritos", e);
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

                    if (!respuesta.isSuccessful()) {
                        Log.w(
                                TAG,
                                "HTTP " + respuesta.code()
                                        + " URL=" + solicitud.url()
                                        + " body=" + contenido
                        );
                        callback.onError(
                                mensajeError(
                                        parsear(contenido),
                                        respuesta.code()
                                )
                        );
                        return;
                    }

                    List<Favorito> favoritos = new ArrayList<>();

                    try {
                        JSONArray arreglo = new JSONArray(contenido);

                        for (int i = 0; i < arreglo.length(); i++) {
                            Favorito favorito = Favorito.desdeJson(
                                    arreglo.optJSONObject(i)
                            );
                            if (favorito != null) {
                                favoritos.add(favorito);
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Respuesta de favoritos no válida", e);
                        callback.onError(
                                "No se pudieron leer tus favoritos."
                        );
                        return;
                    }

                    callback.onSuccess(favoritos);
                }
            }
        });
    }

    /**
     * Busca si el usuario ya guardó un foodtruck concreto en favoritos.
     * Si no existe, entrega null en onSuccess.
     */
    public static void obtenerFavorito(
            String accessToken,
            String usuarioId,
            String foodtruckId,
            FavoritoCallback callback) {

        String url = SupabaseConfig.SUPABASE_URL
                + TABLA_FAVORITOS
                + "?or=(usuario_id.eq." + usuarioId + ",compartido_con_id.eq." + usuarioId + ")"
                + "&foodtruck_id=eq." + foodtruckId
                + "&select=" + CAMPOS_FAVORITO
                + "&limit=1";

        Request solicitud = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        ejecutar(solicitud, new AuthCallback() {
            @Override
            public void onSuccess(JSONObject respuesta) {
                callback.onSuccess(Favorito.desdeJson(respuesta));
            }

            @Override
            public void onError(String mensaje) {
                callback.onError(mensaje);
            }

            @Override
            public void onSinConexion(String mensaje) {
                callback.onSinConexion(mensaje);
            }
        });
    }

    /**
     * Crea un nuevo favorito en public.favoritos con etiqueta y nota personal.
     */
    public static void crearFavorito(
            String accessToken,
            String usuarioId,
            String foodtruckId,
            String etiqueta,
            String nota,
            FavoritoCallback callback) {
        crearFavorito(accessToken, usuarioId, foodtruckId, etiqueta, nota, "", callback);
    }

    /**
     * Crea un nuevo favorito en public.favoritos con etiqueta, nota y nombre del dueño.
     */
    public static void crearFavorito(
            String accessToken,
            String usuarioId,
            String foodtruckId,
            String etiqueta,
            String nota,
            String duenoNombre,
            FavoritoCallback callback) {

        try {
            JSONObject datos = new JSONObject();
            datos.put("usuario_id", usuarioId);
            datos.put("foodtruck_id", foodtruckId);
            datos.put(
                    "etiqueta",
                    etiqueta == null || etiqueta.trim().isEmpty()
                            ? "Para almorzar"
                            : etiqueta.trim()
            );
            datos.put("nota", nota == null ? "" : nota.trim());
            datos.put("dueno_nombre", duenoNombre == null ? "" : duenoNombre.trim());

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL + TABLA_FAVORITOS)
                    .post(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build();

            ejecutar(solicitud, new AuthCallback() {
                @Override
                public void onSuccess(JSONObject respuesta) {
                    callback.onSuccess(Favorito.desdeJson(respuesta));
                }

                @Override
                public void onError(String mensaje) {
                    callback.onError(mensaje);
                }

                @Override
                public void onSinConexion(String mensaje) {
                    callback.onSinConexion(mensaje);
                }
            });

        } catch (Exception e) {
            callback.onError("No se pudo preparar el favorito.");
        }
    }

    /**
     * Actualiza la etiqueta y la nota personal de un favorito existente.
     */
    public static void actualizarFavorito(
            String accessToken,
            String favoritoId,
            String etiqueta,
            String nota,
            FavoritoCallback callback) {

        try {
            JSONObject datos = new JSONObject();
            datos.put(
                    "etiqueta",
                    etiqueta == null || etiqueta.trim().isEmpty()
                            ? "Para almorzar"
                            : etiqueta.trim()
            );
            datos.put("nota", nota == null ? "" : nota.trim());

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL
                            + TABLA_FAVORITOS
                            + "?id=eq." + favoritoId)
                    .patch(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build();

            ejecutar(solicitud, new AuthCallback() {
                @Override
                public void onSuccess(JSONObject respuesta) {
                    callback.onSuccess(Favorito.desdeJson(respuesta));
                }

                @Override
                public void onError(String mensaje) {
                    callback.onError(mensaje);
                }

                @Override
                public void onSinConexion(String mensaje) {
                    callback.onSinConexion(mensaje);
                }
            });

        } catch (Exception e) {
            callback.onError("No se pudo preparar la actualización.");
        }
    }

    /**
     * Elimina un favorito propio por su id.
     */
    public static void eliminarFavorito(
            String accessToken,
            String favoritoId,
            AuthCallback callback) {

        Request solicitud = new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL
                        + TABLA_FAVORITOS
                        + "?id=eq." + favoritoId)
                .delete()
                .addHeader("apikey", SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        ejecutar(solicitud, callback);
    }

    /**
     * Comparte un favorito con otro usuario registrado por su correo mediante RPC.
     */
    public static void compartirFavorito(
            String accessToken,
            String favoritoId,
            String correoDestino,
            FavoritoCallback callback) {

        try {
            JSONObject datos = new JSONObject();
            datos.put("p_favorito_id", favoritoId);
            datos.put(
                    "p_correo_destino",
                    correoDestino == null
                            ? ""
                            : correoDestino.trim().toLowerCase(Locale.ROOT)
            );

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL
                            + "/rest/v1/rpc/compartir_favorito")
                    .post(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .build();

            ejecutar(solicitud, new AuthCallback() {
                @Override
                public void onSuccess(JSONObject respuesta) {
                    callback.onSuccess(Favorito.desdeJson(respuesta));
                }

                @Override
                public void onError(String mensaje) {
                    callback.onError(mensaje);
                }

                @Override
                public void onSinConexion(String mensaje) {
                    callback.onSinConexion(mensaje);
                }
            });

        } catch (Exception e) {
            callback.onError("No se pudo preparar la invitación.");
        }
    }

    /**
     * Quita al usuario compartido de un favorito (vuelve a ser solo personal).
     */
    public static void dejarDeCompartirFavorito(
            String accessToken,
            String favoritoId,
            FavoritoCallback callback) {

        try {
            JSONObject datos = new JSONObject();
            datos.put("compartido_con_id", JSONObject.NULL);
            datos.put("compartido_con_nombre", "");
            datos.put("compartido_con_correo", "");

            Request solicitud = new Request.Builder()
                    .url(SupabaseConfig.SUPABASE_URL
                            + TABLA_FAVORITOS
                            + "?id=eq." + favoritoId)
                    .patch(RequestBody.create(datos.toString(), JSON))
                    .addHeader("apikey",
                            SupabaseConfig.SUPABASE_PUBLISHABLE_KEY)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=representation")
                    .build();

            ejecutar(solicitud, new AuthCallback() {
                @Override
                public void onSuccess(JSONObject respuesta) {
                    callback.onSuccess(Favorito.desdeJson(respuesta));
                }

                @Override
                public void onError(String mensaje) {
                    callback.onError(mensaje);
                }

                @Override
                public void onSinConexion(String mensaje) {
                    callback.onSinConexion(mensaje);
                }
            });

        } catch (Exception e) {
            callback.onError("No se pudo dejar de compartir.");
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
                        Log.w(
                                TAG,
                                "HTTP " + respuesta.code()
                                        + " URL=" + solicitud.url()
                                        + " body=" + contenido
                        );
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

        if (minusculas.contains("favoritos_usuario_foodtruck_key")
                || minusculas.contains("duplicate key value")) {
            return "Este local ya está en tus favoritos.";
        }

        if (minusculas.contains("relation \"public.favoritos\" does not exist")
                || minusculas.contains("could not find the table 'public.favoritos'")) {
            return "Falta crear la tabla favoritos en Supabase (ejecuta supabase/favoritos.sql).";
        }

        if (minusculas.contains("compartir_favorito")
                || minusculas.contains("compartido_con_id")
                || minusculas.contains("dueno_nombre")) {
            return "Ejecuta supabase/favoritos.sql en Supabase para activar favoritos compartidos.";
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
