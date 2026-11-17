package com.chacho.streetbite;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Pantalla para consultar y editar los datos del perfil del usuario.
 *
 * El nombre y el correo visible viven en public.usuarios. El correo es ademas
 * la identidad de la cuenta, por eso se cambia primero en Supabase Auth: si
 * queda pendiente de confirmacion, se avisa al usuario y se conserva el
 * correo anterior hasta que confirme el enlace.
 */
public class MiInformacionActivity extends BaseActivity {

    private EditText edtNombre;
    private EditText edtCorreo;
    private TextView txtInicial;
    private TextView txtNombreActual;
    private TextView txtCorreoActual;
    private Button btnGuardar;

    private String correoActual = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mi_informacion);

        edtNombre = findViewById(R.id.edtNombreInformacion);
        edtCorreo = findViewById(R.id.edtCorreoInformacion);
        txtInicial = findViewById(R.id.txtInicialInformacion);
        txtNombreActual = findViewById(R.id.txtNombreActual);
        txtCorreoActual = findViewById(R.id.txtCorreoActual);
        btnGuardar = findViewById(R.id.btnGuardarInformacion);

        String nombre = getIntent().getStringExtra("nombre");
        String correo = getIntent().getStringExtra("correo");

        if (nombre == null) {
            nombre = Sesion.nombre(this);
        }

        if (correo == null) {
            correo = Sesion.correo(this);
        }

        if (nombre == null) {
            nombre = "";
        }

        correoActual = correo == null ? "" : correo;

        mostrarDatos(nombre, correoActual);
        edtNombre.setText(nombre);
        edtCorreo.setText(correoActual);
        edtNombre.setInputType(InputType.TYPE_CLASS_TEXT);
        edtNombre.setSelectAllOnFocus(true);

        findViewById(R.id.btnVolverInformacion).setOnClickListener(v -> finish());
        findViewById(R.id.btnCancelarInformacion).setOnClickListener(v -> finish());
        btnGuardar.setOnClickListener(v -> guardarCambios());
    }

    private void mostrarDatos(String nombre, String correo) {
        txtNombreActual.setText(nombre.isEmpty() ? "Usuario" : nombre);
        txtCorreoActual.setText(correo);

        String inicial = !nombre.trim().isEmpty()
                ? nombre.trim().substring(0, 1).toUpperCase(Locale.getDefault())
                : (correo.trim().isEmpty()
                        ? "?"
                        : correo.trim().substring(0, 1)
                                .toUpperCase(Locale.getDefault()));

        txtInicial.setText(inicial);
    }

    private void guardarCambios() {
        String nombre = edtNombre.getText().toString().trim();
        String correo = edtCorreo.getText().toString().trim()
                .toLowerCase(Locale.ROOT);

        if (nombre.isEmpty()) {
            mostrarAviso("Escribe tu nombre.");
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            mostrarAviso("Escribe un correo válido.");
            return;
        }

        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty() || usuarioId.isEmpty()) {
            volverAlLogin();
            return;
        }

        cambiarEstadoGuardado(false);

        if (correo.equalsIgnoreCase(correoActual)) {
            guardarPerfilEnNube(accessToken, usuarioId, nombre, correo, true);
            return;
        }

        // El correo es la identidad de la cuenta: se cambia primero en Auth.
        SupabaseHelper.cambiarCorreoAuth(
                accessToken,
                correo,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject usuario) {
                        String correoAplicado =
                                usuario.optString("email", "");
                        String correoPendiente =
                                usuario.optString("new_email", "");

                        boolean aplicado = correoAplicado
                                .equalsIgnoreCase(correo);

                        if (!aplicado) {
                            // El nombre se guarda igual; el correo sigue
                            // siendo el anterior hasta la confirmación.
                            guardarPerfilEnNube(
                                    accessToken,
                                    usuarioId,
                                    nombre,
                                    correoActual,
                                    false
                            );

                            runOnUiThread(() -> {
                                cambiarEstadoGuardado(true);
                                edtCorreo.setText(correoActual);

                                if (correoPendiente.equalsIgnoreCase(correo)) {
                                    new AlertDialog.Builder(
                                            MiInformacionActivity.this)
                                            .setTitle("Confirma tu correo")
                                            .setMessage(
                                                    "Te enviamos un enlace de "
                                                            + "confirmación a "
                                                            + correo
                                                            + ". Tu correo "
                                                            + "actual sigue "
                                                            + "siendo "
                                                            + correoActual
                                                            + " hasta que lo "
                                                            + "confirmes."
                                            )
                                            .setPositiveButton(
                                                    "Entendido", null)
                                            .show();
                                } else {
                                    mostrarAviso(
                                            "Supabase no aplicó el cambio de "
                                                    + "correo. Inténtalo de "
                                                    + "nuevo."
                                    );
                                }
                            });
                            return;
                        }

                        Sesion.guardarUsuario(
                                MiInformacionActivity.this, usuario);
                        guardarPerfilEnNube(
                                accessToken,
                                usuarioId,
                                nombre,
                                correo,
                                true
                        );
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> {
                            cambiarEstadoGuardado(true);
                            mostrarAviso(
                                    "No se pudo cambiar el correo: "
                                            + mensaje);
                        });
                    }
                });
    }

    /**
     * Guarda nombre y correo en public.usuarios y replica el nombre en Auth.
     * Cuando cerrar es false se vuelve a la pantalla (caso correo pendiente).
     */
    private void guardarPerfilEnNube(
            String accessToken,
            String usuarioId,
            String nombre,
            String correo,
            boolean cerrar) {

        SupabaseHelper.guardarOActualizarPerfil(
                accessToken,
                usuarioId,
                nombre,
                correo,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        // El nombre también se guarda en Auth para
                        // conservarlo al volver a entrar.
                        SupabaseHelper.actualizarNombreAuth(
                                accessToken,
                                nombre,
                                new SupabaseHelper.AuthCallback() {
                                    @Override
                                    public void onSuccess(
                                            JSONObject usuario) {
                                        Sesion.guardarUsuario(
                                                MiInformacionActivity.this,
                                                usuario
                                        );
                                    }

                                    @Override
                                    public void onError(String mensaje) {
                                        // El perfil ya se guardó; el nombre
                                        // se reintenta en el próximo inicio.
                                    }
                                }
                        );

                        if (respuesta.length() > 0) {
                            Sesion.guardarPerfil(
                                    MiInformacionActivity.this,
                                    respuesta
                            );
                        }

                        Sesion.actualizarDatosPerfil(
                                MiInformacionActivity.this,
                                nombre,
                                correo
                        );

                        runOnUiThread(() -> {
                            mostrarDatos(nombre, correo);
                            cambiarEstadoGuardado(true);

                            if (cerrar) {
                                Toast.makeText(
                                        MiInformacionActivity.this,
                                        "Perfil actualizado.",
                                        Toast.LENGTH_SHORT
                                ).show();
                                setResult(RESULT_OK);
                                finish();
                            }
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> {
                            cambiarEstadoGuardado(true);
                            mostrarAviso("No se pudo guardar: " + mensaje);
                        });
                    }
                });
    }

    private void cambiarEstadoGuardado(boolean habilitado) {
        btnGuardar.setEnabled(habilitado);
        btnGuardar.setText(habilitado
                ? "Guardar cambios"
                : "Guardando...");
    }

    private void mostrarAviso(String mensaje) {
        runOnUiThread(() -> Toast.makeText(
                MiInformacionActivity.this, mensaje, Toast.LENGTH_LONG).show());
    }

    private void volverAlLogin() {
        Intent intent = new Intent(
                MiInformacionActivity.this, MainActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
