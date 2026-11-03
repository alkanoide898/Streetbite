package com.chacho.streetbite;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class DetalleFoodtruckActivity extends AppCompatActivity {

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
        TextView btnVolverDetalle = findViewById(R.id.btnVolverDetalle);

        // Recibir datos
        String nombre = getIntent().getStringExtra("nombre");
        String descripcion = getIntent().getStringExtra("descripcion");
        String rating = getIntent().getStringExtra("rating");
        String distancia = getIntent().getStringExtra("distancia");
        String estado = getIntent().getStringExtra("estado");
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

        // Volver
        btnVolverDetalle.setOnClickListener(v -> finish());
    }
}