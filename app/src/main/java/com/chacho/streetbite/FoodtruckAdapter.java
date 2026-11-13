package com.chacho.streetbite;

import android.animation.ValueAnimator;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Adaptador compartido por la lista vertical y la fila de destacados.
 * El layout de cada ítem decide qué vistas existen.
 *
 * Incluye las animaciones de la lista: entrada escalonada de las primeras
 * tarjetas, feedback al presionar y transición compartida hacia el detalle.
 */
public class FoodtruckAdapter
        extends RecyclerView.Adapter<FoodtruckAdapter.FoodtruckViewHolder> {

    public interface OnFoodtruckClick {
        void alHacerClick(Foodtruck foodtruck, View foto);
    }

    // Entrada: solo las primeras tarjetas y con un retraso pequeño.
    private static final int MAX_ENTRADA = 6;
    private static final long RETRASO_ENTRADA_MS = 55L;
    private static final long DURACION_ENTRADA_MS = 220L;
    private static final float DESPLAZAMIENTO_ENTRADA_DP = 14f;

    private static final float ESCALA_PRESIONADO = 0.97f;

    @LayoutRes
    private final int layoutRes;

    private final OnFoodtruckClick listener;

    private List<Foodtruck> datos = new ArrayList<>();

    private Location ubicacion;

    // Evita repetir la entrada cuando la tarjeta se recicla o se filtra.
    private final Set<String> yaAnimados = new HashSet<>();

    public FoodtruckAdapter(@LayoutRes int layoutRes,
                            OnFoodtruckClick listener) {
        this.layoutRes = layoutRes;
        this.listener = listener;
        setHasStableIds(true);
    }

    /**
     * Actualiza la lista con DiffUtil: así RecyclerView anima los cambios de
     * los filtros en vez de reemplazar todo de golpe con notifyDataSetChanged.
     */
    public void actualizar(List<Foodtruck> foodtrucks, Location ubicacion) {

        List<Foodtruck> nuevos = foodtrucks == null
                ? new ArrayList<>()
                : new ArrayList<>(foodtrucks);

        DiffUtil.DiffResult resultado = DiffUtil.calculateDiff(
                new Diferencia(datos, this.ubicacion, nuevos, ubicacion),
                true
        );

        datos = nuevos;
        this.ubicacion = ubicacion;

        resultado.dispatchUpdatesTo(this);
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

        holder.imagen.setImageResource(foodtruck.imagen);

        // Nombre único para que la foto viaje de la tarjeta al detalle.
        holder.imagen.setTransitionName("foto_" + clave(foodtruck));

        holder.nombre.setText(foodtruck.nombre);
        holder.rating.setText(foodtruck.ratingTexto());

        if (holder.descripcion != null) {
            holder.descripcion.setText(foodtruck.descripcion);
        }

        if (holder.distancia != null) {
            holder.distancia.setText(foodtruck.distanciaTexto(ubicacion));
        }

        if (holder.estado != null) {
            holder.estado.setText(foodtruck.estadoTexto());
            holder.estado.setTextColor(
                    foodtruck.abierto ? 0xFF4CAF50 : 0xFFD32F2F
            );
        }

        // Si la vista se recicla a mitad de una animación, vuelve a su estado.
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);

        animarEntrada(holder.itemView, position, foodtruck);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.alHacerClick(foodtruck, holder.imagen);
            }
        });
    }

    /** Fade + subida leve, con un pequeño retraso por posición. */
    private void animarEntrada(View vista, int posicion, Foodtruck foodtruck) {

        String clave = clave(foodtruck);

        boolean puedeAnimar = posicion < MAX_ENTRADA
                && !yaAnimados.contains(clave)
                && estaQuieto(vista)
                && ValueAnimator.areAnimatorsEnabled();

        if (!puedeAnimar) {
            vista.setAlpha(1f);
            vista.setTranslationY(0f);
            return;
        }

        yaAnimados.add(clave);

        vista.setAlpha(0f);
        vista.setTranslationY(dp(vista, DESPLAZAMIENTO_ENTRADA_DP));

        vista.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(posicion * RETRASO_ENTRADA_MS)
                .setDuration(DURACION_ENTRADA_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    /**
     * No animar mientras el usuario hace scroll: se vería como un parpadeo
     * en tarjetas que entran y salen de pantalla.
     */
    private boolean estaQuieto(View vista) {
        return !(vista.getParent() instanceof RecyclerView)
                || ((RecyclerView) vista.getParent()).getScrollState()
                == RecyclerView.SCROLL_STATE_IDLE;
    }

    @Override
    public void onViewRecycled(@NonNull FoodtruckViewHolder holder) {
        super.onViewRecycled(holder);

        holder.itemView.animate().cancel();
        holder.itemView.setAlpha(1f);
        holder.itemView.setTranslationY(0f);
        holder.itemView.setScaleX(1f);
        holder.itemView.setScaleY(1f);
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

    /** Compara listas para que RecyclerView anime altas, bajas y movimientos. */
    private static final class Diferencia extends DiffUtil.Callback {

        private final List<Foodtruck> viejos;
        private final Location ubicacionVieja;
        private final List<Foodtruck> nuevos;
        private final Location ubicacionNueva;

        Diferencia(List<Foodtruck> viejos,
                   Location ubicacionVieja,
                   List<Foodtruck> nuevos,
                   Location ubicacionNueva) {
            this.viejos = viejos;
            this.ubicacionVieja = ubicacionVieja;
            this.nuevos = nuevos;
            this.ubicacionNueva = ubicacionNueva;
        }

        @Override
        public int getOldListSize() {
            return viejos.size();
        }

        @Override
        public int getNewListSize() {
            return nuevos.size();
        }

        @Override
        public boolean areItemsTheSame(int viejo, int nuevo) {
            Foodtruck a = viejos.get(viejo);
            Foodtruck b = nuevos.get(nuevo);

            if (!a.id.isEmpty() && !b.id.isEmpty()) {
                return a.id.equals(b.id);
            }

            return a.nombre.equals(b.nombre);
        }

        @Override
        public boolean areContentsTheSame(int viejo, int nuevo) {
            Foodtruck a = viejos.get(viejo);
            Foodtruck b = nuevos.get(nuevo);

            return a.nombre.equals(b.nombre)
                    && a.descripcion.equals(b.descripcion)
                    && a.categoria.equals(b.categoria)
                    && Double.compare(a.rating, b.rating) == 0
                    && a.abierto == b.abierto
                    && a.imagen == b.imagen
                    && a.distanciaTexto(ubicacionVieja)
                    .equals(b.distanciaTexto(ubicacionNueva));
        }
    }

    static class FoodtruckViewHolder extends RecyclerView.ViewHolder {

        final ImageView imagen;
        final TextView nombre;
        final TextView descripcion;
        final TextView rating;
        final TextView distancia;
        final TextView estado;

        FoodtruckViewHolder(@NonNull View itemView) {
            super(itemView);

            imagen = itemView.findViewById(R.id.imgFoodtruck);
            nombre = itemView.findViewById(R.id.txtNombreFoodtruck);
            descripcion = itemView.findViewById(R.id.txtDescripcionFoodtruck);
            rating = itemView.findViewById(R.id.txtRatingFoodtruck);
            distancia = itemView.findViewById(R.id.txtDistanciaFoodtruck);
            estado = itemView.findViewById(R.id.txtEstadoFoodtruck);

            prepararPresion(itemView);
        }

        /**
         * Encoge la tarjeta al presionar y la devuelve con un rebote leve.
         * Devuelve false para no consumir el toque: el click y el sonido
         * siguen funcionando igual.
         */
        private static void prepararPresion(final View vista) {

            final float elevacionNormal = vista instanceof CardView
                    ? ((CardView) vista).getCardElevation()
                    : 0f;

            final float elevacionPresionada =
                    elevacionNormal + dp(vista, 3f);

            vista.setOnTouchListener((v, evento) -> {

                switch (evento.getActionMasked()) {

                    case MotionEvent.ACTION_DOWN:
                        v.animate()
                                .scaleX(ESCALA_PRESIONADO)
                                .scaleY(ESCALA_PRESIONADO)
                                .setDuration(90)
                                .setInterpolator(new DecelerateInterpolator())
                                .start();

                        if (v instanceof CardView) {
                            ((CardView) v).setCardElevation(
                                    elevacionPresionada);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(180)
                                .setInterpolator(new OvershootInterpolator(1.2f))
                                .start();

                        if (v instanceof CardView) {
                            ((CardView) v).setCardElevation(elevacionNormal);
                        }
                        break;

                    default:
                        break;
                }

                return false;
            });
        }
    }
}
