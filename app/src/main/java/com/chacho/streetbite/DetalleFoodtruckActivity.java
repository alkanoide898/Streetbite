package com.chacho.streetbite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;


public class DetalleFoodtruckActivity extends BaseActivity {

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
        TextView txtMenuSubtitulo = findViewById(R.id.txtMenuSubtitulo);
        TextView txtMenuVacio = findViewById(R.id.txtMenuVacio);
        RecyclerView recyclerMenu = findViewById(R.id.recyclerMenu);

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
        configurarMenu(recyclerMenu, txtMenuSubtitulo, txtMenuVacio, menu, nombre);

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

    private void configurarMenu(RecyclerView recycler,
                                TextView subtitulo,
                                TextView vacio,
                                String[] menu,
                                String nombreLocal) {

        List<String> fotos = new ArrayList<>();

        if (menu != null) {
            for (String foto : menu) {
                if (foto != null && !foto.trim().isEmpty()) {
                    fotos.add(foto.trim());
                }
            }
        }

        if (fotos.isEmpty()) {
            recycler.setVisibility(View.GONE);
            subtitulo.setVisibility(View.GONE);
            vacio.setVisibility(View.VISIBLE);
            return;
        }

        subtitulo.setText(
                fotos.size() == 1
                        ? "1 foto de los platos"
                        : fotos.size() + " fotos de los platos"
        );

        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setAdapter(
                new MenuAdapter(
                        fotos,
                        foto -> startActivity(
                                FotoActivity.crearIntent(
                                        this,
                                        foto,
                                        nombreLocal
                                )
                        )
                )
        );
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