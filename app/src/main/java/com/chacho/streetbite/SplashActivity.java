package com.chacho.streetbite;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SplashActivity extends BaseActivity {

    // Tiempo mínimo para que se alcance a ver la animación
    private static final long DURACION_MINIMA_MS = 1300;

    private static final int COLOR_FONDO = 0xFF1C1613;
    private static final int COLOR_CREMA = 0xFFFAF5EE;
    private static final int COLOR_NARANJA = 0xFFFFB020;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Animaciones infinitas: se cancelan al cerrar la pantalla
    private final List<Animator> animadoresInfinitos = new ArrayList<>();

    private boolean tiempoCumplido = false;
    private boolean yaNavego = false;
    private Class<?> destino = null;

    /** El splash no suena: la primera pantalla que se abre ya lo hace. */
    @Override
    protected boolean suenaTransicion() {
        return false;
    }

    /** El splash ya tiene su propia animación de logo, pulsos y puntos. */
    @Override
    protected void animarEntrada() {
        // Sin animación de entrada genérica.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        configurarBarras();
        iniciarAnimaciones();

        handler.postDelayed(() -> {
            tiempoCumplido = true;
            intentarNavegar();
        }, DURACION_MINIMA_MS);

        verificarSesion();
    }

    /** El splash ocupa toda la pantalla: no lleva padding de barras. */
    @Override
    protected boolean usaBarrasDelSistema() {
        return false;
    }

    // ==========================================
    // BARRAS DEL SISTEMA (mismo color que el fondo)
    // ==========================================

    @SuppressWarnings("deprecation")
    private void configurarBarras() {
        Window ventana = getWindow();

        ventana.setStatusBarColor(COLOR_FONDO);
        ventana.setNavigationBarColor(COLOR_FONDO);

        WindowInsetsControllerCompat controlador =
                WindowCompat.getInsetsController(ventana, ventana.getDecorView());

        // Íconos claros sobre fondo oscuro
        controlador.setAppearanceLightStatusBars(false);
        controlador.setAppearanceLightNavigationBars(false);
    }

    // ==========================================
    // ANIMACIONES
    // ==========================================

    private void iniciarAnimaciones() {
        View logo = findViewById(R.id.imgLogoSplash);
        View pulso1 = findViewById(R.id.viewPulso1);
        View pulso2 = findViewById(R.id.viewPulso2);
        TextView nombre = findViewById(R.id.txtNombreSplash);
        TextView lema = findViewById(R.id.txtLemaSplash);

        // "Street" en crema y "Bite" en naranja
        SpannableString titulo = new SpannableString("StreetBite");
        titulo.setSpan(new ForegroundColorSpan(COLOR_CREMA),
                0, 6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        titulo.setSpan(new ForegroundColorSpan(COLOR_NARANJA),
                6, 10, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        nombre.setText(titulo);

        float dp = getResources().getDisplayMetrics().density;

        // 1) Logo, nombre y lema entran sincronizados en el primer frame.
        logo.setAlpha(0f);
        logo.setScaleX(0.92f);
        logo.setScaleY(0.92f);

        nombre.setAlpha(0f);
        nombre.setTranslationY(14 * dp);

        lema.setAlpha(0f);
        lema.setTranslationY(14 * dp);

        logo.post(() -> {
            logo.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(420)
                    .setInterpolator(Animaciones.CURVA_SUAVE)
                    .withLayer()
                    .start();

            nombre.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(420)
                    .setInterpolator(Animaciones.CURVA_SUAVE)
                    .withLayer()
                    .start();

            lema.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(420)
                    .setInterpolator(Animaciones.CURVA_SUAVE)
                    .withLayer()
                    .start();
        });

        // 2) Dos anillos que pulsan detrás del logo
        animarPulso(pulso1, 0);
        animarPulso(pulso2, 900);

        // 3) Puntos de carga: laten uno tras otro
        animarPunto(findViewById(R.id.punto1), 0);
        animarPunto(findViewById(R.id.punto2), 120);
        animarPunto(findViewById(R.id.punto3), 240);
    }

    private void animarPulso(View anillo, long retraso) {
        ObjectAnimator animador = ObjectAnimator.ofPropertyValuesHolder(
                anillo,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.7f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.7f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.5f, 0f));

        animador.setDuration(1800);
        animador.setStartDelay(retraso);
        animador.setRepeatCount(ObjectAnimator.INFINITE);
        animador.setInterpolator(new DecelerateInterpolator());
        animador.start();

        animadoresInfinitos.add(animador);
    }

    private void animarPunto(View punto, long retraso) {
        ObjectAnimator animador = ObjectAnimator.ofPropertyValuesHolder(
                punto,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.6f, 1.2f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.6f, 1.2f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.4f, 1f));

        animador.setDuration(450);
        animador.setStartDelay(retraso);
        animador.setRepeatCount(ObjectAnimator.INFINITE);
        animador.setRepeatMode(ObjectAnimator.REVERSE);
        animador.setInterpolator(new AccelerateDecelerateInterpolator());
        animador.start();

        animadoresInfinitos.add(animador);
    }

    // ==========================================
    // SESIÓN
    // ==========================================

    private void verificarSesion() {
        if (!Sesion.haySesion(this)) {
            destino = MainActivity.class;
            intentarNavegar();
            return;
        }

        if (!Sesion.estaExpirada(this)) {
            cargarPerfil(Sesion.accessToken(this));
            return;
        }

        SupabaseHelper.refrescarSesion(
                Sesion.refreshToken(this),
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        Sesion.guardarRespuestaAuth(
                                SplashActivity.this,
                                respuesta
                        );

                        runOnUiThread(() ->
                                cargarPerfil(
                                        Sesion.accessToken(
                                                SplashActivity.this
                                        )
                                )
                        );
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> {
                            Sesion.limpiar(SplashActivity.this);
                            destino = MainActivity.class;
                            intentarNavegar();
                        });
                    }

                    @Override
                    public void onSinConexion(String mensaje) {
                        // Sin red no se puede renovar, pero la sesión local
                        // sigue sirviendo para entrar a la app.
                        runOnUiThread(() ->
                                cargarPerfil(
                                        Sesion.accessToken(
                                                SplashActivity.this
                                        )
                                )
                        );
                    }
                }
        );
    }

    /**
     * Completa los datos del perfil desde public.usuarios.
     * Nunca bloquea la entrada: si falla, se usa lo que ya está guardado.
     */
    private void cargarPerfil(String accessToken) {
        if (accessToken.isEmpty()) {
            runOnUiThread(() -> {
                destino = MainActivity.class;
                intentarNavegar();
            });
            return;
        }

        String usuarioId = Sesion.usuarioId(this);

        if (usuarioId.isEmpty()) {
            completarUsuario(accessToken);
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
                                    SplashActivity.this,
                                    perfil
                            );
                            continuarAHome();
                            return;
                        }

                        // La fila no existe: la creamos con los datos
                        // que ya tenemos de Auth.
                        crearPerfilSiFalta(accessToken, usuarioId);
                    }

                    @Override
                    public void onError(String mensaje) {
                        continuarAHome();
                    }
                }
        );
    }

    /**
     * Recupera el id/nombre/correo desde /auth/v1/user cuando la sesión
     * guardada es antigua y no tenía esos datos.
     */
    private void completarUsuario(String accessToken) {
        SupabaseHelper.obtenerUsuario(
                accessToken,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject usuario) {
                        Sesion.guardarUsuario(
                                SplashActivity.this,
                                usuario
                        );

                        String usuarioId = Sesion.usuarioId(
                                SplashActivity.this
                        );

                        if (usuarioId.isEmpty()) {
                            continuarAHome();
                        } else {
                            crearPerfilSiFalta(accessToken, usuarioId);
                        }
                    }

                    @Override
                    public void onError(String mensaje) {
                        continuarAHome();
                    }
                }
        );
    }

    private void crearPerfilSiFalta(
            String accessToken,
            String usuarioId) {

        String nombre = Sesion.nombre(this);
        String correo = Sesion.correo(this);

        if (nombre.isEmpty() || correo.isEmpty()) {
            continuarAHome();
            return;
        }

        SupabaseHelper.crearPerfilSiFalta(
                accessToken, usuarioId, nombre, correo,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject perfil) {
                        if (perfil.length() > 0) {
                            Sesion.guardarPerfil(
                                    SplashActivity.this,
                                    perfil
                            );
                        }

                        continuarAHome();
                    }

                    @Override
                    public void onError(String mensaje) {
                        continuarAHome();
                    }
                }
        );
    }

    private void continuarAHome() {
        runOnUiThread(() -> {
            destino = HomeActivity.class;
            intentarNavegar();
        });
    }

    @SuppressWarnings("deprecation")
    private void intentarNavegar() {
        if (yaNavego || !tiempoCumplido || destino == null || isFinishing()) {
            return;
        }

        yaNavego = true;

        startActivity(new Intent(this, destino));
        finish();

        // Transición suave hacia la siguiente pantalla
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);

        for (Animator animador : animadoresInfinitos) {
            animador.cancel();
        }
        animadoresInfinitos.clear();

        super.onDestroy();
    }
}