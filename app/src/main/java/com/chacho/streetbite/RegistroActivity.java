package com.chacho.streetbite;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class RegistroActivity extends AppCompatActivity {

    private Button btnRegistrar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        EditText edtNombre = findViewById(R.id.edtNombre);
        EditText edtCorreo = findViewById(R.id.edtCorreoRegistro);
        EditText edtPassword = findViewById(R.id.edtPasswordRegistro);
        EditText edtConfirmarPassword =
                findViewById(R.id.edtConfirmarPassword);

        btnRegistrar = findViewById(R.id.btnRegistrar);
        TextView txtVolverLogin = findViewById(R.id.txtVolverLogin);

        btnRegistrar.setOnClickListener(v -> {
            String nombre = edtNombre.getText().toString().trim();
            String correo = edtCorreo.getText().toString().trim();
            String password = edtPassword.getText().toString();
            String confirmarPassword =
                    edtConfirmarPassword.getText().toString();

            if (nombre.isEmpty() || correo.isEmpty()
                    || password.isEmpty()
                    || confirmarPassword.isEmpty()) {
                Toast.makeText(this,
                        "Completa todos los campos",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (nombre.length() < 2) {
                Toast.makeText(this,
                        "Ingresa tu nombre",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS
                    .matcher(correo).matches()) {
                Toast.makeText(this,
                        "Ingresa un correo válido",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (!password.equals(confirmarPassword)) {
                Toast.makeText(this,
                        "Las contraseñas no coinciden",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this,
                        "La contraseña debe tener al menos 6 caracteres",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            cambiarEstado(false, "Creando cuenta...");

            SupabaseHelper.registrarUsuario(
                    nombre,
                    correo,
                    password,
                    new SupabaseHelper.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject respuesta) {
                            String accessToken =
                                    respuesta.optString("access_token", "");

                            if (accessToken.isEmpty()) {
                                // El proyecto exige confirmar el correo.
                                runOnUiThread(() -> terminar(
                                        "Cuenta creada. Revisa tu correo "
                                                + "para confirmarla."
                                ));
                                return;
                            }

                            Sesion.guardarRespuestaAuth(
                                    RegistroActivity.this,
                                    respuesta
                            );

                            SupabaseHelper.sincronizarPerfil(
                                    accessToken,
                                    respuesta,
                                    new SupabaseHelper.AuthCallback() {
                                        @Override
                                        public void onSuccess(JSONObject p) {
                                            if (p.length() > 0) {
                                                Sesion.guardarPerfil(
                                                        RegistroActivity.this,
                                                        p
                                                );
                                            }

                                            runOnUiThread(() -> {
                                                Toast.makeText(
                                                        RegistroActivity.this,
                                                        "Cuenta creada.",
                                                        Toast.LENGTH_LONG
                                                ).show();

                                                irAlHome();
                                            });
                                        }

                                        @Override
                                        public void onError(String m) {
                                            runOnUiThread(() -> {
                                                Toast.makeText(
                                                        RegistroActivity.this,
                                                        "Cuenta creada.",
                                                        Toast.LENGTH_LONG
                                                ).show();

                                                irAlHome();
                                            });
                                        }
                                    }
                            );
                        }

                        @Override
                        public void onError(String mensaje) {
                            runOnUiThread(() -> {
                                cambiarEstado(true, "Registrarse");

                                Toast.makeText(
                                        RegistroActivity.this,
                                        mensaje,
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    }
            );
        });

        txtVolverLogin.setOnClickListener(v -> finish());
    }

    private void cambiarEstado(boolean habilitado, String texto) {
        btnRegistrar.setEnabled(habilitado);
        btnRegistrar.setText(texto);
    }

    private void terminar(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        finish();
    }

    private void irAlHome() {
        Intent intent = new Intent(RegistroActivity.this, HomeActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
