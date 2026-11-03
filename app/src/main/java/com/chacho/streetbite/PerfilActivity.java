package com.chacho.streetbite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.util.Locale;

public class PerfilActivity extends AppCompatActivity {

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

        btnMiPerfil.setOnClickListener(v -> mostrarInformacion());

        btnFavoritos.setOnClickListener(v ->
                Toast.makeText(this, "Mis favoritos", Toast.LENGTH_SHORT).show()
        );

        btnNotificaciones.setOnClickListener(v ->
                Toast.makeText(this, "Notificaciones", Toast.LENGTH_SHORT).show()
        );

        btnConfiguracion.setOnClickListener(v ->
                Toast.makeText(this, "Configuración", Toast.LENGTH_SHORT).show()
        );

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

        SupabaseHelper.guardarPerfil(
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

    private void mostrarInformacion() {
        String nombre = Sesion.nombre(this);
        String correo = Sesion.correo(this);

        new AlertDialog.Builder(this)
                .setTitle("Mi información")
                .setMessage(
                        "Nombre: " + (nombre.isEmpty() ? "—" : nombre)
                                + "\nCorreo: "
                                + (correo.isEmpty() ? "—" : correo)
                )
                .setPositiveButton("Cerrar", null)
                .show();
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
