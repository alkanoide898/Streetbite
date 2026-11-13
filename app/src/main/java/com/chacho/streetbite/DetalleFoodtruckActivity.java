package com.chacho.streetbite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


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

        imgDetalle.setImageResource(imagen);

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