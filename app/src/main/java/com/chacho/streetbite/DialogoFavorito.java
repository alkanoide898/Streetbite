package com.chacho.streetbite;

import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Diálogo reutilizable para crear o editar un favorito (etiqueta + nota
 * personal). Lo usan DetalleFoodtruckActivity y FavoritosActivity.
 */
public final class DialogoFavorito {

    public interface OnGuardarListener {
        void onGuardar(String foodtruckId, String etiqueta, String nota);
    }

    public interface OnEliminarListener {
        void onEliminar();
    }

    private DialogoFavorito() {
    }

    /**
     * Muestra el diálogo para un foodtruck concreto (crear o editar).
     */
    public static void mostrarParaLocal(
            BaseActivity activity,
            String nombreLocal,
            String foodtruckId,
            Favorito existente,
            OnGuardarListener onGuardar,
            OnEliminarListener onEliminar) {

        boolean esEdicion = existente != null;

        LinearLayout contenedor = crearContenedor(activity);

        TextView txtLocal = new TextView(activity);
        txtLocal.setText(nombreLocal == null ? "" : nombreLocal);
        txtLocal.setTextColor(activity.getColor(R.color.sb_marron));
        txtLocal.setTextSize(16f);
        txtLocal.setTypeface(null, android.graphics.Typeface.BOLD);
        contenedor.addView(txtLocal);

        Spinner spnEtiqueta = agregarSelectorEtiqueta(
                activity,
                contenedor,
                esEdicion ? existente.etiqueta : Favorito.ETIQUETAS[0]
        );

        EditText edtNota = agregarCampoNota(
                activity,
                contenedor,
                esEdicion ? existente.nota : ""
        );

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(esEdicion ? "Editar favorito" : "Guardar en favoritos")
                .setView(contenedor)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton(
                        esEdicion ? "Guardar cambios" : "Guardar",
                        (dialog, which) -> {
                            String etiqueta = String.valueOf(
                                    spnEtiqueta.getSelectedItem()
                            );
                            String nota = edtNota.getText().toString().trim();
                            onGuardar.onGuardar(foodtruckId, etiqueta, nota);
                        }
                );

        if (esEdicion && onEliminar != null) {
            builder.setNeutralButton(
                    "Quitar",
                    (dialog, which) -> onEliminar.onEliminar()
            );
        }

        AlertDialog dialogo = builder.create();
        dialogo.show();
        activity.sonarBotonesDelDialogo(dialogo);
    }

    /**
     * Muestra el diálogo eligiendo primero el foodtruck desde una lista de
     * locales disponibles (usado en FavoritosActivity al tocar "+ Agregar").
     */
    public static void mostrarNuevoConSelector(
            BaseActivity activity,
            List<Foodtruck> disponibles,
            OnGuardarListener onGuardar) {

        LinearLayout contenedor = crearContenedor(activity);

        float densidad = activity.getResources().getDisplayMetrics().density;

        TextView lblLocal = new TextView(activity);
        lblLocal.setText("Foodtruck");
        lblLocal.setTextColor(activity.getColor(R.color.sb_marron));
        lblLocal.setTextSize(14f);
        lblLocal.setTypeface(null, android.graphics.Typeface.BOLD);
        contenedor.addView(lblLocal);

        List<String> nombres = new ArrayList<>(disponibles.size());
        for (Foodtruck ft : disponibles) {
            nombres.add(ft.nombre);
        }

        Spinner spnLocal = new Spinner(activity);
        ArrayAdapter<String> adaptadorLocal = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                nombres
        );
        spnLocal.setAdapter(adaptadorLocal);
        LinearLayout.LayoutParams paramsSpn = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsSpn.topMargin = Math.round(6 * densidad);
        spnLocal.setLayoutParams(paramsSpn);
        contenedor.addView(spnLocal);

        Spinner spnEtiqueta = agregarSelectorEtiqueta(
                activity,
                contenedor,
                Favorito.ETIQUETAS[0]
        );

