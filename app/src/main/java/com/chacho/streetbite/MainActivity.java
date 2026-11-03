package com.chacho.streetbite;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class MainActivity extends AppCompatActivity {

    private Button btnLogin;

    private static final String PREFS_NAME = "StreetBiteAuth";
    private static final String PKCE_VERIFIER = "pkce_verifier";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        EditText edtCorreo = findViewById(R.id.edtCorreo);
        EditText edtPassword = findViewById(R.id.edtPassword);

        btnLogin = findViewById(R.id.btnLogin);
        TextView txtOlvidaste = findViewById(R.id.txtOlvidaste);
        TextView txtRegistro = findViewById(R.id.txtRegistro);
        Button btnDiscord = findViewById(R.id.btnDiscord);

        // LOGIN TRADICIONAL
        btnLogin.setOnClickListener(v -> {
            String correo = edtCorreo.getText().toString().trim();
            String password = edtPassword.getText().toString();

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                        MainActivity.this,
                        "Ingresa tu correo y contraseña",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            cambiarEstadoLogin(false, "Ingresando...");

            SupabaseHelper.iniciarSesion(
                    correo,
                    password,
                    new SupabaseHelper.AuthCallback() {
                        @Override
                        public void onSuccess(JSONObject respuesta) {
                            runOnUiThread(() -> completarSesion(respuesta));
                        }

                        @Override
                        public void onError(String mensaje) {
                            runOnUiThread(() -> {
                                cambiarEstadoLogin(true, "Iniciar sesión");

                                Toast.makeText(
                                        MainActivity.this,
                                        mensaje,
                                        Toast.LENGTH_LONG
                                ).show();
                            });
                        }
                    }
            );
        });

        // RECUPERAR CONTRASEÑA
        txtOlvidaste.setOnClickListener(v ->
                mostrarDialogoRecuperar(edtCorreo.getText().toString().trim())
        );

        // IR AL REGISTRO
        txtRegistro.setOnClickListener(v -> startActivity(
                new Intent(MainActivity.this, RegistroActivity.class)
        ));

        // LOGIN CON DISCORD
        btnDiscord.setOnClickListener(v -> iniciarSesionDiscord());

        // PROCESAR EL REGRESO DESDE DISCORD, SI CORRESPONDE
        procesarRetornoDiscord(getIntent());
    }

    private void iniciarSesionDiscord() {
        try {
            String verificador = generarVerificadorPkce();
            String desafio = generarDesafioPkce(verificador);

            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PKCE_VERIFIER, verificador)
                    .apply();

            String oauthUrl = Uri.parse(
                            SupabaseConfig.SUPABASE_URL + "/auth/v1/authorize"
                    ).buildUpon()
                    .appendQueryParameter("provider", "discord")
                    .appendQueryParameter("prompt", "login")
                    .appendQueryParameter(
                            "redirect_to",
                            "streetbite://login-callback"
                    )
                    .appendQueryParameter("code_challenge", desafio)
                    .appendQueryParameter("code_challenge_method", "s256")
                    .build()
                    .toString();

            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(oauthUrl)
            ));

        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No se pudo iniciar sesión con Discord.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        procesarRetornoDiscord(intent);
    }

    private void procesarRetornoDiscord(Intent intent) {
        Uri datos = intent != null ? intent.getData() : null;

        if (datos == null
                || !"streetbite".equals(datos.getScheme())
                || !"login-callback".equals(datos.getHost())) {
            return;
        }

        // Se limpia el intent para no procesar el mismo código dos veces
        // (por ejemplo, si la pantalla se recrea al rotar)
        setIntent(new Intent());

        String error = datos.getQueryParameter("error_description");

        if (error == null) {
            error = datos.getQueryParameter("error");
        }

        if (error != null) {
            Toast.makeText(
                    this,
                    "Error de Discord: " + error,
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        String codigo = datos.getQueryParameter("code");

        if (codigo == null || codigo.isEmpty()) {
            Toast.makeText(
                    this,
                    "No se recibió el código de autorización.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        SharedPreferences preferencias =
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        String verificador = preferencias.getString(PKCE_VERIFIER, null);

        if (verificador == null) {
            Toast.makeText(
                    this,
                    "No se encontró el verificador. Intenta nuevamente.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        SupabaseHelper.intercambiarCodigoPkce(
                codigo,
                verificador,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        runOnUiThread(() -> {
                            preferencias.edit()
                                    .remove(PKCE_VERIFIER)
                                    .apply();

                            completarSesion(respuesta);
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                "Error al iniciar sesión: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    /**
     * Guarda la sesión y sincroniza el perfil en public.usuarios.
     * Si la sincronización falla, se entra igual: la cuenta ya está creada.
     */
    private void completarSesion(JSONObject respuesta) {
        String accessToken = respuesta.optString("access_token", "");

        if (accessToken.isEmpty()) {
            Toast.makeText(
                    this,
                    "Supabase no devolvió una sesión.",
                    Toast.LENGTH_LONG
            ).show();

            cambiarEstadoLogin(true, "Iniciar sesión");
            return;
        }

        Sesion.guardarRespuestaAuth(this, respuesta);

        if (SupabaseHelper.extraerDatosPerfil(respuesta) != null) {
            sincronizarPerfil(accessToken, respuesta);
            return;
        }

        // Algunos flujos (por ejemplo Discord) no incluyen "user".
        SupabaseHelper.obtenerUsuario(
                accessToken,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject usuario) {
                        Sesion.guardarUsuario(MainActivity.this, usuario);
                        sincronizarPerfil(accessToken, usuario);
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(MainActivity.this::irAlHome);
                    }
                }
        );
    }

    private void sincronizarPerfil(
            String accessToken,
            JSONObject respuestaAuth) {

        SupabaseHelper.sincronizarPerfil(
                accessToken,
                respuestaAuth,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject perfil) {
                        if (perfil.length() > 0) {
                            Sesion.guardarPerfil(
                                    MainActivity.this,
                                    perfil
                            );
                        }

                        runOnUiThread(MainActivity.this::irAlHome);
                    }

                    @Override
                    public void onError(String mensaje) {
                        // El perfil es secundario: no bloquea el login.
                        runOnUiThread(MainActivity.this::irAlHome);
                    }
                }
        );
    }

    private void irAlHome() {
        Intent intent = new Intent(MainActivity.this, HomeActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    private void cambiarEstadoLogin(boolean habilitado, String texto) {
        btnLogin.setEnabled(habilitado);
        btnLogin.setText(texto);
    }

    private void mostrarDialogoRecuperar(String correoInicial) {
        EditText entrada = new EditText(this);
        entrada.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        entrada.setSingleLine(true);
        entrada.setHint("Correo electrónico");
        entrada.setText(correoInicial);
        entrada.setPadding(48, 32, 48, 16);

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle("Recuperar contraseña")
                .setMessage(
                        "Te enviaremos un enlace para crear una "
                                + "contraseña nueva."
                )
                .setView(entrada)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Enviar", null)
                .create();

        dialogo.setOnShowListener(d -> dialogo
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String correo = entrada.getText().toString().trim();

                    if (!android.util.Patterns.EMAIL_ADDRESS
                            .matcher(correo).matches()) {
                        Toast.makeText(
                                this,
                                "Ingresa un correo válido",
                                Toast.LENGTH_SHORT
                        ).show();
                        return;
                    }

                    dialogo.dismiss();
                    enviarRecuperacion(correo);
                }));

        dialogo.show();
    }

    private void enviarRecuperacion(String correo) {
        SupabaseHelper.recuperarContrasena(
                correo,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                "Revisa tu correo para continuar.",
                                Toast.LENGTH_LONG
                        ).show());
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private String generarVerificadorPkce() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);

        return Base64.encodeToString(
                bytes,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP
        );
    }

    private String generarDesafioPkce(String verificador) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        byte[] hash = digest.digest(
                verificador.getBytes(StandardCharsets.US_ASCII)
        );

        return Base64.encodeToString(
                hash,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP
        );
    }
}
