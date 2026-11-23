package com.chacho.streetbite;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class DetalleFoodtruckActivity extends BaseActivity {

    private String foodtruckId;
    private String nombreLocal;
    private Favorito favoritoActual;

    private TextView btnFavoritoDetalle;
    private TextView btnEditarFavoritoDetalle;
    private View bloqueFavoritoDetalle;
    private TextView txtEtiquetaFavoritoDetalle;
    private TextView txtCompartidoFavoritoDetalle;
    private TextView txtNotaFavoritoDetalle;

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
        btnFavoritoDetalle = findViewById(R.id.btnFavoritoDetalle);
        bloqueFavoritoDetalle = findViewById(R.id.bloqueFavoritoDetalle);
        txtEtiquetaFavoritoDetalle = findViewById(R.id.txtEtiquetaFavoritoDetalle);
        txtCompartidoFavoritoDetalle = findViewById(R.id.txtCompartidoFavoritoDetalle);
        txtNotaFavoritoDetalle = findViewById(R.id.txtNotaFavoritoDetalle);
        btnEditarFavoritoDetalle = findViewById(R.id.btnEditarFavoritoDetalle);
        TextView txtMenuVacio = findViewById(R.id.txtMenuVacio);
        LinearLayout contenedorMenu = findViewById(R.id.contenedorMenu);

        // La foto viene desde la tarjeta con una transición compartida.
        String transicionFoto = getIntent().getStringExtra("transicion_foto");

        if (transicionFoto != null && !transicionFoto.isEmpty()) {
            imgDetalle.setTransitionName(transicionFoto);
        }

        // Recibir datos
        foodtruckId = getIntent().getStringExtra("id");
        String nombre = getIntent().getStringExtra("nombre");
        nombreLocal = nombre;
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
        double lat = getIntent().getDoubleExtra("lat", 0.0);
        double lon = getIntent().getDoubleExtra("lon", 0.0);

        Button btnComoLlegar = findViewById(R.id.btnComoLlegarDetalle);
        if (lat == 0.0 && lon == 0.0) {
            btnComoLlegar.setVisibility(View.GONE);
        } else {
            btnComoLlegar.setOnClickListener(v ->
                    Foodtruck.abrirComoLlegar(this, lat, lon, nombreLocal)
            );
        }

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
            boolean cerrado = estado.toLowerCase().contains("cerrado");
            int colorEstado = getColor(
                    cerrado ? R.color.sb_rojo : R.color.sb_verde
            );
            txtEstadoDetalle.setTextColor(colorEstado);
            txtEstadoDetalle.setBackgroundResource(
                    cerrado
                            ? R.drawable.bg_badge_cerrado
                            : R.drawable.bg_badge_abierto
            );
            txtEstadoDetalle.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(colorEstado)
            );
        }

        mostrarLogo(imgDetalle, logo, imagen);
        configurarMenu(contenedorMenu, txtMenuVacio, menu, nombre);

        if (direccion != null && !direccion.trim().isEmpty()) {
            txtDireccionDetalle.setText(direccion.trim());
            txtDireccionDetalle.setVisibility(View.VISIBLE);
        }

        if (horario != null && !horario.trim().isEmpty()) {
            txtHorarioDetalle.setText(horario.trim());
            txtHorarioDetalle.setVisibility(View.VISIBLE);
        }

        if (telefono != null && !telefono.trim().isEmpty()) {

            txtTelefonoDetalle.setText(telefono.trim());
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

            txtInstagramDetalle.setText("@" + usuario);
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

        // Favorito (Crear / Leer / Actualizar / Eliminar)
        btnFavoritoDetalle.setOnClickListener(v -> abrirDialogoFavorito());
        btnEditarFavoritoDetalle.setOnClickListener(v -> abrirDialogoFavorito());
        cargarEstadoFavorito();
    }

    private void cargarEstadoFavorito() {
        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty()
                || usuarioId.isEmpty()
                || !Favorito.esUuidValido(foodtruckId)) {
            actualizarVistaFavorito();
            return;
        }

        SupabaseHelper.obtenerFavorito(
                accessToken,
                usuarioId,
                foodtruckId,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito favorito) {
                        runOnUiThread(() -> {
                            favoritoActual = favorito;
                            actualizarVistaFavorito();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        // Si falla o la tabla aún no existe, se deja el botón listo.
                    }
                }
        );
    }

    private void abrirDialogoFavorito() {
        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty() || usuarioId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Inicia sesión para guardar favoritos.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!Favorito.esUuidValido(foodtruckId)) {
            Toast.makeText(
                    this,
                    "Conéctate a internet para sincronizar con Supabase antes de guardar.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (favoritoActual != null && !favoritoActual.esDueno(usuarioId)) {
            confirmarDejarDeRecibirFavorito(accessToken);
            return;
        }

        DialogoFavorito.mostrarParaLocal(
                this,
                nombreLocal,
                foodtruckId,
                favoritoActual,
                (idLocal, etiqueta, nota) -> {
                    if (favoritoActual == null) {
                        crearFavorito(accessToken, usuarioId, idLocal, etiqueta, nota);
                    } else {
                        actualizarFavorito(accessToken, favoritoActual.id, etiqueta, nota);
                    }
                },
                () -> confirmarQuitarFavorito(accessToken)
        );
    }

    private void crearFavorito(
            String accessToken,
            String usuarioId,
            String idLocal,
            String etiqueta,
            String nota) {

        SupabaseHelper.crearFavorito(
                accessToken,
                usuarioId,
                idLocal,
                etiqueta,
                nota,
                Sesion.nombre(this),
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito creado) {
                        runOnUiThread(() -> {
                            favoritoActual = creado;
                            actualizarVistaFavorito();
                            Toast.makeText(
                                    DetalleFoodtruckActivity.this,
                                    "Guardado en tus favoritos.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                DetalleFoodtruckActivity.this,
                                "No se pudo guardar: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void actualizarFavorito(
            String accessToken,
            String favoritoId,
            String etiqueta,
            String nota) {

        SupabaseHelper.actualizarFavorito(
                accessToken,
                favoritoId,
                etiqueta,
                nota,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito actualizado) {
                        runOnUiThread(() -> {
                            if (actualizado != null) {
                                favoritoActual = actualizado;
                            }
                            actualizarVistaFavorito();
                            Toast.makeText(
                                    DetalleFoodtruckActivity.this,
                                    "Favorito actualizado.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                DetalleFoodtruckActivity.this,
                                "No se pudo actualizar: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void confirmarQuitarFavorito(String accessToken) {
        if (favoritoActual == null) {
            return;
        }

        String local = nombreLocal == null || nombreLocal.trim().isEmpty()
                ? "este local"
                : nombreLocal.trim();

        AlertDialog confirmacion = new AlertDialog.Builder(this)
                .setTitle("Quitar de favoritos")
                .setMessage("¿Quieres quitar a " + local + " de tus favoritos?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Quitar",
                        (d, w) -> eliminarFavorito(accessToken, favoritoActual.id)
                )
                .create();
        confirmacion.show();
        sonarBotonesDelDialogo(confirmacion);
    }

    private void eliminarFavorito(String accessToken, String favoritoId) {
        SupabaseHelper.eliminarFavorito(
                accessToken,
                favoritoId,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        runOnUiThread(() -> {
                            favoritoActual = null;
                            actualizarVistaFavorito();
                            Toast.makeText(
                                    DetalleFoodtruckActivity.this,
                                    "Eliminado de tus favoritos.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                DetalleFoodtruckActivity.this,
                                "No se pudo eliminar: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void confirmarDejarDeRecibirFavorito(String accessToken) {
        if (favoritoActual == null) {
            return;
        }

        String local = nombreLocal == null || nombreLocal.trim().isEmpty()
                ? "este local"
                : nombreLocal.trim();
        String companero = favoritoActual.nombreCompanero(Sesion.usuarioId(this));

        AlertDialog confirmacion = new AlertDialog.Builder(this)
                .setTitle("Favorito compartido")
                .setMessage(
                        "Este favorito fue compartido contigo por " + companero
                                + ". Solo el propietario puede editarlo. "
                                + "¿Quieres dejar de recibir a " + local
                                + " sin eliminar el favorito original?"
                )
                .setNegativeButton("Cerrar", null)
                .setPositiveButton(
                        "Dejar de recibir",
                        (d, w) -> dejarDeRecibirFavorito(accessToken, favoritoActual.id)
                )
                .create();
        confirmacion.show();
        sonarBotonesDelDialogo(confirmacion);
    }

    private void dejarDeRecibirFavorito(String accessToken, String favoritoId) {
        SupabaseHelper.dejarDeCompartirFavorito(
                accessToken,
                favoritoId,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito actualizado) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            favoritoActual = null;
                            actualizarVistaFavorito();
                            Toast.makeText(
                                    DetalleFoodtruckActivity.this,
                                    "Dejaste de recibir este favorito.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed()) {
                                return;
                            }
                            Toast.makeText(
                                    DetalleFoodtruckActivity.this,
                                    "No se pudo actualizar: " + mensaje,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void actualizarVistaFavorito() {
        if (favoritoActual == null) {
            btnFavoritoDetalle.setText("Guardar");
            btnFavoritoDetalle.setTextColor(0xFFFFFFFF);
            btnFavoritoDetalle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    R.drawable.ic_heart_outline, 0, 0, 0
            );
            btnFavoritoDetalle.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(0xFFFFFFFF)
            );
            bloqueFavoritoDetalle.setVisibility(View.GONE);
            return;
        }

        boolean esDueno = favoritoActual.esDueno(Sesion.usuarioId(this));
        int colorGuardado = getColor(R.color.sb_ambar);
        btnFavoritoDetalle.setText(esDueno ? "Guardado" : "Compartido");
        btnFavoritoDetalle.setTextColor(colorGuardado);
        btnFavoritoDetalle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_heart_filled, 0, 0, 0
        );
        btnFavoritoDetalle.setCompoundDrawableTintList(
                android.content.res.ColorStateList.valueOf(colorGuardado)
        );
        bloqueFavoritoDetalle.setVisibility(View.VISIBLE);
        btnEditarFavoritoDetalle.setVisibility(esDueno ? View.VISIBLE : View.GONE);
        txtEtiquetaFavoritoDetalle.setText(
                (esDueno ? "En tus favoritos · " : "Favorito compartido · ")
                        + favoritoActual.etiqueta
        );

        if (favoritoActual.estaCompartido()) {
            String companero = favoritoActual.nombreCompanero(
                    Sesion.usuarioId(this)
            );
            txtCompartidoFavoritoDetalle.setText(
                    "En conjunto con " + companero
            );
            txtCompartidoFavoritoDetalle.setVisibility(View.VISIBLE);
        } else {
            txtCompartidoFavoritoDetalle.setVisibility(View.GONE);
        }

        if (favoritoActual.nota.isEmpty()) {
            txtNotaFavoritoDetalle.setVisibility(View.GONE);
        } else {
            txtNotaFavoritoDetalle.setText(favoritoActual.nota);
            txtNotaFavoritoDetalle.setVisibility(View.VISIBLE);
        }
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