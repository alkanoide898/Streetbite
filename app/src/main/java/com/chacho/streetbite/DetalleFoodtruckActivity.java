package com.chacho.streetbite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class DetalleFoodtruckActivity extends BaseActivity {

    /** La foto entra con una transición compartida desde la tarjeta. */
    @Override
    protected void animarEntrada() {
        // Sin animación de entrada genérica.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle_foodtruck);

        // Vistas
        ImageView imgDetalle = findViewById(R.id.imgDetalle);
        TextView txtNombreDetalle = findViewById(R.id.txtNombreDetalle);
        TextView txtDescripcionDetalle = findViewById(R.id.txtDescripcionDetalle);
        TextView txtRatingDetalle = findViewById(R.id.txtRatingDetalle);
        TextView txtDistanciaDetalle = findViewById(R.id.txtDistanciaDetalle);
        TextView txtEstadoDetalle = findViewById(R.id.txtEstadoDetalle);
        TextView txtDireccionDetalle = findViewById(R.id.txtDireccionDetalle);
        TextView txtHorarioDetalle = findViewById(R.id.txtHorarioDetalle);
        TextView txtTelefonoDetalle = findViewById(R.id.txtTelefonoDetalle);
        TextView txtInstagramDetalle = findViewById(R.id.txtInstagramDetalle);
        TextView btnVolverDetalle = findViewById(R.id.btnVolverDetalle);
        TextView txtMenuVacio = findViewById(R.id.txtMenuVacio);
        LinearLayout contenedorMenu = findViewById(R.id.contenedorMenu);

        // La foto viene desde la tarjeta con una transición compartida.
        String transicionFoto = getIntent().getStringExtra("transicion_foto");

        if (transicionFoto != null && !transicionFoto.isEmpty()) {
            imgDetalle.setTransitionName(transicionFoto);
        }

        // Recibir datos
        String nombre = getIntent().getStringExtra("nombre");
        String descripcion = getIntent().getStringExtra("descripcion");
        String rating = getIntent().getStringExtra("rating");
        String distancia = getIntent().getStringExtra("distancia");
        String estado = getIntent().getStringExtra("estado");
        String direccion = getIntent().getStringExtra("direccion");
        String horario = getIntent().getStringExtra("horario");
        String telefono = getIntent().getStringExtra("telefono");
        String instagram = getIntent().getStringExtra("instagram");
        String logo = getIntent().getStringExtra("logo");
        String[] menu = getIntent().getStringArrayExtra("menu");
        int imagen = getIntent().getIntExtra("imagen", R.drawable.tacos);

        // Asignar datos
        if (nombre != null) {
            txtNombreDetalle.setText(nombre);
        }
        if (descripcion != null) {
            txtDescripcionDetalle.setText(descripcion);
        }
        if (rating != null) {
            txtRatingDetalle.setText(rating);
        }
        if (distancia != null) {
            txtDistanciaDetalle.setText(distancia);
        }
        if (estado != null) {
            txtEstadoDetalle.setText(estado);
            // Colorear estado según abierto/cerrado
            if (estado.toLowerCase().contains("cerrado")) {
                txtEstadoDetalle.setTextColor(0xFFD32F2F);
            } else {
                txtEstadoDetalle.setTextColor(0xFF4CAF50);
            }
        }

        mostrarLogo(imgDetalle, logo, imagen);
        configurarMenu(contenedorMenu, txtMenuVacio, menu, nombre);

        if (direccion != null && !direccion.trim().isEmpty()) {
            txtDireccionDetalle.setText("📍 " + direccion);
            txtDireccionDetalle.setVisibility(View.VISIBLE);
        }

        if (horario != null && !horario.trim().isEmpty()) {
            txtHorarioDetalle.setText("🕒 " + horario);
            txtHorarioDetalle.setVisibility(View.VISIBLE);
        }

        if (telefono != null && !telefono.trim().isEmpty()) {

            txtTelefonoDetalle.setText("📱 " + telefono);
            txtTelefonoDetalle.setVisibility(View.VISIBLE);

            String digitos = telefono.replaceAll("[^0-9]", "");

            if (!digitos.isEmpty()) {
                txtTelefonoDetalle.setOnClickListener(v ->
                        abrirUrl("https://wa.me/" + digitos)
                );
            }
        }

        if (instagram != null && !instagram.trim().isEmpty()) {

            String usuario = instagram.trim();

            if (usuario.startsWith("@")) {
                usuario = usuario.substring(1);
            }

            txtInstagramDetalle.setText("📷 @" + usuario);
            txtInstagramDetalle.setVisibility(View.VISIBLE);

            if (!usuario.isEmpty()) {

                final String usuarioFinal = usuario;

                txtInstagramDetalle.setOnClickListener(v ->
                        abrirUrl("https://instagram.com/" + usuarioFinal)
                );
            }
        }

        // Volver
        btnVolverDetalle.setOnClickListener(v -> finish());
    }

    /**
     * El logo se carga de forma inmediata para que la transición compartida
     * desde la tarjeta no muestre la ilustración de respaldo.
     */
    private void mostrarLogo(ImageView vista, String logo, int respaldo) {

        if (logo != null && !logo.trim().isEmpty()) {
            vista.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Imagenes.cargarAhora(vista, logo, 200, 200);
            return;
        }

        vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
        vista.setPadding(0, 0, 0, 0);
        vista.setImageResource(respaldo);
    }

    private void configurarMenu(LinearLayout contenedor,
                                TextView vacio,
                                String[] menu,
                                String nombreLocal) {

        contenedor.removeAllViews();

        List<String> fotos = new ArrayList<>();

        if (menu != null) {
            for (String foto : menu) {
                if (foto != null && !foto.trim().isEmpty()) {
                    fotos.add(foto.trim());
                }
            }
        }

        if (fotos.isEmpty()) {
            contenedor.setVisibility(View.GONE);
            vacio.setVisibility(View.VISIBLE);
            return;
        }

        contenedor.setVisibility(View.VISIBLE);
        vacio.setVisibility(View.GONE);

        String local = nombreLocal == null || nombreLocal.trim().isEmpty()
                ? "este local"
                : nombreLocal.trim();

        float densidad = getResources().getDisplayMetrics().density;
        int margen = Math.round(6f * densidad);

        // Grilla simple de dos columnas: al no ser una lista que se recicla,
        // el ScrollView del detalle puede desplazarse hasta el final.
        for (int i = 0; i < fotos.size(); i += 2) {

            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            for (int columna = 0; columna < 2; columna++) {

                int indice = i + columna;

                if (indice >= fotos.size()) {
                    // Relleno para que la última foto no se estire sola.
                    View hueco = new View(this);
                    hueco.setLayoutParams(
                            new LinearLayout.LayoutParams(0, 1, 1f)
                    );
                    fila.addView(hueco);
                    continue;
                }

                final String foto = fotos.get(indice);

                View celda = getLayoutInflater().inflate(
                        R.layout.item_menu_foto,
                        fila,
                        false
                );

                LinearLayout.LayoutParams parametros =
                        new LinearLayout.LayoutParams(
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f
                        );
                parametros.setMargins(margen, margen, margen, margen);
                celda.setLayoutParams(parametros);

                ImageView imagen = celda.findViewById(R.id.imgMenuFoto);

                imagen.setContentDescription(
                        "Foto " + (indice + 1) + " del menú de " + local
                );

                Imagenes.cargar(imagen, foto, 180, 190);

                celda.setOnClickListener(v -> startActivity(
                        FotoActivity.crearIntent(this, foto, local)
                ));

                fila.addView(celda);
            }

            contenedor.addView(fila);
        }
    }

    private void abrirUrl(String url) {

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "No se pudo abrir el enlace",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }
}