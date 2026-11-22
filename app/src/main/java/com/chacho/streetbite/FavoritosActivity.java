package com.chacho.streetbite;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityOptionsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pantalla "Mis favoritos" (CRUD completo de public.favoritos en Supabase).
 *
 * - Crear: botón "+ Agregar" (o desde el detalle de cualquier foodtruck).
 * - Leer: listado de favoritos propios con filtro por etiqueta.
 * - Actualizar: editar etiqueta y nota personal de cualquier favorito.
 * - Eliminar: quitar de favoritos con confirmación.
 */
public class FavoritosActivity extends BaseActivity {

    private static final String FILTRO_TODOS = "Todos";

    private TextView txtResumen;
    private LinearLayout contenedorChips;
    private View vistaCargando;
    private View vistaVacia;
    private TextView txtTituloVacio;
    private TextView txtSubtituloVacio;
    private Button btnAgregarVacio;
    private RecyclerView recyclerFavoritos;

    private FavoritoAdapter adaptador;

    private final List<Favorito> favoritos = new ArrayList<>();
    private String etiquetaActiva = FILTRO_TODOS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favoritos);

        txtResumen = findViewById(R.id.txtResumenFavoritos);
        contenedorChips = findViewById(R.id.contenedorChipsEtiquetas);
        vistaCargando = findViewById(R.id.vistaCargandoFavoritos);
        vistaVacia = findViewById(R.id.vistaVaciaFavoritos);
        txtTituloVacio = findViewById(R.id.txtTituloVacioFavoritos);
        txtSubtituloVacio = findViewById(R.id.txtSubtituloVacioFavoritos);
        btnAgregarVacio = findViewById(R.id.btnAgregarFavoritoVacio);
        recyclerFavoritos = findViewById(R.id.recyclerFavoritos);

        TextView btnVolver = findViewById(R.id.btnVolverFavoritos);
        Button btnAgregar = findViewById(R.id.btnAgregarFavorito);

        btnVolver.setOnClickListener(v -> finish());
        btnAgregar.setOnClickListener(v -> abrirDialogoNuevoFavorito());
        btnAgregarVacio.setOnClickListener(v -> abrirDialogoNuevoFavorito());

        adaptador = new FavoritoAdapter(new FavoritoAdapter.AccionesListener() {
            @Override
            public void onAbrirDetalle(
                    FavoritoAdapter.ItemFavorito item,
                    ImageView imgLogo) {
                abrirDetalle(item.foodtruck, imgLogo);
            }

            @Override
            public void onEditar(FavoritoAdapter.ItemFavorito item) {
                abrirDialogoEditarFavorito(item);
            }

            @Override
            public void onEliminar(FavoritoAdapter.ItemFavorito item) {
                confirmarEliminarFavorito(item);
            }
        });

        recyclerFavoritos.setLayoutManager(new LinearLayoutManager(this));
        recyclerFavoritos.setAdapter(adaptador);

        construirChipsFiltro();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cargarCatalogoYFavoritos();
    }

    private void cargarCatalogoYFavoritos() {
        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty() || usuarioId.isEmpty()) {
            Toast.makeText(
                    this,
                    "Inicia sesión para ver tus favoritos.",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }

        mostrarCargando(true);

        // Aseguramos tener el catálogo real de Supabase (con UUIDs válidos)
        // para vincular cada foodtruck_id con su nombre, logo y datos.
        FoodtruckRepositorio.cargar((lista, desdeSupabase) ->
                cargarFavoritosDesdeNube(accessToken, usuarioId)
        );
    }

    private void cargarFavoritosDesdeNube(
            String accessToken,
            String usuarioId) {

        SupabaseHelper.listarFavoritos(
                accessToken,
                usuarioId,
                new SupabaseHelper.FavoritosCallback() {
                    @Override
                    public void onSuccess(List<Favorito> lista) {
                        runOnUiThread(() -> {
                            favoritos.clear();
                            favoritos.addAll(lista);
                            mostrarCargando(false);
                            refrescarVista();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> {
                            mostrarCargando(false);
                            refrescarVista();
                            Toast.makeText(
                                    FavoritosActivity.this,
                                    mensaje,
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }
                }
        );
    }

    private void abrirDialogoNuevoFavorito() {
        Set<String> yaGuardados = new HashSet<>();
        for (Favorito fav : favoritos) {
            yaGuardados.add(fav.foodtruckId);
        }

        List<Foodtruck> disponibles = new ArrayList<>();
        boolean tieneUuidsReales = false;

        for (Foodtruck ft : FoodtruckRepositorio.obtenerTodos()) {
            if (Favorito.esUuidValido(ft.id)) {
                tieneUuidsReales = true;
                if (!yaGuardados.contains(ft.id)) {
                    disponibles.add(ft);
                }
            }
        }

        if (!tieneUuidsReales) {
            Toast.makeText(
                    this,
                    "Conéctate a internet para cargar los locales desde Supabase.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (disponibles.isEmpty()) {
            Toast.makeText(
                    this,
                    "Ya guardaste todos los foodtrucks en tus favoritos.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        DialogoFavorito.mostrarNuevoConSelector(
                this,
                disponibles,
                this::crearFavoritoEnNube
        );
    }

    private void crearFavoritoEnNube(
            String foodtruckId,
            String etiqueta,
            String nota) {

        String accessToken = Sesion.accessToken(this);
        String usuarioId = Sesion.usuarioId(this);

        if (accessToken.isEmpty() || usuarioId.isEmpty()) {
            return;
        }

        SupabaseHelper.crearFavorito(
                accessToken,
                usuarioId,
                foodtruckId,
                etiqueta,
                nota,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito creado) {
                        runOnUiThread(() -> {
                            if (creado != null) {
                                favoritos.add(0, creado);
                                etiquetaActiva = FILTRO_TODOS;
                                construirChipsFiltro();
                                refrescarVista();
                            } else {
                                cargarCatalogoYFavoritos();
                            }
                            Toast.makeText(
                                    FavoritosActivity.this,
                                    "Guardado en tus favoritos.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                FavoritosActivity.this,
                                "No se pudo guardar: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void abrirDialogoEditarFavorito(FavoritoAdapter.ItemFavorito item) {
        String nombreLocal = item.foodtruck != null
                ? item.foodtruck.nombre
                : "Foodtruck guardado";

        DialogoFavorito.mostrarParaLocal(
                this,
                nombreLocal,
                item.favorito.foodtruckId,
                item.favorito,
                (foodtruckId, etiqueta, nota) ->
                        actualizarFavoritoEnNube(item.favorito.id, etiqueta, nota),
                () -> confirmarEliminarFavorito(item)
        );
    }

    private void actualizarFavoritoEnNube(
            String favoritoId,
            String etiqueta,
            String nota) {

        String accessToken = Sesion.accessToken(this);
        if (accessToken.isEmpty()) {
            return;
        }

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
                                reemplazarFavorito(actualizado);
                                refrescarVista();
                            } else {
                                cargarCatalogoYFavoritos();
                            }
                            Toast.makeText(
                                    FavoritosActivity.this,
                                    "Favorito actualizado.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                FavoritosActivity.this,
                                "No se pudo actualizar: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void confirmarEliminarFavorito(FavoritoAdapter.ItemFavorito item) {
        String nombre = item.foodtruck != null
                ? item.foodtruck.nombre
                : "este local";

        AlertDialog confirmacion = new AlertDialog.Builder(this)
                .setTitle("Quitar de favoritos")
                .setMessage("¿Quieres quitar a " + nombre + " de tus favoritos?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        "Quitar",
                        (dialog, which) -> eliminarFavoritoEnNube(item.favorito.id)
                )
                .create();

        confirmacion.show();
        sonarBotonesDelDialogo(confirmacion);
    }

    private void eliminarFavoritoEnNube(String favoritoId) {
        String accessToken = Sesion.accessToken(this);
        if (accessToken.isEmpty()) {
            return;
        }

        SupabaseHelper.eliminarFavorito(
                accessToken,
                favoritoId,
                new SupabaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(JSONObject respuesta) {
                        runOnUiThread(() -> {
                            quitarFavoritoPorId(favoritoId);
                            refrescarVista();
                            Toast.makeText(
                                    FavoritosActivity.this,
                                    "Eliminado de tus favoritos.",
                                    Toast.LENGTH_SHORT
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        runOnUiThread(() -> Toast.makeText(
                                FavoritosActivity.this,
                                "No se pudo eliminar: " + mensaje,
                                Toast.LENGTH_LONG
                        ).show());
                    }
                }
        );
    }

    private void abrirDetalle(Foodtruck foodtruck, ImageView imgLogo) {
        if (foodtruck == null) {
            Toast.makeText(
                    this,
                    "No se encontró el detalle de este local.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        String transicion = "foto_" + foodtruck.id;
        imgLogo.setTransitionName(transicion);

        Intent intent = foodtruck.crearIntentDetalle(this, null);
        intent.putExtra("transicion_foto", transicion);

        ActivityOptionsCompat opciones =
                ActivityOptionsCompat.makeSceneTransitionAnimation(
                        this,
                        imgLogo,
                        transicion
                );
        startActivity(intent, opciones.toBundle());
    }

    private void construirChipsFiltro() {
        contenedorChips.removeAllViews();

        List<String> opciones = new ArrayList<>();
        opciones.add(FILTRO_TODOS);
        for (String etiqueta : Favorito.ETIQUETAS) {
            opciones.add(etiqueta);
        }

        float densidad = getResources().getDisplayMetrics().density;
        int padH = Math.round(14 * densidad);
        int padV = Math.round(7 * densidad);
        int margen = Math.round(8 * densidad);

        for (String opcion : opciones) {
            boolean activo = opcion.equals(etiquetaActiva);

            TextView chip = new TextView(this);
            chip.setText(opcion);
            chip.setTextSize(13f);
            chip.setPadding(padH, padV, padH, padV);
            chip.setBackgroundResource(R.drawable.bg_sb_chip);
            chip.setSelected(activo);
            chip.setTextColor(getColor(
                    activo ? R.color.sb_texto_sobre_acento : R.color.sb_marron
            ));
            chip.setTypeface(
                    null,
                    activo ? android.graphics.Typeface.BOLD
                            : android.graphics.Typeface.NORMAL
            );

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(margen);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                etiquetaActiva = opcion;
                construirChipsFiltro();
                refrescarVista();
            });

            contenedorChips.addView(chip);
        }
    }

    private void refrescarVista() {
        int total = favoritos.size();
        if (total == 0) {
            txtResumen.setText("Tus foodtrucks guardados con notas personales");
        } else if (total == 1) {
            txtResumen.setText("Tienes 1 foodtruck guardado");
        } else {
            txtResumen.setText("Tienes " + total + " foodtrucks guardados");
        }

        List<FavoritoAdapter.ItemFavorito> visibles = new ArrayList<>();

        for (Favorito fav : favoritos) {
            if (!FILTRO_TODOS.equals(etiquetaActiva)
                    && !fav.etiqueta.equalsIgnoreCase(etiquetaActiva)) {
                continue;
            }
            Foodtruck ft = FoodtruckRepositorio.buscarPorId(fav.foodtruckId);
            visibles.add(new FavoritoAdapter.ItemFavorito(fav, ft));
        }

        adaptador.actualizar(visibles);

        if (visibles.isEmpty()) {
            recyclerFavoritos.setVisibility(View.GONE);
            vistaVacia.setVisibility(View.VISIBLE);

            if (favoritos.isEmpty()) {
                txtTituloVacio.setText("Todavía no tienes favoritos");
                txtSubtituloVacio.setText(
                        "Guarda tus foodtrucks preferidos de San Carlos con una etiqueta y tus notas personales."
                );
                btnAgregarVacio.setVisibility(View.VISIBLE);
            } else {
                txtTituloVacio.setText(
                        "Sin favoritos en \"" + etiquetaActiva + "\""
                );
                txtSubtituloVacio.setText(
                        "Prueba seleccionando otra etiqueta o guarda un nuevo local."
                );
                btnAgregarVacio.setVisibility(View.GONE);
            }
        } else {
            vistaVacia.setVisibility(View.GONE);
            recyclerFavoritos.setVisibility(View.VISIBLE);
        }
    }

    private void mostrarCargando(boolean cargando) {
        vistaCargando.setVisibility(cargando ? View.VISIBLE : View.GONE);
        if (cargando) {
            vistaVacia.setVisibility(View.GONE);
            recyclerFavoritos.setVisibility(View.GONE);
        }
    }

    private void reemplazarFavorito(Favorito actualizado) {
        for (int i = 0; i < favoritos.size(); i++) {
            if (favoritos.get(i).id.equals(actualizado.id)) {
                favoritos.set(i, actualizado);
                return;
            }
        }
        favoritos.add(0, actualizado);
    }

    private void quitarFavoritoPorId(String favoritoId) {
        for (int i = 0; i < favoritos.size(); i++) {
            if (favoritos.get(i).id.equals(favoritoId)) {
                favoritos.remove(i);
                return;
            }
        }
    }
}