        EditText edtNota = agregarCampoNota(activity, contenedor, "");

        AlertDialog dialogo = new AlertDialog.Builder(activity)
                .setTitle("Agregar a favoritos")
                .setView(contenedor)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    int pos = spnLocal.getSelectedItemPosition();
                    if (pos >= 0 && pos < disponibles.size()) {
                        Foodtruck elegido = disponibles.get(pos);
                        String etiqueta = String.valueOf(
                                spnEtiqueta.getSelectedItem()
                        );
                        String nota = edtNota.getText().toString().trim();
                        onGuardar.onGuardar(elegido.id, etiqueta, nota);
                    }
                })
                .create();

        dialogo.show();
        activity.sonarBotonesDelDialogo(dialogo);
    }

    private static LinearLayout crearContenedor(BaseActivity activity) {
        float densidad = activity.getResources().getDisplayMetrics().density;
        int horizontal = Math.round(22 * densidad);
        int vertical = Math.round(12 * densidad);

        LinearLayout contenedor = new LinearLayout(activity);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(horizontal, vertical, horizontal, vertical / 2);
        return contenedor;
    }

    private static Spinner agregarSelectorEtiqueta(
            BaseActivity activity,
            LinearLayout contenedor,
            String etiquetaInicial) {

        float densidad = activity.getResources().getDisplayMetrics().density;

        TextView lblEtiqueta = new TextView(activity);
        lblEtiqueta.setText("Etiqueta");
        lblEtiqueta.setTextColor(activity.getColor(R.color.sb_marron));
        lblEtiqueta.setTextSize(14f);
        lblEtiqueta.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams paramsLbl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsLbl.topMargin = Math.round(14 * densidad);
        lblEtiqueta.setLayoutParams(paramsLbl);
        contenedor.addView(lblEtiqueta);

        Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adaptador = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                Favorito.ETIQUETAS
        );
        spinner.setAdapter(adaptador);

        for (int i = 0; i < Favorito.ETIQUETAS.length; i++) {
            if (Favorito.ETIQUETAS[i].equalsIgnoreCase(etiquetaInicial)) {
                spinner.setSelection(i);
                break;
            }
        }

        LinearLayout.LayoutParams paramsSpn = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsSpn.topMargin = Math.round(6 * densidad);
        spinner.setLayoutParams(paramsSpn);
        contenedor.addView(spinner);

        return spinner;
    }

    private static EditText agregarCampoNota(
            BaseActivity activity,
            LinearLayout contenedor,
            String notaInicial) {

        float densidad = activity.getResources().getDisplayMetrics().density;

        TextView lblNota = new TextView(activity);
        lblNota.setText("Nota personal (opcional)");
        lblNota.setTextColor(activity.getColor(R.color.sb_marron));
        lblNota.setTextSize(14f);
        lblNota.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams paramsLbl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsLbl.topMargin = Math.round(14 * densidad);
        lblNota.setLayoutParams(paramsLbl);
        contenedor.addView(lblNota);

        EditText edtNota = new EditText(activity);
        edtNota.setBackgroundResource(R.drawable.bg_sb_input);
        edtNota.setHint("Ej: Pedir la mechada sin mayo");
        edtNota.setText(notaInicial == null ? "" : notaInicial);
        edtNota.setTextColor(activity.getColor(R.color.sb_marron));
        edtNota.setHintTextColor(activity.getColor(R.color.sb_hint));
        edtNota.setTextSize(14f);
        edtNota.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                        | InputType.TYPE_TEXT_FLAG_MULTI_LINE
        );
        edtNota.setMinLines(2);
        edtNota.setMaxLines(4);
        edtNota.setGravity(Gravity.TOP | Gravity.START);
        int pad = Math.round(12 * densidad);
        edtNota.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams paramsEdt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsEdt.topMargin = Math.round(8 * densidad);
        edtNota.setLayoutParams(paramsEdt);
        contenedor.addView(edtNota);

        return edtNota;
    }
}
