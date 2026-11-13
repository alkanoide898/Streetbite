package com.chacho.streetbite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

public class MapActivity extends BaseActivity {

    private static final int PERMISOS_UBICACION = 1001;
    private static final String PREFS_UBICACION = "StreetBiteUbicacion";

    // San Carlos, Ñuble. Centro por defecto cuando no hay GPS ni ubicación guardada.
    private static final double SAN_CARLOS_LAT = -36.4248;
    private static final double SAN_CARLOS_LON = -71.9581;
    private static final String CIUDAD_POR_DEFECTO = "San Carlos";

    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private GeoPoint puntoUsuario;

    private final List<Marker> marcadores = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Configuración obligatoria para osmdroid
        Configuration.getInstance().load(getApplicationContext(),
                getSharedPreferences("osmdroid", MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_map);

        // Vistas
        mapView = findViewById(R.id.mapView);
        TextView btnVolverMapa = findViewById(R.id.btnVolverMapa);
        CardView btnMiUbicacion = findViewById(R.id.btnMiUbicacion);

        // Configuración básica del mapa
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(15.0);

        // Volver
        btnVolverMapa.setOnClickListener(v -> finish());

        // Centrar en mi ubicación
        btnMiUbicacion.setOnClickListener(v -> centrarEnMiUbicacion());

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
        if (locationOverlay == null) {
            locationOverlay = new MyLocationNewOverlay(
                    new GpsMyLocationProvider(this),
                    mapView
            );
            mapView.getOverlays().add(locationOverlay);
        }

        locationOverlay.enableMyLocation();

        // 1) PRIORIDAD: Ubicación seleccionada desde Home
        SharedPreferences prefsUbic = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        boolean tieneGuardada = prefsUbic.contains("lat") && prefsUbic.contains("lon");

        if (tieneGuardada) {
            float latGuardada = prefsUbic.getFloat("lat", (float) SAN_CARLOS_LAT);
            float lonGuardada = prefsUbic.getFloat("lon", (float) SAN_CARLOS_LON);
            String nombreGuardado = prefsUbic.getString("nombre", CIUDAD_POR_DEFECTO);

            GeoPoint puntoGuardado = new GeoPoint((double) latGuardada, (double) lonGuardada);
            mapView.getController().setCenter(puntoGuardado);
            mapView.getController().setZoom(15.2);
            Toast.makeText(this, "Mostrando " + nombreGuardado, Toast.LENGTH_SHORT).show();
            return;
        }

        // 2) SI NO HAY SELECCIONADA: usar última ubicación GPS
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm != null) {
            Location mejor = obtenerUltimaUbicacion(lm);
            if (mejor != null) {
                puntoUsuario = new GeoPoint(mejor.getLatitude(), mejor.getLongitude());
                mapView.getController().setCenter(puntoUsuario);
                mapView.getController().setZoom(15.5);
                Toast.makeText(this, "Centrado en tu ubicación actual", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Fallback a San Carlos si no hay ubicación
        GeoPoint sanCarlos = new GeoPoint(SAN_CARLOS_LAT, SAN_CARLOS_LON);
        mapView.getController().setCenter(sanCarlos);
        mapView.getController().setZoom(15.0);
        Toast.makeText(this, "Mostrando San Carlos. Activa tu ubicación para centrarte ahí.", Toast.LENGTH_SHORT).show();
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
        SharedPreferences prefsUbic = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        boolean tieneGuardada = prefsUbic.contains("lat") && prefsUbic.contains("lon");

        if (tieneGuardada) {
            float latGuardada = prefsUbic.getFloat("lat", (float) SAN_CARLOS_LAT);
            float lonGuardada = prefsUbic.getFloat("lon", (float) SAN_CARLOS_LON);
            String nombreGuardado = prefsUbic.getString("nombre", CIUDAD_POR_DEFECTO);
            GeoPoint puntoGuardado = new GeoPoint((double) latGuardada, (double) lonGuardada);
            mapView.getController().animateTo(puntoGuardado);
            mapView.getController().setZoom(15.8);
            Toast.makeText(this, "Centrado en " + nombreGuardado, Toast.LENGTH_SHORT).show();
            return;
        }

        if (locationOverlay != null && locationOverlay.getMyLocation() != null) {
            mapView.getController().animateTo(locationOverlay.getMyLocation());
            mapView.getController().setZoom(16.0);
            Toast.makeText(this, "Centrado en tu ubicación GPS", Toast.LENGTH_SHORT).show();
            return;
        }

        if (puntoUsuario != null) {
            mapView.getController().animateTo(puntoUsuario);
            mapView.getController().setZoom(16.0);
            return;
        }

        if (tienePermisosUbicacion()) {
            iniciarUbicacion();
        } else {
            solicitarPermisosUbicacion();
        }
    }

    private void cargarFoodtrucks() {
        FoodtruckRepositorio.cargar((foodtrucks, desdeSupabase) ->
                runOnUiThread(this::añadirMarcadoresFoodtrucks)
        );
    }

    private void añadirMarcadoresFoodtrucks() {
        // Quita solo los marcadores anteriores, no la capa de ubicación.
        mapView.getOverlays().removeAll(marcadores);
        marcadores.clear();

        List<Foodtruck> lista = FoodtruckRepositorio.obtenerTodos();

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        // OJO: cada Marker necesita su PROPIO Drawable. Si se comparte la misma
        // instancia, osmdroid le cambia los bounds a todos a la vez y termina
        // dibujando un solo marcador gigante.
        for (Foodtruck f : lista) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(f.lat, f.lon));
            marker.setTitle(f.nombre);
            marker.setSubDescription(f.descripcion + " • " + f.ratingTexto() + " • " + f.estadoTexto());
            marker.setIcon(crearIconoMarcador());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
            marker.setOnMarkerClickListener((m, mapView) -> {
                m.showInfoWindow();
                mapView.getController().animateTo(m.getPosition());
                return true;
            });
            mapView.getOverlays().add(marker);
            marcadores.add(marker);

            minLat = Math.min(minLat, f.lat);
            maxLat = Math.max(maxLat, f.lat);
            minLon = Math.min(minLon, f.lon);
            maxLon = Math.max(maxLon, f.lon);
        }

        // Encuadra todos los locales para que ninguno quede fuera de pantalla.
        if (!marcadores.isEmpty()) {
            int margenPx = Math.round(56f * getResources().getDisplayMetrics().density);
            final BoundingBox encuadre = new BoundingBox(maxLat, maxLon, minLat, minLon);

            // Se hace en un post para que el MapView ya tenga tamaño medido.
            mapView.post(() -> {
                mapView.zoomToBoundingBox(encuadre, false, margenPx);
                mapView.invalidate();
            });
        }

        mapView.invalidate();
    }

    /**
     * Crea un icono nuevo, ya rasterizado y con tamaño fijo, para cada marcador.
     * Así osmdroid no reutiliza el mismo Drawable entre markers.
     */
    private Drawable crearIconoMarcador() {
        int tamanoPx = Math.round(
                40f * getResources().getDisplayMetrics().density
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarUbicacion();
            } else {
                Toast.makeText(this, "Sin permiso de ubicación. Usando la ubicación seleccionada o San Carlos.", Toast.LENGTH_SHORT).show();
                iniciarUbicacion();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        // Al volver desde Home se vuelve a centrar con la ubicación actual seleccionada
        iniciarUbicacion();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
    }
}