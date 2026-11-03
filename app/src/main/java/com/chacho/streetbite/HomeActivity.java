package com.chacho.streetbite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.json.JSONObject;

public class HomeActivity extends AppCompatActivity {

    private static final int PERMISOS_UBICACION = 2001;

    // Tarjetas principales que se pueden filtrar
    private final Map<CardView, Foodtruck> tarjetasPrincipales =
            new LinkedHashMap<>();

    // Estado de los filtros
    private String categoriaActual = null;
    private String textoBusqueda = "";

    private CheckBox checkSoloAbiertos;

    // Botones de categoría
    private TextView btnTodos;
    private TextView btnTacos;
    private TextView btnHamburguesas;
    private TextView btnCompletos;
    private TextView btnPizza;

    private TextView botonCategoriaSeleccionado;

    // Ubicación del usuario
    private Location ubicacionUsuario;

    // TextViews de distancia
    private TextView txtDistTaco;
    private TextView txtDistBurger;
    private TextView txtDistPizza;
    private TextView txtDistCompleto;

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

        // ============================
        // TEXTVIEWS DE DISTANCIA
        // ============================
        txtDistTaco = findViewById(R.id.txtDistTaco);
        txtDistBurger = findViewById(R.id.txtDistBurger);
        txtDistPizza = findViewById(R.id.txtDistPizza);
        txtDistCompleto = findViewById(R.id.txtDistCompleto);

        // ============================
        // NAVEGACIÓN
        // ============================
        LinearLayout btnIrMapaLayout =
                findViewById(R.id.btnIrMapa);

        LinearLayout btnHomeLayout =
                findViewById(R.id.btnHome);

        TextView btnPerfil =
                findViewById(R.id.btnPerfil);

        btnIrMapaLayout.setOnClickListener(v ->
                startActivity(
                        new Intent(this, MapActivity.class)
                )
        );

        btnHomeLayout.setOnClickListener(v -> {
            // Ya estamos en Home
        });

        btnPerfil.setOnClickListener(v ->
                startActivity(
                        new Intent(this, PerfilActivity.class)
                )
        );

        // ============================
        // TARJETAS PRINCIPALES
        // ============================
        registrarTarjetaPrincipal(
                R.id.cardTaco,
                "Taco Truck"
        );

        registrarTarjetaPrincipal(
                R.id.cardBurger,
                "Burger Truck"
        );

        registrarTarjetaPrincipal(
                R.id.cardPizza,
                "Pizza Truck"
        );

        registrarTarjetaPrincipal(
                R.id.cardCompleto,
                "Completo Express"
        );

        // ============================
        // MEJOR CALIFICADOS
        // ============================
        registrarTarjetaSimple(
                R.id.cardStreetBurger,
                "Street Burger"
        );

        registrarTarjetaSimple(
                R.id.cardTacoHouse,
                "Taco House"
        );

        registrarTarjetaSimple(
                R.id.cardCompletoExpress,
                "Completo Express"
        );

        // ============================
        // CATEGORÍAS
        // ============================
        btnTodos = findViewById(R.id.btnTodos);
        btnTacos = findViewById(R.id.btnTacos);
        btnHamburguesas =
                findViewById(R.id.btnHamburguesas);
        btnCompletos =
                findViewById(R.id.btnCompletos);
        btnPizza = findViewById(R.id.btnPizza);

        configurarCategoria(btnTodos, null);
        configurarCategoria(btnTacos, "tacos");
        configurarCategoria(
                btnHamburguesas,
                "hamburguesas"
        );
        configurarCategoria(
                btnCompletos,
                "completos"
        );
        configurarCategoria(btnPizza, "pizza");

        seleccionarCategoria(btnTodos);

        // ============================
        // SOLO ABIERTOS
        // ============================
        checkSoloAbiertos =
                findViewById(R.id.checkSoloAbiertos);

        checkSoloAbiertos.setOnCheckedChangeListener(
                (buttonView, isChecked) -> aplicarFiltros()
        );

        // ============================
        // BUSCADOR
        // ============================
        EditText edtBuscar =
                findViewById(R.id.edtBuscar);

