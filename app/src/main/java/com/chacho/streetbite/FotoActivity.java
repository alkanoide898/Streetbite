package com.chacho.streetbite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

/**
 * Visor de una foto del menú a pantalla completa.
 * Permite pellizcar para acercar y arrastrar para moverse.
 */
public class FotoActivity extends BaseActivity {

    public static Intent crearIntent(
            android.content.Context contexto,
            String foto,
            String nombreLocal) {

        Intent intent = new Intent(contexto, FotoActivity.class);
        intent.putExtra("foto", foto);
        intent.putExtra("nombre", nombreLocal);
        return intent;
    }

    /** El visor entra desde la foto del menú con transición compartida. */
    @Override
    protected void animarEntrada() {
        // Sin animación de entrada genérica.
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_foto);

        ImageView imagen = findViewById(R.id.imgFoto);
        View cerrar = findViewById(R.id.btnCerrarFoto);

        String foto = getIntent().getStringExtra("foto");
        String nombreLocal = getIntent().getStringExtra("nombre");

        if (nombreLocal != null && !nombreLocal.trim().isEmpty()) {
            imagen.setContentDescription(
                    "Foto del menú de " + nombreLocal
            );
        }

        int ancho = getResources().getDisplayMetrics().widthPixels;
        int alto = getResources().getDisplayMetrics().heightPixels;

        Imagenes.cargarPx(imagen, foto, ancho, alto);

        cerrar.setOnClickListener(v -> finish());
    }
}
