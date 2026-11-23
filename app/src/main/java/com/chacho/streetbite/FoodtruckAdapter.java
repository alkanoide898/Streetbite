package com.chacho.streetbite;

import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adaptador compartido por la lista vertical y la fila de destacados.
 * El layout de cada ítem decide qué vistas existen.
 */
public class FoodtruckAdapter
        extends RecyclerView.Adapter<FoodtruckAdapter.FoodtruckViewHolder> {

    public interface OnFoodtruckClick {
        void alHacerClick(Foodtruck foodtruck, View foto);
    }

    public interface OnCompartirClick {
        void alCompartir(Foodtruck foodtruck);
    }

    private static final float ESCALA_PRESIONADO = 0.98f;

    @LayoutRes
    private final int layoutRes;

    private final OnFoodtruckClick listener;
    private OnCompartirClick compartirListener;

    private List<Foodtruck> datos = new ArrayList<>();

    private Location ubicacion;

    public FoodtruckAdapter(@LayoutRes int layoutRes,
                            OnFoodtruckClick listener) {
        this.layoutRes = layoutRes;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setOnCompartirClick(OnCompartirClick compartirListener) {
        this.compartirListener = compartirListener;
    }

    /**
     * Reemplaza los datos y notifica de inmediato para que el RecyclerView
     * dentro de NestedScrollView siempre recalcule la altura exacta sin
     * ocultar ni recortar tarjetas al cambiar de categoría.
     */
    public void actualizar(List<Foodtruck> foodtrucks, Location ubicacion) {
        datos = foodtrucks == null
                ? new ArrayList<>()
                : new ArrayList<>(foodtrucks);
        this.ubicacion = ubicacion;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return clave(datos.get(position)).hashCode() & 0xffffffffL;
    }

    @NonNull
    @Override
    public FoodtruckViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View vista = LayoutInflater.from(parent.getContext())
                .inflate(layoutRes, parent, false);

        return new FoodtruckViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(
            @NonNull FoodtruckViewHolder holder,
            int position) {

        Foodtruck foodtruck = datos.get(position);

        mostrarLogo(holder, foodtruck);

        // Nombre único para que la foto viaje de la tarjeta al detalle.
        holder.imagen.setTransitionName("foto_" + clave(foodtruck));

        holder.nombre.setText(foodtruck.nombre);
        holder.rating.setText(foodtruck.ratingTexto());

        if (holder.descripcion != null) {
            holder.descripcion.setText(foodtruck.descripcion);
        }

        if (holder.categorias != null) {
            String resumenCategorias = formatearCategorias(foodtruck);
            if (resumenCategorias.isEmpty()) {
                holder.categorias.setVisibility(View.GONE);
            } else {
                holder.categorias.setText(resumenCategorias);
                holder.categorias.setVisibility(View.VISIBLE);
            }
        }

        if (holder.distancia != null) {
            holder.distancia.setText(foodtruck.distanciaTexto(ubicacion));
        }

        if (holder.estado != null) {
            holder.estado.setText(foodtruck.estadoTexto());
            int colorEstado = holder.estado.getContext().getColor(
                    foodtruck.abierto ? R.color.sb_verde : R.color.sb_rojo
            );
            holder.estado.setTextColor(colorEstado);
            holder.estado.setBackgroundResource(
                    foodtruck.abierto
                            ? R.drawable.bg_badge_abierto
                            : R.drawable.bg_badge_cerrado
            );
            holder.estado.setCompoundDrawableTintList(
                    ColorStateList.valueOf(colorEstado)
            );
        }

        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.alHacerClick(foodtruck, holder.imagen);
            }
        });

        if (holder.btnCompartir != null) {
            holder.btnCompartir.setOnClickListener(v -> {
                if (compartirListener != null) {
                    compartirListener.alCompartir(foodtruck);
                }
            });
        }
    }

    private static String formatearCategorias(Foodtruck foodtruck) {
        List<String> cats = foodtruck.categorias();
        if (cats.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cats.size(); i++) {
            String cat = cats.get(i).trim();
            if (cat.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(cat.substring(0, 1).toUpperCase(Locale.getDefault()))
                    .append(cat.substring(1));
        }
        return sb.toString();
    }

    /**
     * Si el local tiene logo real se muestra completo, sin recortarlo.
     * Si todavía no tiene fotos, se usa la ilustración de su categoría.
     */
    private void mostrarLogo(FoodtruckViewHolder holder, Foodtruck foodtruck) {

        if (foodtruck.logoNombre.isEmpty()) {

            holder.imagen.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.imagen.setPadding(0, 0, 0, 0);
            holder.imagen.setImageResource(foodtruck.imagen);
            return;
        }

        boolean destacado = layoutRes == R.layout.item_foodtruck_destacado;
        int margen = Math.round(dp(holder.imagen, 8f));

        holder.imagen.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        holder.imagen.setPadding(margen, margen, margen, margen);

        Imagenes.cargar(
                holder.imagen,
                foodtruck.logoNombre,
                destacado ? 180 : 120,
                destacado ? 110 : 120
        );
    }

    @Override
    public void onViewRecycled(@NonNull FoodtruckViewHolder holder) {
        super.onViewRecycled(holder);

        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);

        // Descarta una carga pendiente de la posición anterior.
        holder.imagen.setTag(R.id.tag_imagen, null);
    }

    @Override
    public int getItemCount() {
        return datos.size();
    }

    private static String clave(Foodtruck foodtruck) {
        return foodtruck.id.isEmpty() ? foodtruck.nombre : foodtruck.id;
    }

    private static float dp(View vista, float valor) {
        return valor * vista.getResources().getDisplayMetrics().density;
    }

    static class FoodtruckViewHolder extends RecyclerView.ViewHolder {

        final ImageView imagen;
        final TextView nombre;
        final TextView descripcion;
        final TextView categorias;
        final TextView rating;
        final TextView distancia;
        final TextView estado;
        final View btnCompartir;

        FoodtruckViewHolder(@NonNull View itemView) {
            super(itemView);

            imagen = itemView.findViewById(R.id.imgFoodtruck);
            nombre = itemView.findViewById(R.id.txtNombreFoodtruck);
            descripcion = itemView.findViewById(R.id.txtDescripcionFoodtruck);
            categorias = itemView.findViewById(R.id.txtCategoriasFoodtruck);
            rating = itemView.findViewById(R.id.txtRatingFoodtruck);
            distancia = itemView.findViewById(R.id.txtDistanciaFoodtruck);
            estado = itemView.findViewById(R.id.txtEstadoFoodtruck);
            btnCompartir = itemView.findViewById(R.id.btnCompartirFoodtruck);

            prepararPresion(itemView);
        }

        /**
         * Encoge suavemente la tarjeta al presionar sin re-rasterizar sombras
         * dinámicas. Devuelve false para no consumir el toque ni el sonido.
         */
        private static void prepararPresion(final View vista) {

            vista.setOnTouchListener((v, evento) -> {

                if (!ValueAnimator.areAnimatorsEnabled()) {
                    return false;
                }

                switch (evento.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:
                        v.animate().cancel();
                        v.animate()
                                .scaleX(ESCALA_PRESIONADO)
                                .scaleY(ESCALA_PRESIONADO)
                                .setDuration(80)
                                .setInterpolator(Animaciones.CURVA_SUAVE)
                                .start();
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().cancel();
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(140)
                                .setInterpolator(Animaciones.CURVA_SUAVE)
                                .start();
                        break;

                    default:
                        break;
                }

                return false;
            });
        }
    }
}
