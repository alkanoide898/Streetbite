package com.chacho.streetbite;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.Locale;

public class PerfilActivity extends BaseActivity {

    private TextView txtInicial;
    private TextView txtNombre;
    private TextView txtCorreo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_perfil);

        txtInicial = findViewById(R.id.txtPerfilInicial);
        txtNombre = findViewById(R.id.txtPerfilNombre);
        txtCorreo = findViewById(R.id.txtPerfilCorreo);

        TextView btnVolver = findViewById(R.id.btnVolverPerfil);
        btnVolver.setOnClickListener(v -> finish());

        LinearLayout btnMiPerfil = findViewById(R.id.btnMiPerfil);
        LinearLayout btnFavoritos = findViewById(R.id.btnFavoritos);
        LinearLayout btnNotificaciones = findViewById(R.id.btnNotificaciones);
        LinearLayout btnConfiguracion = findViewById(R.id.btnConfiguracion);
        LinearLayout btnAyuda = findViewById(R.id.btnAyuda);
        LinearLayout btnCerrarSesion = findViewById(R.id.btnCerrarSesion);

        btnMiPerfil.setOnClickListener(v -> sincronizarYEditarPerfil());

        LinearLayout btnEliminarCuenta = findViewById(R.id.btnEliminarCuenta);
        btnEliminarCuenta.setOnClickListener(v -> confirmarEliminarCuenta());

        btnFavoritos.setOnClickListener(v ->
                Toast.makeText(this, "Mis favoritos", Toast.LENGTH_SHORT).show()
        );

        btnNotificaciones.setOnClickListener(v ->
                Toast.makeText(this, "Notificaciones", Toast.LENGTH_SHORT).show()
        );

        btnConfiguracion.setOnClickListener(v -> mostrarConfiguracion());

        btnAyuda.setOnClickListener(v ->
                Toast.makeText(this, "Ayuda y soporte", Toast.LENGTH_SHORT).show()
        );

        btnCerrarSesion.setOnClickListener(v -> confirmarCierre());

        mostrarDatosGuardados();
        cargarPerfil();
    }

    private void mostrarDatosGuardados() {
        mostrarDatos(Sesion.nombre(this), Sesion.correo(this));
    }

    private void mostrarDatos(String nombre, String correo) {
        txtNombre.setText(nombre.isEmpty() ? "Usuario" : nombre);
        txtCorreo.setText(correo);

        String inicial = !nombre.trim().isEmpty()
                ? nombre.trim().substring(0, 1).toUpperCase(Locale.getDefault())
                : (correo.trim().isEmpty()
                        ? "?"
                        : correo.trim().substring(0, 1)
                                .toUpperCase(Locale.getDefault()));

        txtInicial.setText(inicial);
    }

    private void cargarPerfil() {
        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty()) {
            volverAlLogin();
            return;
        }

        if (usuarioId.isEmpty()) {
            SupabaseHelper.obtenerUsuario(
                    accessToken,
                    new SupabaseHelper.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject usuario) {
                            Sesion.guardarUsuario(
                                    PerfilActivity.this,
                                    usuario
                            );

                            runOnUiThread(() -> {
                                mostrarDatosGuardados();
                                cargarPerfil();
                            });
                        }

                        @Override
                        public void onError(String mensaje) {
                            // Se mantienen los datos guardados.
                        }
                    }
            );
            return;
        }

        SupabaseHelper.obtenerPerfil(
                accessToken,
                usuarioId,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject perfil) {
                        if (perfil.length() == 0) {
                            crearPerfil(accessToken, usuarioId);
                            return;
                        }

                        Sesion.guardarPerfil(PerfilActivity.this, perfil);

                        runOnUiThread(() -> mostrarDatos(
                                perfil.optString("nombre", ""),
                                perfil.optString("correo", "")
                        ));
                    }

                    @Override
                    public void onError(String mensaje) {
                        // Se mantienen los datos guardados.
                    }
                }
        );
    }

    private void crearPerfil(String accessToken, String usuarioId) {
        String nombre = Sesion.nombre(this);
        String correo = Sesion.correo(this);

        if (nombre.isEmpty() || correo.isEmpty()) {
            return;
        }

        SupabaseHelper.crearPerfilSiFalta(
                accessToken,
                usuarioId,
                nombre,
                correo,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject perfil) {
                        if (perfil.length() > 0) {
                            Sesion.guardarPerfil(
                                    PerfilActivity.this,
                                    perfil
                            );

                            runOnUiThread(() -> mostrarDatos(
                                    perfil.optString("nombre", ""),
                                    perfil.optString("correo", "")
                            ));
                        }
                    }

                    @Override
                    public void onError(String mensaje) {
                        // El perfil local sigue visible.
                    }
                }
        );
    }

    private void mostrarConfiguracion() {
        CheckBox checkSonidos = new CheckBox(this);
        checkSonidos.setText("Sonidos al tocar botones y tarjetas");
        checkSonidos.setChecked(Sonidos.estaActivo(this));

        checkSonidos.setOnCheckedChangeListener((boton, activo) -> {
            Sonidos.activar(this, activo);

            // Suena de inmediato para que se note el cambio.
            if (activo) {
                Sonidos.tocar(this, Sonidos.TAP);
            }
        });

        int margen = Math.round(
                20 * getResources().getDisplayMetrics().density);

        FrameLayout contenedor = new FrameLayout(this);
        contenedor.setPadding(margen, margen / 2, margen, 0);
        contenedor.addView(checkSonidos);

        new AlertDialog.Builder(this)
                .setTitle("Configuración")
                .setView(contenedor)
                .setPositiveButton("Listo", null)
                .show();
    }

    /**
     * Abre el editor con los datos que están guardados en la nube.
     * Si la fila todavía no existe, la crea con los datos de la sesión.
     */
    private void sincronizarYEditarPerfil() {
        String accessToken = Sesion.accessToken(this);

        if (accessToken.isEmpty()) {
            Toast.makeText(this,
                    "No hay sesión activa. Inicia sesión de nuevo.",
                    Toast.LENGTH_LONG).show();
            volverAlLogin();
            return;
        }

        String usuarioId = Sesion.usuarioId(this);

        if (usuarioId.isEmpty()) {
            SupabaseHelper.obtenerUsuario(
                    accessToken,
                    new SupabaseHelper.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject usuario) {
                            Sesion.guardarUsuario(
                                    PerfilActivity.this, usuario);
                            runOnUiThread(
                                    PerfilActivity.this::
                                            sincronizarYEditarPerfil);
                        }

                        @Override
                        public void onError(String mensaje) {
                            mostrarAviso(
                                    "No se pudo obtener el usuario: "
                                            + mensaje);
                        }
                    }
            );
            return;
        }

        SupabaseHelper.obtenerPerfil(
                accessToken,
                usuarioId,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject perfil) {
                        if (perfil.length() > 0) {
                            Sesion.guardarPerfil(
                                    PerfilActivity.this, perfil);
                            runOnUiThread(() -> {
                                mostrarDatos(
                                        perfil.optString("nombre", ""),
                                        perfil.optString("correo", "")
                                );
                                editarPerfil();
                            });
                            return;
                        }

                        // No hay fila en la nube: se crea y luego se edita.
                        crearPerfilYEditar(accessToken, usuarioId);
                    }

                    @Override
                    public void onError(String mensaje) {
                        mostrarAviso(
                                "No se pudo leer tu perfil: " + mensaje);
                        runOnUiThread(PerfilActivity.this::editarPerfil);
                    }
                }
        );
    }

    /**
     * Crea la fila del perfil con los datos de la sesión y abre el editor.
     */
    private void crearPerfilYEditar(
            String accessToken,
            String usuarioId) {

        String nombre = Sesion.nombre(this);
        String correo = Sesion.correo(this);

        if (nombre.isEmpty() || correo.isEmpty()) {
            SupabaseHelper.obtenerUsuario(
                    accessToken,
                    new SupabaseHelper.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject usuario) {
                            Sesion.guardarUsuario(
                                    PerfilActivity.this, usuario);
                            runOnUiThread(() -> crearPerfilYEditar(
                                    accessToken, usuarioId));
                        }

                        @Override
                        public void onError(String mensaje) {
                            mostrarAviso(
                                    "No se pudieron leer tus datos: "
                                            + mensaje);
                        }
                    }
            );
            return;
        }

        SupabaseHelper.crearPerfilSiFalta(
                accessToken,
                usuarioId,
                nombre,
                correo,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject perfil) {
                        if (perfil.length() > 0) {
                            Sesion.guardarPerfil(
                                    PerfilActivity.this, perfil);
                        }

                        runOnUiThread(() -> {
                            mostrarDatos(
                                    Sesion.nombre(PerfilActivity.this),
                                    Sesion.correo(PerfilActivity.this)
                            );
                            editarPerfil();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        mostrarAviso(
                                "No se pudo crear tu perfil: " + mensaje);
                        runOnUiThread(PerfilActivity.this::editarPerfil);
                    }
                }
        );
    }

    private void editarPerfil() {
        String nombreActual = Sesion.nombre(this);
        String correoActual = Sesion.correo(this);

        float densidad = getResources().getDisplayMetrics().density;
        int margen = Math.round(20f * densidad);

        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(margen, 0, margen, 0);

        EditText inputNombre = new EditText(this);
        inputNombre.setHint("Nombre");
        inputNombre.setText(nombreActual);
        inputNombre.setInputType(InputType.TYPE_CLASS_TEXT);
        inputNombre.setSelectAllOnFocus(true);
        contenedor.addView(inputNombre);

        EditText inputCorreo = new EditText(this);
        inputCorreo.setHint("Correo electrónico");
        inputCorreo.setText(correoActual);
        inputCorreo.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        inputCorreo.setEnabled(false);
        inputCorreo.setFocusable(false);
        contenedor.addView(inputCorreo);

        TextView nota = new TextView(this);
        nota.setText(
                "El correo de inicio de sesión no se cambia desde aquí.");
        nota.setTextSize(12f);
        nota.setAlpha(0.7f);
        nota.setPadding(0, Math.round(-4f * densidad), 0, 0);
        contenedor.addView(nota);

        new AlertDialog.Builder(this)
                .setTitle("Editar perfil")
                .setView(contenedor)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialogo, cual) -> {
                    String nombre = inputNombre.getText().toString().trim();

                    if (nombre.isEmpty() || correoActual.isEmpty()) {
                        Toast.makeText(this,
                                "Escribe tu nombre.", Toast.LENGTH_SHORT)
                                .show();
                        return;
                    }

                    String accessToken = Sesion.accessToken(this);
                    String usuarioId = Sesion.usuarioId(this);

                    if (accessToken.isEmpty() || usuarioId.isEmpty()) {
                        volverAlLogin();
                        return;
                    }

                    SupabaseHelper.guardarOActualizarPerfil(
                            accessToken,
                            usuarioId,
                            nombre,
                            correoActual,
                            new SupabaseHelper.AuthCallback() {
                                @Override
                                public void onSuccess(JSONObject respuesta) {
                                    // El nombre también se guarda en Auth
                                    // para conservarlo al volver a entrar.
                                    SupabaseHelper.actualizarNombreAuth(
                                            accessToken,
                                            nombre,
                                            new SupabaseHelper.AuthCallback() {
                                                @Override
                                                public void onSuccess(
                                                        JSONObject usuario) {
                                                    Sesion.guardarUsuario(
                                                            PerfilActivity.this,
                                                            usuario);
                                                }

                                                @Override
                                                public void onError(
                                                        String mensaje) {
                                                    // El perfil ya se guardó;
                                                    // el nombre se reintenta
                                                    // en el próximo inicio.
                                                }
                                            }
                                    );

                                    if (respuesta.length() > 0) {
                                        Sesion.guardarPerfil(
                                                PerfilActivity.this,
                                                respuesta);
                                    }

                                    Sesion.actualizarDatosPerfil(
                                            PerfilActivity.this,
                                            nombre,
                                            correoActual);

                                    runOnUiThread(() -> {
                                        mostrarDatos(nombre, correoActual);
                                        Toast.makeText(
                                                PerfilActivity.this,
                                                "Perfil actualizado.",
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    });
                                }

                                @Override
                                public void onError(String mensaje) {
                                    mostrarAviso(
                                            "No se pudo guardar: "
                                                    + mensaje);
                                }
                            });
                })
                .show();
    }

    private void mostrarAviso(String mensaje) {
        runOnUiThread(() -> Toast.makeText(
                PerfilActivity.this, mensaje, Toast.LENGTH_LONG).show());
    }

    private void confirmarEliminarCuenta() {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar cuenta")
                .setMessage(
                        "¿Estás seguro de que quieres eliminar tu cuenta? "
                                + "Esta acción no se puede deshacer y se borrará "
                                + "tu perfil y tu historial de favoritos.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (dialogo, cual) -> eliminarCuenta())
                .show();
    }

    private void eliminarCuenta() {
        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty() || usuarioId.isEmpty()) {
            volverAlLogin();
            return;
        }

        SupabaseHelper.eliminarPerfil(
                accessToken,
                usuarioId,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        // El perfil público se borró; cerramos sesión local.
                        Sesion.limpiar(PerfilActivity.this);
                        runOnUiThread(PerfilActivity.this::volverAlLogin);
                    }

                    @Override
                    public void onError(String mensaje) {
                        // Aunque falle el borrado remoto, cerramos sesión
                        // local para no dejar al usuario colgado.
                        Sesion.limpiar(PerfilActivity.this);
                        runOnUiThread(PerfilActivity.this::volverAlLogin);
                    }
                });
    }

    private void confirmarCierre() {
        new AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Quieres salir de tu cuenta?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (d, w) -> cerrarSesion())
                .show();
    }

    private void cerrarSesion() {
        String accessToken = Sesion.accessToken(this);

        // Los tokens se borran de inmediato, aunque falle la red.
        Sesion.limpiar(this);
        volverAlLogin();

        if (!accessToken.isEmpty()) {
            SupabaseHelper.cerrarSesion(
                    accessToken,
                    new SupabaseHelper.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject respuesta) {
                            // La sesión local ya está cerrada.
                        }

                        @Override
                        public void onError(String mensaje) {
                            // La sesión local ya está cerrada.
                        }
                    }
            );
        }
    }

    private void volverAlLogin() {
        Intent intent = new Intent(PerfilActivity.this, MainActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
