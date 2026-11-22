package com.chacho.streetbite;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends BaseActivity {

    private static final int PERMISOS_UBICACION = 1001;
    private static final String PREFS_UBICACION = "StreetBiteUbicacion";

    // San Carlos, Ñuble. Centro por defecto cuando no hay GPS local.
    private static final double SAN_CARLOS_LAT = Foodtruck.SAN_CARLOS_LAT;
    private static final double SAN_CARLOS_LON = Foodtruck.SAN_CARLOS_LON;
    private static final String CIUDAD_POR_DEFECTO = "San Carlos";

    // Zoom inicial más cercano para distinguir locales cercanos entre sí.
    private static final double ZOOM_INICIAL = 16.3;
    private static final double ZOOM_ENFOQUE_LOCAL = 17.2;

    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private GeoPoint puntoUsuario;
    private Location ubicacionReferencia = Foodtruck.ubicacionEnSanCarlos(null);
    private boolean camaraInicializada = false;

    // Tarjeta flotante inferior
    private View cardDetalleMapa;
    private ImageView imgMapaFoodtruck;
    private TextView txtMapaNombre;
    private TextView txtMapaDescripcion;
    private TextView txtMapaRating;
    private TextView txtMapaDistancia;
    private TextView txtMapaEstado;
    private TextView txtMapaDireccion;
    private Button btnVerDetalleMapa;
    private Foodtruck foodtruckSeleccionado;

    private final List<Marker> marcadores = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuración obligatoria para osmdroid
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        // Vistas del mapa y controles
        mapView = findViewById(R.id.mapView);
        TextView btnVolverMapa = findViewById(R.id.btnVolverMapa);
        CardView btnMiUbicacion = findViewById(R.id.btnMiUbicacion);

        // Vistas de la tarjeta inferior de detalle
        cardDetalleMapa = findViewById(R.id.cardDetalleMapa);
        imgMapaFoodtruck = findViewById(R.id.imgMapaFoodtruck);
        txtMapaNombre = findViewById(R.id.txtMapaNombre);
        txtMapaDescripcion = findViewById(R.id.txtMapaDescripcion);
        txtMapaRating = findViewById(R.id.txtMapaRating);
        txtMapaDistancia = findViewById(R.id.txtMapaDistancia);
        txtMapaEstado = findViewById(R.id.txtMapaEstado);
        txtMapaDireccion = findViewById(R.id.txtMapaDireccion);
        btnVerDetalleMapa = findViewById(R.id.btnVerDetalleMapa);
        View btnCerrarTarjeta = findViewById(R.id.btnCerrarTarjetaMapa);

        // Configuración básica del mapa: centrado en San Carlos con zoom cercano
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(ZOOM_INICIAL);
        mapView.getController().setCenter(
                new GeoPoint(SAN_CARLOS_LAT, SAN_CARLOS_LON)
        );

        // Al tocar un área vacía del mapa se oculta la tarjeta
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                ocultarTarjetaFoodtruck();
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));

        // Volver y acciones de tarjeta
        btnVolverMapa.setOnClickListener(v -> finish());
        btnMiUbicacion.setOnClickListener(v -> centrarEnMiUbicacion());
        btnCerrarTarjeta.setOnClickListener(v -> ocultarTarjetaFoodtruck());
        btnVerDetalleMapa.setOnClickListener(v -> abrirDetalleSeleccionado());

        // Comprobar permisos
        if (tienePermisosUbicacion()) {
            iniciarUbicacion();
        } else {
            solicitarPermisosUbicacion();
        }

        // Añadir marcadores de foodtrucks
        cargarFoodtrucks();
    }

    private boolean tienePermisosUbicacion() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void solicitarPermisosUbicacion() {
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                PERMISOS_UBICACION);
    }

    @SuppressLint("MissingPermission")
    private void iniciarUbicacion() {
        if (tienePermisosUbicacion()) {
            if (locationOverlay == null) {
                locationOverlay = new MyLocationNewOverlay(
                        new GpsMyLocationProvider(this),
                        mapView
                );
                mapView.getOverlays().add(locationOverlay);
            }
            locationOverlay.enableMyLocation();
        }

        // 1) PRIORIDAD: Ubicación seleccionada desde Home (solo si está en San Carlos)
        SharedPreferences prefsUbic = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        boolean tieneGuardada = prefsUbic.contains("lat") && prefsUbic.contains("lon");

        if (tieneGuardada) {
            float latGuardada = prefsUbic.getFloat("lat", (float) SAN_CARLOS_LAT);
            float lonGuardada = prefsUbic.getFloat("lon", (float) SAN_CARLOS_LON);
            Location guardada = new Location("prefs");
            guardada.setLatitude(latGuardada);
            guardada.setLongitude(lonGuardada);

            Location enSanCarlos = Foodtruck.ubicacionEnSanCarlos(guardada);
            if (enSanCarlos == guardada) {
                ubicacionReferencia = guardada;
                GeoPoint puntoGuardado = new GeoPoint(
                        (double) latGuardada, (double) lonGuardada);
                mapView.getController().setZoom(ZOOM_INICIAL);
                mapView.getController().setCenter(puntoGuardado);
                camaraInicializada = true;
                return;
            }
        }

        // 2) SI HAY GPS: solo centrar si el usuario está dentro de San Carlos
        Location mejor = null;
        if (tienePermisosUbicacion()) {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm != null) {
                mejor = obtenerUltimaUbicacion(lm);
            }
        }

        Location valida = Foodtruck.ubicacionEnSanCarlos(mejor);
        ubicacionReferencia = valida;

        if (mejor != null && valida == mejor) {
            puntoUsuario = new GeoPoint(mejor.getLatitude(), mejor.getLongitude());
            mapView.getController().setZoom(ZOOM_INICIAL);
            mapView.getController().setCenter(puntoUsuario);
            camaraInicializada = true;
            return;
        }

        // 3) Por defecto (o si el emulador/GPS está fuera de San Carlos): centro de San Carlos
        GeoPoint sanCarlos = new GeoPoint(SAN_CARLOS_LAT, SAN_CARLOS_LON);
        mapView.getController().setZoom(ZOOM_INICIAL);
        mapView.getController().setCenter(sanCarlos);
        camaraInicializada = true;
    }

    @SuppressLint("MissingPermission")
    private Location obtenerUltimaUbicacion(LocationManager lm) {
        Location mejor = null;
        for (String proveedor : lm.getProviders(true)) {
            Location l = lm.getLastKnownLocation(proveedor);
            if (l != null && (mejor == null || l.getAccuracy() < mejor.getAccuracy())) {
                mejor = l;
            }
        }
        return mejor;
    }

    private void centrarEnMiUbicacion() {
        GeoPoint gpsLocal = null;
        if (locationOverlay != null && locationOverlay.getMyLocation() != null) {
            GeoPoint candidato = locationOverlay.getMyLocation();
            Location loc = new Location("overlay");
            loc.setLatitude(candidato.getLatitude());
            loc.setLongitude(candidato.getLongitude());
            if (Foodtruck.ubicacionEnSanCarlos(loc) == loc) {
                gpsLocal = candidato;
                ubicacionReferencia = loc;
            }
        }

        if (gpsLocal != null) {
            mapView.getController().animateTo(gpsLocal, ZOOM_ENFOQUE_LOCAL, 350L);
            Toast.makeText(this, "Centrado en tu ubicación", Toast.LENGTH_SHORT).show();
            return;
        }

        if (puntoUsuario != null) {
            mapView.getController().animateTo(puntoUsuario, ZOOM_ENFOQUE_LOCAL, 350L);
            return;
        }

        GeoPoint centroSanCarlos = new GeoPoint(SAN_CARLOS_LAT, SAN_CARLOS_LON);
        mapView.getController().animateTo(centroSanCarlos, ZOOM_INICIAL, 350L);
        Toast.makeText(
                this,
                "Centrado en " + CIUDAD_POR_DEFECTO,
                Toast.LENGTH_SHORT
        ).show();
    }

    private void cargarFoodtrucks() {
        FoodtruckRepositorio.cargar((foodtrucks, desdeSupabase) ->
                runOnUiThread(this::añadirMarcadoresFoodtrucks)
        );
    }

    private void añadirMarcadoresFoodtrucks() {
        // Quita solo los marcadores anteriores, no la capa de ubicación ni eventos.
        mapView.getOverlays().removeAll(marcadores);
        marcadores.clear();

        List<Foodtruck> lista = FoodtruckRepositorio.obtenerTodos();

        // OJO: cada Marker necesita su PROPIO Drawable. Si se comparte la misma
        // instancia, osmdroid le cambia los bounds a todos a la vez y termina
        // dibujando un solo marcador gigante.
        for (Foodtruck f : lista) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(f.lat, f.lon));
            marker.setTitle(f.nombre);
            marker.setInfoWindow(null);
            marker.setIcon(crearIconoMarcador());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setOnMarkerClickListener((m, mv) -> {
                mostrarTarjetaFoodtruck(f);
                double zoomDestino = Math.max(
                        mv.getZoomLevelDouble(),
                        ZOOM_ENFOQUE_LOCAL
                );
                mv.getController().animateTo(
                        m.getPosition(),
                        zoomDestino,
                        350L
                );
                return true;
            });
            mapView.getOverlays().add(marker);
            marcadores.add(marker);
        }

        mapView.invalidate();
    }

    private void mostrarTarjetaFoodtruck(Foodtruck f) {
        if (f == null || cardDetalleMapa == null) {
            return;
        }

        foodtruckSeleccionado = f;

        txtMapaNombre.setText(f.nombre);
        txtMapaDescripcion.setText(f.descripcion);
        txtMapaRating.setText(f.ratingTexto());
        txtMapaDistancia.setText(f.distanciaTexto(ubicacionReferencia));

        txtMapaEstado.setText(f.estadoTexto());
        int colorEstado = getColor(f.abierto ? R.color.sb_verde : R.color.sb_rojo);
        txtMapaEstado.setTextColor(colorEstado);
        txtMapaEstado.setBackgroundResource(
                f.abierto ? R.drawable.bg_badge_abierto : R.drawable.bg_badge_cerrado
        );
        txtMapaEstado.setCompoundDrawableTintList(
                ColorStateList.valueOf(colorEstado)
        );

        if (f.direccion == null || f.direccion.trim().isEmpty()) {
            txtMapaDireccion.setVisibility(View.GONE);
        } else {
            txtMapaDireccion.setText(f.direccion.trim());
            txtMapaDireccion.setVisibility(View.VISIBLE);
        }

        if (!f.logoNombre.isEmpty()) {
            int pad = Math.round(5f * getResources().getDisplayMetrics().density);
            imgMapaFoodtruck.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            imgMapaFoodtruck.setPadding(pad, pad, pad, pad);
            Imagenes.cargar(imgMapaFoodtruck, f.logoNombre, 96, 96);
        } else {
            imgMapaFoodtruck.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imgMapaFoodtruck.setPadding(0, 0, 0, 0);
            imgMapaFoodtruck.setImageResource(f.imagen);
        }

        boolean estabaOculta = cardDetalleMapa.getVisibility() != View.VISIBLE;
        cardDetalleMapa.setVisibility(View.VISIBLE);

        if (estabaOculta && ValueAnimator.areAnimatorsEnabled()) {
            float desplazamiento = 14f * getResources().getDisplayMetrics().density;
            cardDetalleMapa.animate().cancel();
            cardDetalleMapa.setAlpha(0f);
            cardDetalleMapa.setTranslationY(desplazamiento);
            cardDetalleMapa.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(200L)
                    .setInterpolator(Animaciones.CURVA_SUAVE)
                    .withLayer()
                    .start();
        } else {
            cardDetalleMapa.setAlpha(1f);
            cardDetalleMapa.setTranslationY(0f);
        }
    }

    private void ocultarTarjetaFoodtruck() {
        if (cardDetalleMapa == null
                || cardDetalleMapa.getVisibility() != View.VISIBLE) {
            return;
        }

        foodtruckSeleccionado = null;

        if (!ValueAnimator.areAnimatorsEnabled()) {
            cardDetalleMapa.setVisibility(View.GONE);
            return;
        }

        float desplazamiento = 10f * getResources().getDisplayMetrics().density;
        cardDetalleMapa.animate().cancel();
        cardDetalleMapa.animate()
                .alpha(0f)
                .translationY(desplazamiento)
                .setDuration(150L)
                .setInterpolator(Animaciones.CURVA_SUAVE)
                .withEndAction(() -> {
                    cardDetalleMapa.setVisibility(View.GONE);
                    cardDetalleMapa.setAlpha(1f);
                    cardDetalleMapa.setTranslationY(0f);
                })
                .start();
    }

    private void abrirDetalleSeleccionado() {
        if (foodtruckSeleccionado == null) {
            return;
        }

        String clave = foodtruckSeleccionado.id.isEmpty()
                ? foodtruckSeleccionado.nombre
                : foodtruckSeleccionado.id;
        String transicion = "foto_" + clave;
        imgMapaFoodtruck.setTransitionName(transicion);

        Intent intent = foodtruckSeleccionado.crearIntentDetalle(
                this,
                ubicacionReferencia
        );
        intent.putExtra("transicion_foto", transicion);

        ActivityOptionsCompat opciones =
                ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        imgMapaFoodtruck,
                        transicion
                );
        startActivity(intent, opciones.toBundle());
    }

    /**
     * Crea un icono nuevo, ya rasterizado y con tamaño fijo, para cada marcador.
     * Así osmdroid no reutiliza el mismo Drawable entre markers.
     */
    private Drawable crearIconoMarcador() {
        int tamanoPx = Math.round(
                36f * getResources().getDisplayMetrics().density
        );

        Bitmap bitmap = Bitmap.createBitmap(
                tamanoPx, tamanoPx, Bitmap.Config.ARGB_8888
        );

        Canvas canvas = new Canvas(bitmap);
        Drawable base = ContextCompat.getDrawable(this, R.drawable.ic_marker_streetbite);

        if (base != null) {
            base.setBounds(0, 0, tamanoPx, tamanoPx);
            base.draw(canvas);
        }

        BitmapDrawable resultado = new BitmapDrawable(getResources(), bitmap);
        resultado.setBounds(0, 0, tamanoPx, tamanoPx);

        return resultado;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISOS_UBICACION) {
            iniciarUbicacion();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        if (!camaraInicializada) {
            iniciarUbicacion();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }
}