        edtBuscar.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(
                    CharSequence s,
                    int start,
                    int count,
                    int after
            ) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s,
                    int start,
                    int before,
                    int count
            ) {
                textoBusqueda = s.toString();
                aplicarFiltros();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // ============================
        // UBICACIÓN
        // ============================
        if (tienePermisosUbicacion()) {
            obtenerUbicacionActual();
        } else {
            solicitarPermisosUbicacion();
        }

        aplicarFiltros();
    }

    // ==========================================
    // PERMISOS DE UBICACIÓN
    // ==========================================
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
                (LocationManager) getSystemService(
                        LOCATION_SERVICE
                );

        if (lm == null) {
            return;
        }

        Location mejor = null;

        for (String proveedor : lm.getProviders(true)) {

            Location ubicacion =
                    lm.getLastKnownLocation(proveedor);

            if (ubicacion != null
                    && (mejor == null
                    || ubicacion.getAccuracy()
                    < mejor.getAccuracy())) {

                mejor = ubicacion;
            }
        }

        if (mejor != null) {

            ubicacionUsuario = mejor;
            actualizarDistancias();

        } else {

            Toast.makeText(
                    this,
                    "No se pudo obtener la ubicación actual",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    // ==========================================
    // ACTUALIZAR DISTANCIAS
    // ==========================================
    private void actualizarDistancias() {

        if (ubicacionUsuario == null) {
            return;
        }

        try {

            Foodtruck taco =
                    FoodtruckRepositorio.buscarPorNombre(
                            "Taco Truck"
                    );

            if (taco != null && txtDistTaco != null) {
                txtDistTaco.setText(
                        taco.distanciaTexto(ubicacionUsuario)
                );
            }

            Foodtruck burger =
                    FoodtruckRepositorio.buscarPorNombre(
                            "Burger Truck"
                    );

            if (burger != null && txtDistBurger != null) {
                txtDistBurger.setText(
                        burger.distanciaTexto(ubicacionUsuario)
                );
            }

            Foodtruck pizza =
                    FoodtruckRepositorio.buscarPorNombre(
                            "Pizza Truck"
                    );

            if (pizza != null && txtDistPizza != null) {
                txtDistPizza.setText(
                        pizza.distanciaTexto(ubicacionUsuario)
                );
            }

            Foodtruck completo =
                    FoodtruckRepositorio.buscarPorNombre(
                            "Completo Express"
                    );

            if (completo != null
                    && txtDistCompleto != null) {

                txtDistCompleto.setText(
                        completo.distanciaTexto(ubicacionUsuario)
                );
            }

        } catch (Exception ignored) {
            // Evitar que la aplicación se cierre
        }
    }

    // ==========================================
    // FILTROS
    // ==========================================
    private void aplicarFiltros() {

        boolean soloAbiertos =
                checkSoloAbiertos != null
                        && checkSoloAbiertos.isChecked();

        for (Map.Entry<CardView, Foodtruck> entrada
                : tarjetasPrincipales.entrySet()) {

            Foodtruck foodtruck =
                    entrada.getValue();

            boolean coincideCategoria =
                    categoriaActual == null
                            || categoriaActual.equals(
                            foodtruck.categoria
                    );

            boolean coincideEstado =
                    !soloAbiertos
                            || foodtruck.abierto;

            boolean coincideTexto =
                    foodtruck.coincideCon(
                            textoBusqueda
                    );

            boolean visible =
                    coincideCategoria
                            && coincideEstado
                            && coincideTexto;

            entrada.getKey().setVisibility(
                    visible
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    // ==========================================
    // REGISTRAR TARJETAS
    // ==========================================
    private void registrarTarjetaPrincipal(
            int idCard,
            String nombreFoodtruck
    ) {

        CardView card =
                findViewById(idCard);

        Foodtruck foodtruck =
                FoodtruckRepositorio.buscarPorNombre(
                        nombreFoodtruck
                );

        if (card == null || foodtruck == null) {
            return;
        }

        tarjetasPrincipales.put(
                card,
                foodtruck
        );

        card.setOnClickListener(v ->
                abrirDetalle(foodtruck)
        );
    }

    private void registrarTarjetaSimple(
            int idCard,
            String nombreFoodtruck
    ) {

        CardView card =
                findViewById(idCard);

        Foodtruck foodtruck =
                FoodtruckRepositorio.buscarPorNombre(
                        nombreFoodtruck
                );

        if (card == null || foodtruck == null) {
            return;
        }

        card.setOnClickListener(v ->
                abrirDetalle(foodtruck)
        );
    }

    // ==========================================
    // CATEGORÍAS
    // ==========================================
    private void configurarCategoria(
            TextView boton,
            String categoria
    ) {

        boton.setOnClickListener(v -> {

            seleccionarCategoria(boton);

            categoriaActual = categoria;

            aplicarFiltros();
        });
    }

    private void seleccionarCategoria(
            TextView boton
    ) {

        if (botonCategoriaSeleccionado != null) {

            botonCategoriaSeleccionado.setSelected(
                    false
            );
        }

        boton.setSelected(true);

        botonCategoriaSeleccionado = boton;
    }

    // ==========================================
    // ABRIR DETALLE
    // ==========================================
    private void abrirDetalle(
            Foodtruck foodtruck
    ) {

        startActivity(
                foodtruck.crearIntentDetalle(
                        this,
                        ubicacionUsuario
                )
        );
    }

    // ==========================================
    // RESPUESTA DE PERMISOS
    // ==========================================
    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == PERMISOS_UBICACION) {

            if (grantResults.length > 0
                    && grantResults[0]
                    == PackageManager.PERMISSION_GRANTED) {

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
                        // No se expulsa al usuario: los datos de Home
                        // siguen disponibles sin conexión.
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

        if (tienePermisosUbicacion()
                && ubicacionUsuario == null) {

            obtenerUbicacionActual();

        } else if (ubicacionUsuario != null) {

            actualizarDistancias();
        }
    }
}