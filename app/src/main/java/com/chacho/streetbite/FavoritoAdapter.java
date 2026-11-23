package com.chacho.streetbite;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador para la lista de favoritos en FavoritosActivity.
 * Cada tarjeta muestra el foodtruck asociado, la etiqueta elegida, la nota
 * personal del usuario y las acciones de editar o quitar.
 */
public class FavoritoAdapter
        extends RecyclerView.Adapter<FavoritoAdapter.FavoritoViewHolder> {

    public static class ItemFavorito {
        public final Favorito favorito;
        public final Foodtruck foodtruck;

        public ItemFavorito(Favorito favorito, Foodtruck foodtruck) {
            this.favorito = favorito;
            this.foodtruck = foodtruck;
        }
    }

    public interface AccionesListener {
        void onAbrirDetalle(ItemFavorito item, ImageView imgLogo);

        void onCompartir(ItemFavorito item);

        void onEditar(ItemFavorito item);

        void onEliminar(ItemFavorito item);
    }

    private final List<ItemFavorito> items = new ArrayList<>();
    private final AccionesListener listener;

    public FavoritoAdapter(AccionesListener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void actualizar(List<ItemFavorito> nuevaLista) {
        items.clear();
        if (nuevaLista != null) {
            items.addAll(nuevaLista);
        }
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).favorito.id.hashCode();
    }

    @NonNull
    @Override
    public FavoritoViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorito, parent, false);
        return new FavoritoViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(
            @NonNull FavoritoViewHolder holder,
            int position) {

        ItemFavorito item = items.get(position);
        Favorito fav = item.favorito;
        Foodtruck ft = item.foodtruck;

        if (ft != null) {
            holder.txtNombre.setText(ft.nombre);
            holder.txtRating.setText(ft.ratingTexto());
            holder.txtEstado.setText(ft.estadoTexto());
            int colorEstado = holder.txtEstado.getContext().getColor(
                    ft.abierto ? R.color.sb_verde : R.color.sb_rojo
            );
            holder.txtEstado.setTextColor(colorEstado);
            holder.txtEstado.setBackgroundResource(
                    ft.abierto
                            ? R.drawable.bg_badge_abierto
                            : R.drawable.bg_badge_cerrado
            );
            holder.txtEstado.setCompoundDrawableTintList(
                    android.content.res.ColorStateList.valueOf(colorEstado)
            );
            holder.txtEstado.setVisibility(View.VISIBLE);
            mostrarLogo(holder.imgLogo, ft);
        } else {
            holder.txtNombre.setText("Foodtruck guardado");
            holder.txtRating.setText("--");
            holder.txtEstado.setVisibility(View.GONE);
            holder.imgLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            holder.imgLogo.setPadding(0, 0, 0, 0);
            holder.imgLogo.setImageResource(R.drawable.tacos);
        }

        holder.txtEtiqueta.setText(fav.etiqueta);

        String miUsuarioId = Sesion.usuarioId(holder.itemView.getContext());
        if (fav.estaCompartido()) {
            holder.contenedorTarjeta.setBackgroundResource(
                    R.drawable.bg_tarjeta_compartida
            );
            holder.txtCompartido.setText(
                    "En conjunto con " + fav.nombreCompanero(miUsuarioId)
            );
            holder.txtCompartido.setVisibility(View.VISIBLE);
        } else {
            holder.contenedorTarjeta.setBackgroundResource(
                    R.drawable.bg_tarjeta_borde
            );
            holder.txtCompartido.setVisibility(View.GONE);
        }

        if (fav.nota.isEmpty()) {
            holder.txtNota.setVisibility(View.GONE);
        } else {
            holder.txtNota.setText(fav.nota);
            holder.txtNota.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAbrirDetalle(item, holder.imgLogo);
            }
        });

        holder.btnCompartir.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCompartir(item);
            }
        });

        holder.btnEditar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEditar(item);
            }
        });

        holder.btnQuitar.setOnClickListener(v -> {
            if (listener != null) {
                listener.onEliminar(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void mostrarLogo(ImageView vista, Foodtruck ft) {
        if (ft.logoNombre != null && !ft.logoNombre.isEmpty()) {
            int pad = Math.round(
                    6 * vista.getResources().getDisplayMetrics().density
            );
            vista.setScaleType(ImageView.ScaleType.FIT_CENTER);
            vista.setPadding(pad, pad, pad, pad);
            Imagenes.cargar(vista, ft.logoNombre, 96, 96);
            return;
        }

        vista.setScaleType(ImageView.ScaleType.CENTER_CROP);
        vista.setPadding(0, 0, 0, 0);
        vista.setImageResource(ft.imagen);
    }

    static class FavoritoViewHolder extends RecyclerView.ViewHolder {

        final View contenedorTarjeta;
        final ImageView imgLogo;
        final TextView txtNombre;
        final TextView txtRating;
        final TextView txtEstado;
        final TextView txtEtiqueta;
        final TextView txtCompartido;
        final TextView txtNota;
        final TextView btnCompartir;
        final TextView btnEditar;
        final TextView btnQuitar;

        FavoritoViewHolder(@NonNull View itemView) {
            super(itemView);
            contenedorTarjeta = itemView.findViewById(R.id.contenedorTarjetaFavorito);
            imgLogo = itemView.findViewById(R.id.imgFavoritoLogo);
            txtNombre = itemView.findViewById(R.id.txtFavoritoNombre);
            txtRating = itemView.findViewById(R.id.txtFavoritoRating);
            txtEstado = itemView.findViewById(R.id.txtFavoritoEstado);
            txtEtiqueta = itemView.findViewById(R.id.txtFavoritoEtiqueta);
            txtCompartido = itemView.findViewById(R.id.txtFavoritoCompartido);
            txtNota = itemView.findViewById(R.id.txtFavoritoNota);
            btnCompartir = itemView.findViewById(R.id.btnCompartirFavorito);
            btnEditar = itemView.findViewById(R.id.btnEditarFavorito);
            btnQuitar = itemView.findViewById(R.id.btnQuitarFavorito);
        }
    }
}
