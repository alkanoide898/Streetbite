package com.chacho.streetbite;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Grilla de fotos del menú dentro del detalle del foodtruck. */
public class MenuAdapter
        extends RecyclerView.Adapter<MenuAdapter.FotoViewHolder> {

    public interface OnFotoClick {
        void alHacerClick(String foto);
    }

    private final List<String> fotos;
    private final OnFotoClick listener;

    public MenuAdapter(List<String> fotos, OnFotoClick listener) {
        this.fotos = fotos == null
                ? new ArrayList<>()
                : new ArrayList<>(fotos);
        this.listener = listener;
    }

    @NonNull
    @Override
    public FotoViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {

        View vista = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu_foto, parent, false);

        return new FotoViewHolder(vista);
    }

    @Override
    public void onBindViewHolder(
            @NonNull FotoViewHolder holder,
            int position) {

        String foto = fotos.get(position);

        Imagenes.cargar(holder.imagen, foto, 180, 190);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.alHacerClick(foto);
            }
        });
    }

    @Override
    public void onViewRecycled(@NonNull FotoViewHolder holder) {
        super.onViewRecycled(holder);
        holder.imagen.setTag(R.id.tag_imagen, null);
    }

    @Override
    public int getItemCount() {
        return fotos.size();
    }

    static class FotoViewHolder extends RecyclerView.ViewHolder {

        final ImageView imagen;

        FotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imagen = itemView.findViewById(R.id.imgMenuFoto);
        }
    }
}
