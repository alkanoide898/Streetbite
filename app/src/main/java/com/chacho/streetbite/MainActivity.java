package com.chacho.streetbite;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class MainActivity extends BaseActivity {

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

    /**
     * En el login todo el contenido cae desde arriba: primero el logo, luego
     * el título, la descripción, la tarjeta y el enlace de registro. El logo
     * también gira un poco mientras aterriza.
     */
    @Override
    protected void animarEntrada() {
        View logo = findViewById(R.id.imgLogoLogin);

        Animaciones.caerLogoDesdeArriba(
                logo,
                findViewById(R.id.txtStreetBite),
                findViewById(R.id.txtDescripcion),
                findViewById(R.id.cardLogin),
                findViewById(R.id.filaRegistro));
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

        // 1) Retorno desde enlace de recuperación de contraseña (viene en #fragment)
        String fragment = datos.getFragment();
        if (fragment != null && !fragment.isEmpty()) {
            Uri fragmentParams = Uri.parse("streetbite://callback?" + fragment);
            String errorFragment = fragmentParams.getQueryParameter("error_description");
            if (errorFragment == null) {
                errorFragment = fragmentParams.getQueryParameter("error");
            }
            if (errorFragment != null && !errorFragment.isEmpty()) {
                Toast.makeText(
                        this,
                        "No se pudo completar la recuperación: " + errorFragment,
                        Toast.LENGTH_LONG
                ).show();
                return;
            }

            String tipo = fragmentParams.getQueryParameter("type");
            String recoveryToken = fragmentParams.getQueryParameter("access_token");
            if ("recovery".equals(tipo)
                    && recoveryToken != null
                    && !recoveryToken.isEmpty()) {
                mostrarDialogoNuevaContrasena(recoveryToken);
                return;
            }
        }

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
        sonarBotonesDelDialogo(dialogo);
    }

    private void mostrarDialogoNuevaContrasena(String recoveryAccessToken) {
        float densidad = getResources().getDisplayMetrics().density;
        int padH = Math.round(22 * densidad);
        int padV = Math.round(12 * densidad);
        int padCampo = Math.round(14 * densidad);

        LinearLayout contenedor = new LinearLayout(this);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(padH, padV, padH, padV / 2);

        EditText edtNueva = new EditText(this);
        edtNueva.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edtNueva.setSingleLine(true);
        edtNueva.setHint("Nueva contraseña (mínimo 6 caracteres)");
        edtNueva.setBackgroundResource(R.drawable.bg_sb_input);
        edtNueva.setTextColor(getColor(R.color.sb_marron));
        edtNueva.setHintTextColor(getColor(R.color.sb_hint));
        edtNueva.setPadding(padCampo, padCampo, padCampo, padCampo);
        contenedor.addView(edtNueva);

        EditText edtConfirmar = new EditText(this);
        edtConfirmar.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        edtConfirmar.setSingleLine(true);
        edtConfirmar.setHint("Repetir nueva contraseña");
        edtConfirmar.setBackgroundResource(R.drawable.bg_sb_input);
        edtConfirmar.setTextColor(getColor(R.color.sb_marron));
        edtConfirmar.setHintTextColor(getColor(R.color.sb_hint));
        edtConfirmar.setPadding(padCampo, padCampo, padCampo, padCampo);
        LinearLayout.LayoutParams paramsConfirmar = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        paramsConfirmar.topMargin = Math.round(10 * densidad);
        edtConfirmar.setLayoutParams(paramsConfirmar);
        contenedor.addView(edtConfirmar);

        AlertDialog dialogo = new AlertDialog.Builder(this)
                .setTitle("Nueva contraseña")
                .setMessage("Ingresa tu nueva contraseña para tu cuenta StreetBite.")
                .setView(contenedor)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();

        dialogo.setOnShowListener(d -> {
            Button btnGuardar = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
            btnGuardar.setOnClickListener(v -> {
                String nueva = edtNueva.getText().toString();
                String confirmar = edtConfirmar.getText().toString();

                if (nueva.length() < 6) {
                    Toast.makeText(
                            this,
                            "La contraseña debe tener al menos 6 caracteres.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                if (!nueva.equals(confirmar)) {
                    Toast.makeText(
                            this,
                            "Las contraseñas no coinciden.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                btnGuardar.setEnabled(false);
                btnGuardar.setText("Guardando...");

                SupabaseHelper.cambiarContrasenaAuth(
                        recoveryAccessToken,
                        nueva,
                        new SupabaseHelper.AuthCallback() {
                            @Override
                            public void onSuccess(JSONObject respuesta) {
                                runOnUiThread(() -> {
                                    dialogo.dismiss();
                                    Toast.makeText(
                                            MainActivity.this,
                                            "Contraseña actualizada. Ya puedes iniciar sesión.",
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                            }

                            @Override
                            public void onError(String mensaje) {
                                runOnUiThread(() -> {
                                    btnGuardar.setEnabled(true);
                                    btnGuardar.setText("Guardar");
                                    Toast.makeText(
                                            MainActivity.this,
                                            "No se pudo actualizar: " + mensaje,
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                            }
                        }
                );
            });
        });

        dialogo.show();
        sonarBotonesDelDialogo(dialogo);
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
