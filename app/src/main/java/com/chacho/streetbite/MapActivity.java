package com.chacho.streetbite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.List;

public class MapActivity extends AppCompatActivity {

    private static final int PERMISOS_UBICACION = 1001;
    private static final String PREFS_UBICACION = "StreetBiteUbicacion";

    private MapView mapView;
    private MyLocationNewOverlay locationOverlay;
    private GeoPoint puntoUsuario;

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
        añadirMarcadoresFoodtrucks();
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
        locationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(this), mapView);
        locationOverlay.enableMyLocation();
        mapView.getOverlays().add(locationOverlay);

        // 1) PRIORIDAD: Ubicación seleccionada desde Home
        SharedPreferences prefsUbic = getSharedPreferences(PREFS_UBICACION, MODE_PRIVATE);
        boolean tieneGuardada = prefsUbic.contains("lat") && prefsUbic.contains("lon");

        if (tieneGuardada) {
            float latGuardada = prefsUbic.getFloat("lat", -36.8270f);
            float lonGuardada = prefsUbic.getFloat("lon", -73.0500f);
            String nombreGuardado = prefsUbic.getString("nombre", "Concepción");

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

        // Fallback a Chillán si no hay ubicación
        GeoPoint chillan = new GeoPoint(-36.60664, -72.10344);
        mapView.getController().setCenter(chillan);
        mapView.getController().setZoom(15.0);
        Toast.makeText(this, "Mostrando Chillán. Activa tu ubicación para centrarte ahí.", Toast.LENGTH_SHORT).show();
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
            float latGuardada = prefsUbic.getFloat("lat", -36.8270f);
            float lonGuardada = prefsUbic.getFloat("lon", -73.0500f);
            String nombreGuardado = prefsUbic.getString("nombre", "Concepción");
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

    private void añadirMarcadoresFoodtrucks() {
        List<Foodtruck> lista = FoodtruckRepositorio.obtenerTodos();
        Drawable icono = ContextCompat.getDrawable(this, R.drawable.ic_marker_streetbite);

        for (Foodtruck f : lista) {
            Marker marker = new Marker(mapView);
            marker.setPosition(new GeoPoint(f.lat, f.lon));
            marker.setTitle(f.nombre);
            marker.setSubDescription(f.descripcion + " • " + f.ratingTexto() + " • " + f.estadoTexto());
            marker.setIcon(icono);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setOnMarkerClickListener((m, mapView) -> {
                m.showInfoWindow();
                return true;
            });
            mapView.getOverlays().add(marker);
        }

        mapView.invalidate();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISOS_UBICACION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarUbicacion();
            } else {
                Toast.makeText(this, "Sin permiso de ubicación. Usando ubicación seleccionada o Concepción.", Toast.LENGTH_SHORT).show();
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