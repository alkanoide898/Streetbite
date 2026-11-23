package com.chacho.streetbite;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class HomeActivity extends BaseActivity {

    private static final int PERMISOS_UBICACION = 2001;

    private static final int MAX_CERCANOS = 4;
    private static final int MAX_DESTACADOS = 3;

    // Catálogo completo tal como llega del repositorio.
    private final List<Foodtruck> todos = new ArrayList<>();

    private RecyclerView recyclerCercanos;
    private FoodtruckAdapter adaptadorCercanos;
    private FoodtruckAdapter adaptadorDestacados;

    private TextView txtSinResultados;

    // Estado de los filtros
    private String categoriaActual = null;
    private String textoBusqueda = "";

    private CheckBox checkSoloAbiertos;

    // Contenedor de chips de categoría (se generan desde los datos).
    private LinearLayout contenedorCategorias;

    private TextView botonCategoriaSeleccionado;

    // Ubicación de referencia (centro de San Carlos si no hay GPS local)
    private Location ubicacionUsuario = Foodtruck.ubicacionEnSanCarlos(null);

    // Sensor acelerómetro (agitar para recomendar un local)
    private DetectorAgitacion detectorAgitacion;
    private AlertDialog dialogoRecomendacion;
    private View vistaRecomendacion;
    private Foodtruck foodtruckRecomendado;
    private String ultimoRecomendadoId = "";
    private final Random aleatorio = new Random();

    private boolean cargando = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ============================
        // VALIDACIÓN DE SESIÓN
        // ============================
        if (!Sesion.haySesion(this)) {
            irAlLogin();
            return;
        }

        renovarSesionSiHaceFalta();

        // ============================
        // CARGAR INTERFAZ
        // ============================
        setContentView(R.layout.activity_home);

        configurarNavegacion();
        configurarCategorias();
        configurarBuscador();
        configurarListas();
        configurarUbicacion();
        configurarSensorAgitacion();

        cargarFoodtrucks();
    }

    // ==========================================
    // NAVEGACIÓN
    // ==========================================
    private void configurarNavegacion() {

        View btnMapa = findViewById(R.id.btnIrMapa);
        btnMapa.setOnClickListener(v -> {
            animarBotonNav(v);
            startActivity(new Intent(this, MapActivity.class));
        });

        View btnFavoritosNav = findViewById(R.id.btnIrFavoritos);
        btnFavoritosNav.setOnClickListener(v -> {
            animarBotonNav(v);
            startActivity(new Intent(this, FavoritosActivity.class));
        });

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            // Ya estamos en Home: solo el rebote del icono.
            animarBotonNav(v);
        });

        View btnPerfil = findViewById(R.id.btnPerfil);
        btnPerfil.setOnClickListener(v -> {
            animarBotonNav(v);
            startActivity(new Intent(this, PerfilActivity.class));
        });

        findViewById(R.id.btnComoFunciona).setOnClickListener(v ->
                startActivity(new Intent(this, VideoAyudaActivity.class))
        );
    }

    /** Feedback corto y suave del botón de la barra inferior al tocarlo. */
    private void animarBotonNav(View vista) {

        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }

        vista.animate().cancel();
        vista.setScaleX(1f);
        vista.setScaleY(1f);

        vista.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(70)
                .setInterpolator(Animaciones.CURVA_SUAVE)
                .withEndAction(() ->
                        vista.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(130)
                                .setInterpolator(Animaciones.CURVA_SUAVE)
                                .start()
                )
                .start();
    }

    /** Si una animación quedó a medias al navegar, deja los botones normales. */
    private void reiniciarBotonesNav() {

        int[] ids = {R.id.btnHome, R.id.btnIrMapa, R.id.btnIrFavoritos, R.id.btnPerfil};

        for (int id : ids) {
            View vista = findViewById(id);

            if (vista != null) {
                vista.animate().cancel();
                vista.setScaleX(1f);
                vista.setScaleY(1f);
            }
        }
    }

    // ==========================================
    // LISTAS
    // ==========================================
    private void configurarListas() {

        txtSinResultados = findViewById(R.id.txtSinResultados);

        recyclerCercanos = findViewById(R.id.recyclerCercanos);
        recyclerCercanos.setLayoutManager(new LinearLayoutManager(this));
        recyclerCercanos.setItemAnimator(null);
        adaptadorCercanos = new FoodtruckAdapter(
                R.layout.item_foodtruck,
                this::abrirDetalle
        );
        adaptadorCercanos.setOnCompartirClick(foodtruck ->
                DialogoCompartirFavorito.mostrarParaFoodtruck(this, foodtruck)
        );
        recyclerCercanos.setAdapter(adaptadorCercanos);

        RecyclerView recyclerDestacados = findViewById(R.id.recyclerDestacados);
        recyclerDestacados.setLayoutManager(
                new LinearLayoutManager(
                        this,
                        LinearLayoutManager.HORIZONTAL,
                        false
                )
        );
        recyclerDestacados.setItemAnimator(null);
        adaptadorDestacados = new FoodtruckAdapter(
                R.layout.item_foodtruck_destacado,
                this::abrirDetalle
        );
        recyclerDestacados.setAdapter(adaptadorDestacados);
    }

    private void cargarFoodtrucks() {

        cargando = true;

        if (txtSinResultados != null) {
            txtSinResultados.setText("Cargando foodtrucks...");
            txtSinResultados.setVisibility(View.VISIBLE);
        }

        FoodtruckRepositorio.cargar((foodtrucks, desdeSupabase) ->
                runOnUiThread(() -> {
                    cargando = false;

                    todos.clear();
                    todos.addAll(foodtrucks);

                    reconstruirCategorias();

                    if (!desdeSupabase) {
                        Toast.makeText(
                                HomeActivity.this,
                                "Mostrando catálogo local: no se pudo cargar desde Supabase.",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    aplicarFiltros();
                })
        );
    }

    // ==========================================
    // CATEGORÍAS
    // ==========================================
    private void configurarCategorias() {

        contenedorCategorias = findViewById(R.id.contenedorCategorias);

        reconstruirCategorias();
    }

    /**
     * Genera un chip por cada categoría que exista en el catálogo.
     * Así, si en San Carlos aparecen categorías nuevas (sushi, empanadas,
     * café...), se muestran solas sin tocar el layout.
     */
    private void reconstruirCategorias() {

        if (contenedorCategorias == null) {
            return;
        }

        List<String> categorias = new ArrayList<>();

        for (Foodtruck foodtruck : todos) {

            for (String categoria : foodtruck.categorias()) {

                categoria = categoria.trim();

                if (categoria.isEmpty()) {
                    continue;
                }

                boolean repetida = false;

                for (String existente : categorias) {
                    if (existente.equalsIgnoreCase(categoria)) {
                        repetida = true;
                        break;
                    }
                }

                if (!repetida) {
                    categorias.add(categoria);
                }
            }
        }

        Collections.sort(categorias, String.CASE_INSENSITIVE_ORDER);

        // Si la categoría seleccionada ya no existe, volvemos a "Todos".
        if (categoriaActual != null) {

            boolean existe = false;

            for (String categoria : categorias) {
                if (categoria.equalsIgnoreCase(categoriaActual)) {
                    categoriaActual = categoria;
                    existe = true;
                    break;
                }
            }

            if (!existe) {
                categoriaActual = null;
            }
        }

        contenedorCategorias.removeAllViews();
        botonCategoriaSeleccionado = null;

        TextView chipTodos = crearChip("Todos", null);
        contenedorCategorias.addView(chipTodos);

        if (categoriaActual == null) {
            seleccionarCategoria(chipTodos);
        }

        for (String categoria : categorias) {

            TextView chip = crearChip(
                    etiquetaCategoria(categoria),
                    categoria
            );

            contenedorCategorias.addView(chip);

            if (categoria.equalsIgnoreCase(categoriaActual)) {
                seleccionarCategoria(chip);
            }
        }
    }

    private TextView crearChip(String texto, String categoria) {

        TextView chip = new TextView(this);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(38)
        );

        if (contenedorCategorias.getChildCount() > 0) {
            params.setMarginStart(dp(8));
        }

        chip.setLayoutParams(params);
        chip.setBackgroundResource(R.drawable.bg_sb_chip);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(16), 0, dp(16), 0);
        chip.setText(texto);
        chip.setTextColor(ContextCompat.getColor(this, R.color.sb_marron));
        chip.setTextSize(14f);
        chip.setClickable(true);
        chip.setFocusable(true);

        chip.setOnClickListener(v -> {
            seleccionarCategoria(chip);
            categoriaActual = categoria;
            animarChip(chip);
            desplazarChipALaVista(chip);
            aplicarFiltros();
        });

        return chip;
    }

    /** Micro-feedback suave del chip al elegirlo. */
    private void animarChip(View chip) {

        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }

        chip.animate().cancel();
        chip.setScaleX(1f);
        chip.setScaleY(1f);

        chip.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(70)
                .setInterpolator(Animaciones.CURVA_SUAVE)
                .withEndAction(() ->
                        chip.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(130)
                                .setInterpolator(Animaciones.CURVA_SUAVE)
                                .start()
                )
                .start();
    }

    /** Centra el chip elegido si quedó fuera de la pantalla. */
    private void desplazarChipALaVista(View chip) {

        View padre = (View) chip.getParent();

        if (padre == null
                || !(padre.getParent() instanceof HorizontalScrollView)) {
            return;
        }

        HorizontalScrollView scroll =
                (HorizontalScrollView) padre.getParent();

        int destino = chip.getLeft()
                - (scroll.getWidth() - chip.getWidth()) / 2;

        scroll.smoothScrollTo(Math.max(0, destino), 0);
    }

    private String etiquetaCategoria(String categoria) {

        switch (categoria.toLowerCase(Locale.ROOT)) {

            case "tacos":
                return "Tacos";
            case "hamburguesas":
            case "hamburguesa":
                return "Hamburguesas";
            case "completos":
            case "completo":
                return "Completos";
            case "pizza":
            case "pizzas":
                return "Pizzas";
            case "sushi":
                return "Sushi";
            case "empanadas":
            case "empanada":
                return "Empanadas";
            case "papas fritas":
            case "papas":
                return "Papas fritas";
            case "cafe":
            case "café":
                return "Café";
            case "postres":
            case "postre":
                return "Postres";
            case "sandwiches":
            case "sándwiches":
            case "sandwich":
            case "sandwichs":
                return "Sándwiches";
            case "pollo":
                return "Pollo";
            case "churrascos":
            case "churrasco":
                return "Churrascos";
            case "pichangas":
            case "pichanga":
                return "Pichangas";
            case "fajitas":
            case "fajita":
                return "Fajitas";
            case "bowls":
            case "bowl":
                return "Bowls";
            case "alitas":
            case "alita":
                return "Alitas";
            case "almuerzos":
            case "almuerzo":
                return "Almuerzos";
            case "bebestibles":
                return "Bebestibles";
            case "fritos":
            case "frito":
                return "Fritos";
            case "chorrillanas":
            case "chorrillana":
                return "Chorrillanas";
            case "salchipapas":
            case "salchipapa":
                return "Salchipapas";
            case "handrolls":
            case "handroll":
                return "Handrolls";
            case "arrollados":
            case "arrollado":
                return "Arrollados";
            case "palitos de ajo":
                return "Palitos de ajo";
            default:
                return capitalizar(categoria);
        }
    }

    private String capitalizar(String texto) {

        if (texto == null || texto.isEmpty()) {
            return "";
        }

        return texto.substring(0, 1).toUpperCase(Locale.getDefault())
                + texto.substring(1);
    }

    private int dp(int valor) {
        return Math.round(
                getResources().getDisplayMetrics().density * valor
        );
    }

    private void seleccionarCategoria(TextView boton) {

        if (boton == null) {
            return;
        }

        if (botonCategoriaSeleccionado != null) {
            botonCategoriaSeleccionado.setSelected(false);
            botonCategoriaSeleccionado.setTextColor(
                    ContextCompat.getColor(this, R.color.sb_marron)
            );
            botonCategoriaSeleccionado.setTypeface(
                    null,
                    android.graphics.Typeface.NORMAL
            );
        }

        boton.setSelected(true);
        boton.setTextColor(
                ContextCompat.getColor(this, R.color.sb_texto_sobre_acento)
        );
        boton.setTypeface(null, android.graphics.Typeface.BOLD);
        botonCategoriaSeleccionado = boton;
    }

    // ==========================================
    // BUSCADOR Y SOLO ABIERTOS
    // ==========================================
    private void configurarBuscador() {

        checkSoloAbiertos = findViewById(R.id.checkSoloAbiertos);

        if (checkSoloAbiertos != null) {
            checkSoloAbiertos.setOnCheckedChangeListener(
                    (buttonView, isChecked) -> aplicarFiltros()
            );
        }

        EditText edtBuscar = findViewById(R.id.edtBuscar);

        if (edtBuscar == null) {
            return;
        }

        edtBuscar.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count) {
                textoBusqueda = s.toString();
                aplicarFiltros();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    // ==========================================
    // FILTROS
    // ==========================================
    private void aplicarFiltros() {

        if (adaptadorCercanos == null || adaptadorDestacados == null) {
            return;
        }

        boolean soloAbiertos =
                checkSoloAbiertos != null && checkSoloAbiertos.isChecked();

        List<Foodtruck> filtrados = new ArrayList<>();

        for (Foodtruck foodtruck : todos) {

            boolean coincideCategoria =
                    categoriaActual == null
                            || foodtruck.tieneCategoria(categoriaActual);

            boolean coincideEstado = !soloAbiertos || foodtruck.abierto;

            boolean coincideTexto = foodtruck.coincideCon(textoBusqueda);

            if (coincideCategoria && coincideEstado && coincideTexto) {
                filtrados.add(foodtruck);
            }
        }

        Collections.sort(filtrados, (a, b) -> {
            int cmp = Float.compare(
                    a.distanciaMetros(ubicacionUsuario),
                    b.distanciaMetros(ubicacionUsuario)
            );
            return cmp != 0 ? cmp : a.nombre.compareToIgnoreCase(b.nombre);
        });

        List<Foodtruck> destacados = new ArrayList<>(filtrados);
        Collections.sort(destacados, (a, b) -> {
            int cmp = Double.compare(b.rating, a.rating);
            return cmp != 0 ? cmp : a.nombre.compareToIgnoreCase(b.nombre);
        });

        if (destacados.size() > MAX_DESTACADOS) {
            destacados = new ArrayList<>(
                    destacados.subList(0, MAX_DESTACADOS)
            );
        }

        List<Foodtruck> cercanos = filtrados.size() > MAX_CERCANOS
                ? new ArrayList<>(filtrados.subList(0, MAX_CERCANOS))
                : filtrados;

        adaptadorCercanos.actualizar(cercanos, ubicacionUsuario);
        adaptadorDestacados.actualizar(destacados, ubicacionUsuario);
        if (recyclerCercanos != null) {
            recyclerCercanos.requestLayout();
        }

        if (txtSinResultados != null) {

            boolean vacio = filtrados.isEmpty();

            if (vacio && !cargando) {
                txtSinResultados.setText(
                        "No hay foodtrucks que coincidan con tu búsqueda."
                );
            }

            txtSinResultados.setVisibility(
                    vacio ? View.VISIBLE : View.GONE
            );
        }
    }

    // ==========================================
    // UBICACIÓN
    // ==========================================
    private void configurarUbicacion() {

        if (tienePermisosUbicacion()) {
            obtenerUbicacionActual();
        } else {
            solicitarPermisosUbicacion();
        }
    }

    private boolean tienePermisosUbicacion() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermisosUbicacion() {

        ActivityCompat.requestPermissions(
                this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                PERMISOS_UBICACION
        );
    }

    @SuppressLint("MissingPermission")
    private void obtenerUbicacionActual() {

        LocationManager lm =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        if (lm == null) {
            return;
        }

        Location mejor = null;

        for (String proveedor : lm.getProviders(true)) {

            Location ubicacion = lm.getLastKnownLocation(proveedor);

            if (ubicacion != null
                    && (mejor == null
                    || ubicacion.getAccuracy() < mejor.getAccuracy())) {

                mejor = ubicacion;
            }
        }

        ubicacionUsuario = Foodtruck.ubicacionEnSanCarlos(mejor);
        aplicarFiltros();
    }

    // ==========================================
    // ABRIR DETALLE
    // ==========================================
    private void abrirDetalle(Foodtruck foodtruck, View foto) {

        Intent intent = foodtruck.crearIntentDetalle(this, ubicacionUsuario);

        String nombreTransicion = foto == null
                ? null
                : foto.getTransitionName();

        if (nombreTransicion == null || nombreTransicion.isEmpty()) {
            startActivity(intent);
            return;
        }

        intent.putExtra("transicion_foto", nombreTransicion);

        ActivityOptionsCompat opciones =
                ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        foto,
                        nombreTransicion
                );

        startActivity(intent, opciones.toBundle());
    }

    // ==========================================
    // RESPUESTA DE PERMISOS
    // ==========================================
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISOS_UBICACION) {

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                obtenerUbicacionActual();

            } else {
                ubicacionUsuario = Foodtruck.ubicacionEnSanCarlos(null);
                aplicarFiltros();
            }
        }
    }

    // ==========================================
    // SESIÓN
    // ==========================================
    private void renovarSesionSiHaceFalta() {

        if (!Sesion.estaExpirada(this)) {
            return;
        }

        SupabaseHelper.refrescarSesion(
                Sesion.refreshToken(this),
                new SupabaseHelper.AuthCallback() {

                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        Sesion.guardarRespuestaAuth(
                                HomeActivity.this,
                                respuesta
                        );
                    }

                    @Override
                    public void onError(String mensaje) {
                        // No se expulsa al usuario: Home sigue disponible.
                    }
                }
        );
    }

    private void irAlLogin() {

        Intent intent = new Intent(HomeActivity.this, MainActivity.class);

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    // ==========================================
    // AL VOLVER A HOME
    // ==========================================
    @Override
    protected void onResume() {

        super.onResume();

        reiniciarBotonesNav();

        if (detectorAgitacion != null) {
            detectorAgitacion.iniciar();
        }

        if (todos.isEmpty() && !cargando) {
            cargarFoodtrucks();
        }

        if (tienePermisosUbicacion() && ubicacionUsuario == null) {

            obtenerUbicacionActual();

        } else if (ubicacionUsuario != null) {

            aplicarFiltros();
        }
    }

    @Override
    protected void onPause() {
        if (detectorAgitacion != null) {
            detectorAgitacion.detener();
        }
        super.onPause();
    }

    // ==========================================
    // SENSOR ACELERÓMETRO (AGITAR PARA ELEGIR)
    // ==========================================
    private void configurarSensorAgitacion() {
        detectorAgitacion = new DetectorAgitacion(this, this::alAgitarTelefono);

        View btnSugerencia = findViewById(R.id.btnSugerenciaAgitar);
        if (btnSugerencia != null) {
            btnSugerencia.setOnClickListener(v -> recomendarFoodtruckAleatorio());
        }
    }

    private void alAgitarTelefono() {
        runOnUiThread(() -> {
            getWindow().getDecorView().performHapticFeedback(
                    HapticFeedbackConstants.LONG_PRESS
            );
            Sonidos.tocar(HomeActivity.this);
            recomendarFoodtruckAleatorio();
        });
    }

    private void recomendarFoodtruckAleatorio() {
        if (todos.isEmpty()) {
            Toast.makeText(
                    this,
                    "Aún cargando foodtrucks...",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        // Prioridad: 1) abiertos de la categoría elegida, 2) abiertos, 3) todos.
        List<Foodtruck> candidatos = new ArrayList<>();

        for (Foodtruck ft : todos) {
            boolean coincideCat = categoriaActual == null
                    || ft.tieneCategoria(categoriaActual);
            if (ft.abierto && coincideCat) {
                candidatos.add(ft);
            }
        }

        if (candidatos.isEmpty()) {
            for (Foodtruck ft : todos) {
                if (ft.abierto) {
                    candidatos.add(ft);
                }
            }
        }

        if (candidatos.isEmpty()) {
            candidatos.addAll(todos);
        }

        Foodtruck elegido = candidatos.get(aleatorio.nextInt(candidatos.size()));
        if (candidatos.size() > 1) {
            String clave = elegido.id.isEmpty() ? elegido.nombre : elegido.id;
            int intentos = 0;
            while (clave.equals(ultimoRecomendadoId) && intentos < 5) {
                elegido = candidatos.get(aleatorio.nextInt(candidatos.size()));
                clave = elegido.id.isEmpty() ? elegido.nombre : elegido.id;
                intentos++;
            }
        }

        ultimoRecomendadoId = elegido.id.isEmpty() ? elegido.nombre : elegido.id;
        foodtruckRecomendado = elegido;

        if (vistaRecomendacion == null) {
            vistaRecomendacion = getLayoutInflater().inflate(
                    R.layout.dialog_recomendacion, null);

            vistaRecomendacion.findViewById(R.id.btnCerrarRecomendacion)
                    .setOnClickListener(v -> {
                        Sonidos.tocar(HomeActivity.this);
                        if (dialogoRecomendacion != null) {
                            dialogoRecomendacion.dismiss();
                        }
                    });

            vistaRecomendacion.findViewById(R.id.btnOtroRecomendado)
                    .setOnClickListener(v -> {
                        Sonidos.tocar(HomeActivity.this);
                        recomendarFoodtruckAleatorio();
                    });

            vistaRecomendacion.findViewById(R.id.btnVerRecomendado)
                    .setOnClickListener(v -> {
                        Sonidos.tocar(HomeActivity.this);
                        if (dialogoRecomendacion != null) {
                            dialogoRecomendacion.dismiss();
                        }
                        if (foodtruckRecomendado != null) {
                            abrirDetalle(foodtruckRecomendado, null);
                        }
                    });
        }

        actualizarVistaRecomendacion(vistaRecomendacion, elegido);

        if (dialogoRecomendacion == null) {
            dialogoRecomendacion = new AlertDialog.Builder(this)
                    .setView(vistaRecomendacion)
                    .create();

            if (dialogoRecomendacion.getWindow() != null) {
                dialogoRecomendacion.getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.TRANSPARENT)
                );
            }
        }

        if (!dialogoRecomendacion.isShowing()) {
            dialogoRecomendacion.show();
        } else if (ValueAnimator.areAnimatorsEnabled()) {
            vistaRecomendacion.animate().cancel();
            vistaRecomendacion.setAlpha(0.6f);
            vistaRecomendacion.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(Animaciones.CURVA_SUAVE)
                    .start();
        }
    }

    private void actualizarVistaRecomendacion(View vista, Foodtruck elegido) {
        ImageView imgLogo = vista.findViewById(R.id.imgRecomendacionLogo);
        TextView txtNombre = vista.findViewById(R.id.txtRecomendacionNombre);
        TextView txtDesc = vista.findViewById(R.id.txtRecomendacionDescripcion);
        TextView txtRating = vista.findViewById(R.id.txtRecomendacionRating);
        TextView txtDistancia = vista.findViewById(R.id.txtRecomendacionDistancia);
        TextView txtEstado = vista.findViewById(R.id.txtRecomendacionEstado);
        View bloqueInfo = vista.findViewById(R.id.bloqueRecomendacionInfo);
        TextView txtDireccion = vista.findViewById(R.id.txtRecomendacionDireccion);
        TextView txtHorario = vista.findViewById(R.id.txtRecomendacionHorario);

        if (!elegido.logoNombre.isEmpty()) {
            imgLogo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imgLogo.setPadding(dp(8), dp(8), dp(8), dp(8));
            Imagenes.cargar(imgLogo, elegido.logoNombre, 140, 140);
        } else {
            imgLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgLogo.setPadding(0, 0, 0, 0);
            imgLogo.setImageResource(elegido.imagen);
        }

        txtNombre.setText(elegido.nombre);
        if (elegido.descripcion.isEmpty()) {
            txtDesc.setVisibility(View.GONE);
        } else {
            txtDesc.setText(elegido.descripcion);
            txtDesc.setVisibility(View.VISIBLE);
        }

        txtRating.setText(elegido.ratingTexto());
        txtDistancia.setText(elegido.distanciaTexto(ubicacionUsuario));

        txtEstado.setText(elegido.estadoTexto());
        int colorEstado = getColor(
                elegido.abierto ? R.color.sb_verde : R.color.sb_rojo
        );
        txtEstado.setTextColor(colorEstado);
        txtEstado.setBackgroundResource(
                elegido.abierto
                        ? R.drawable.bg_badge_abierto
                        : R.drawable.bg_badge_cerrado
        );
        txtEstado.setCompoundDrawableTintList(
                ColorStateList.valueOf(colorEstado)
        );

        boolean tieneDireccion = !elegido.direccion.trim().isEmpty();
        boolean tieneHorario = !elegido.horario.trim().isEmpty();

        if (!tieneDireccion && !tieneHorario) {
            bloqueInfo.setVisibility(View.GONE);
        } else {
            bloqueInfo.setVisibility(View.VISIBLE);
            if (tieneDireccion) {
                txtDireccion.setText(elegido.direccion.trim());
                txtDireccion.setVisibility(View.VISIBLE);
            } else {
                txtDireccion.setVisibility(View.GONE);
            }

            if (tieneHorario) {
                txtHorario.setText(elegido.horario.trim());
                txtHorario.setVisibility(View.VISIBLE);
            } else {
                txtHorario.setVisibility(View.GONE);
            }
        }
    }
}
