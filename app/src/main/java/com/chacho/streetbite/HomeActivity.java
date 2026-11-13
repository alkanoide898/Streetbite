package com.chacho.streetbite;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HomeActivity extends BaseActivity {

    private static final int PERMISOS_UBICACION = 2001;

    private static final int MAX_DESTACADOS = 3;

    // Catálogo completo tal como llega del repositorio.
    private final List<Foodtruck> todos = new ArrayList<>();

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

    // Ubicación del usuario
    private Location ubicacionUsuario;

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

        findViewById(R.id.btnHome).setOnClickListener(v -> {
            // Ya estamos en Home: solo el rebote del icono.
            animarBotonNav(v);
        });

        View btnPerfil = findViewById(R.id.btnPerfil);
        btnPerfil.setOnClickListener(v -> {
            animarBotonNav(v);
            startActivity(new Intent(this, PerfilActivity.class));
        });
    }

    /** Rebote corto del botón de la barra inferior al tocarlo. */
    private void animarBotonNav(View vista) {

        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }

        vista.animate().cancel();
        vista.setScaleX(1f);
        vista.setScaleY(1f);

        vista.animate()
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(90)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() ->
                        vista.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(180)
                                .setInterpolator(new OvershootInterpolator(2f))
                                .start()
                )
                .start();
    }

    /** Si una animación quedó a medias al navegar, deja los botones normales. */
    private void reiniciarBotonesNav() {

        int[] ids = {R.id.btnHome, R.id.btnIrMapa, R.id.btnPerfil};

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

        RecyclerView recyclerCercanos = findViewById(R.id.recyclerCercanos);
        recyclerCercanos.setLayoutManager(new LinearLayoutManager(this));
        adaptadorCercanos = new FoodtruckAdapter(
                R.layout.item_foodtruck,
                this::abrirDetalle
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
        adaptadorDestacados = new FoodtruckAdapter(
                R.layout.item_foodtruck_destacado,
                this::abrirDetalle
        );
        recyclerDestacados.setAdapter(adaptadorDestacados);
    }

    /**
     * El animador de filtros se activa recién después de la primera carga:
     * así la entrada escalonada no pelea con la animación de "alta" de
     * RecyclerView y conserva su retraso por tarjeta.
     */
    private void activarAnimadoresListas() {

        RecyclerView cercanos = findViewById(R.id.recyclerCercanos);
        RecyclerView destacados = findViewById(R.id.recyclerDestacados);

        if (cercanos != null && cercanos.getItemAnimator() == null) {
            configurarAnimadorLista(cercanos);
        }

        if (destacados != null && destacados.getItemAnimator() == null) {
            configurarAnimadorLista(destacados);
        }
    }

    /** Animaciones cortas al agregar, quitar o mover tarjetas por un filtro. */
    private void configurarAnimadorLista(RecyclerView recycler) {

        DefaultItemAnimator animador = new DefaultItemAnimator();
        animador.setAddDuration(180);
        animador.setRemoveDuration(140);
        animador.setMoveDuration(180);
        animador.setChangeDuration(140);
        animador.setSupportsChangeAnimations(false);

        recycler.setItemAnimator(animador);
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
                    activarAnimadoresListas();
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

    /** Pequeño "pop" del chip al elegirlo. */
    private void animarChip(View chip) {

        if (!ValueAnimator.areAnimatorsEnabled()) {
            return;
        }

        chip.animate().cancel();
        chip.setScaleX(1f);
        chip.setScaleY(1f);

        chip.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(70)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() ->
                        chip.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(170)
                                .setInterpolator(new OvershootInterpolator(2f))
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
                return "🌮 Tacos";
            case "hamburguesas":
            case "hamburguesa":
                return "🍔 Hamburguesas";
            case "completos":
            case "completo":
                return "🌭 Completos";
            case "pizza":
            case "pizzas":
                return "🍕 Pizza";
            case "sushi":
                return "🍣 Sushi";
            case "empanadas":
            case "empanada":
                return "🥟 Empanadas";
            case "papas fritas":
            case "papas":
                return "🍟 Papas fritas";
            case "cafe":
            case "café":
                return "☕ Café";
            case "postres":
            case "postre":
                return "🍰 Postres";
            case "sandwiches":
            case "sándwiches":
            case "sandwich":
            case "sandwichs":
                return "🥪 Sándwiches";
            case "pollo":
                return "🍗 Pollo";
            case "churrascos":
            case "churrasco":
                return "🥩 Churrascos";
            case "pichangas":
            case "pichanga":
                return "🥘 Pichangas";
            case "fajitas":
            case "fajita":
                return "🌯 Fajitas";
            case "bowls":
            case "bowl":
                return "🥣 Bowls";
            case "alitas":
            case "alita":
                return "🍗 Alitas";
            case "almuerzos":
            case "almuerzo":
                return "🍽️ Almuerzos";
            case "bebestibles":
                return "🥤 Bebestibles";
            case "fritos":
            case "frito":
                return "🍟 Fritos";
            case "chorrillanas":
            case "chorrillana":
                return "🍳 Chorrillanas";
            case "salchipapas":
            case "salchipapa":
                return "🍟 Salchipapas";
            case "handrolls":
            case "handroll":
                return "🍣 Handrolls";
            case "arrollados":
            case "arrollado":
                return "🌯 Arrollados";
            case "palitos de ajo":
                return "🧄 Palitos de ajo";
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
        }

        boton.setSelected(true);
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

        List<Foodtruck> destacados = new ArrayList<>(filtrados);
        Collections.sort(
                destacados,
                (a, b) -> Double.compare(b.rating, a.rating)
        );

        if (destacados.size() > MAX_DESTACADOS) {
            destacados = new ArrayList<>(
                    destacados.subList(0, MAX_DESTACADOS)
            );
        }

        adaptadorCercanos.actualizar(filtrados, ubicacionUsuario);
        adaptadorDestacados.actualizar(destacados, ubicacionUsuario);

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

        if (mejor != null) {

            ubicacionUsuario = mejor;
            aplicarFiltros();

        } else {

            Toast.makeText(
                    this,
                    "No se pudo obtener la ubicación actual",
                    Toast.LENGTH_SHORT
            ).show();
        }
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

                Toast.makeText(
                        this,
                        "Sin permiso de ubicación",
                        Toast.LENGTH_SHORT
                ).show();
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

        if (todos.isEmpty() && !cargando) {
            cargarFoodtrucks();
        }

        if (tienePermisosUbicacion() && ubicacionUsuario == null) {

            obtenerUbicacionActual();

        } else if (ubicacionUsuario != null) {

            aplicarFiltros();
        }
    }
}
