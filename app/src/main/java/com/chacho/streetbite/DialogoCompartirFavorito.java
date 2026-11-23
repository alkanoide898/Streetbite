package com.chacho.streetbite;

import android.text.InputType;
import android.util.Patterns;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * Diálogo reutilizable para compartir un foodtruck con otro usuario registrado
 * en Supabase, creando automáticamente el favorito si aún no estaba guardado.
 */
public final class DialogoCompartirFavorito {

    private DialogoCompartirFavorito() {
    }

    public static void mostrarParaFoodtruck(
            BaseActivity activity,
            Foodtruck foodtruck) {

        if (activity == null || foodtruck == null) {
            return;
        }

        String accessToken = Sesion.accessToken(activity);
        String usuarioId = Sesion.usuarioId(activity);

        if (accessToken.isEmpty() || usuarioId.isEmpty()) {
            Toast.makeText(
                    activity,
                    "Inicia sesión para compartir locales con amigos.",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        if (!Favorito.esUuidValido(foodtruck.id)) {
            Toast.makeText(
                    activity,
                    "Conéctate a internet para compartir locales con otros usuarios.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        float densidad = activity.getResources().getDisplayMetrics().density;
        int padH = Math.round(22 * densidad);
        int padV = Math.round(12 * densidad);
        int padCampo = Math.round(14 * densidad);

        LinearLayout contenedor = new LinearLayout(activity);
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(padH, padV, padH, padV / 2);

        TextView txtInstruccion = new TextView(activity);
        txtInstruccion.setText(
                "Comparte \"" + foodtruck.nombre
                        + "\" con otro usuario registrado en StreetBite. "
                        + "Aparecerá en los favoritos de ambos."
        );
        txtInstruccion.setTextColor(activity.getColor(R.color.sb_texto_suave));
        txtInstruccion.setTextSize(13f);
        contenedor.addView(txtInstruccion);

        EditText edtCorreo = new EditText(activity);
        edtCorreo.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        edtCorreo.setSingleLine(true);
        edtCorreo.setHint("amigo@ejemplo.cl");
        edtCorreo.setBackgroundResource(R.drawable.bg_sb_input);
        edtCorreo.setTextColor(activity.getColor(R.color.sb_marron));
        edtCorreo.setHintTextColor(activity.getColor(R.color.sb_hint));
        edtCorreo.setPadding(padCampo, padCampo, padCampo, padCampo);

        LinearLayout.LayoutParams paramsCampo = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        paramsCampo.topMargin = Math.round(12 * densidad);
        edtCorreo.setLayoutParams(paramsCampo);
        contenedor.addView(edtCorreo);

        TextView lblSeccion = new TextView(activity);
        lblSeccion.setText("Sección");
        lblSeccion.setTextColor(activity.getColor(R.color.sb_marron));
        lblSeccion.setTextSize(14f);
        lblSeccion.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams paramsLblSeccion = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsLblSeccion.topMargin = Math.round(14 * densidad);
        lblSeccion.setLayoutParams(paramsLblSeccion);
        contenedor.addView(lblSeccion);

        Spinner spnEtiqueta = new Spinner(activity);
        ArrayAdapter<String> adaptadorEtiqueta = new ArrayAdapter<>(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                Favorito.ETIQUETAS
        );
        spnEtiqueta.setAdapter(adaptadorEtiqueta);
        for (int i = 0; i < Favorito.ETIQUETAS.length; i++) {
            if ("Con amigos".equalsIgnoreCase(Favorito.ETIQUETAS[i])) {
                spnEtiqueta.setSelection(i);
                break;
            }
        }
        LinearLayout.LayoutParams paramsSpn = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        paramsSpn.topMargin = Math.round(6 * densidad);
        spnEtiqueta.setLayoutParams(paramsSpn);
        contenedor.addView(spnEtiqueta);

        AlertDialog dialogo = new AlertDialog.Builder(activity)
                .setTitle("Compartir con un amigo")
                .setView(contenedor)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Compartir", null)
                .create();

        dialogo.setOnShowListener(d -> {
            Button btnCompartir = dialogo.getButton(AlertDialog.BUTTON_POSITIVE);
            btnCompartir.setOnClickListener(v -> {
                String correo = edtCorreo.getText().toString().trim();
                if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                    Toast.makeText(
                            activity,
                            "Ingresa un correo electrónico válido.",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }

                String etiquetaElegida = String.valueOf(
                        spnEtiqueta.getSelectedItem()
                );

                btnCompartir.setEnabled(false);
                btnCompartir.setText("Compartiendo...");
                asegurarFavoritoYCompartir(
                        activity,
                        accessToken,
                        usuarioId,
                        foodtruck.id,
                        correo,
                        etiquetaElegida,
                        dialogo,
                        btnCompartir
                );
            });
        });

        dialogo.show();
        activity.sonarBotonesDelDialogo(dialogo);
    }

    private static void asegurarFavoritoYCompartir(
            BaseActivity activity,
            String accessToken,
            String usuarioId,
            String foodtruckId,
            String correoDestino,
            String etiquetaElegida,
            AlertDialog dialogo,
            Button btnCompartir) {

        SupabaseHelper.obtenerFavorito(
                accessToken,
                usuarioId,
                foodtruckId,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito existente) {
                        if (existente != null && !existente.id.isEmpty()) {
                            if (!etiquetaElegida.equalsIgnoreCase(existente.etiqueta)) {
                                actualizarSeccionYCompartir(
                                        activity,
                                        accessToken,
                                        existente,
                                        etiquetaElegida,
                                        correoDestino,
                                        dialogo,
                                        btnCompartir
                                );
                                return;
                            }
                            compartir(
                                    activity,
                                    accessToken,
                                    existente.id,
                                    correoDestino,
                                    dialogo,
                                    btnCompartir
                            );
                            return;
                        }

                        SupabaseHelper.crearFavorito(
                                accessToken,
                                usuarioId,
                                foodtruckId,
                                etiquetaElegida,
                                "",
                                Sesion.nombre(activity),
                                new SupabaseHelper.FavoritoCallback() {
                                    @Override
                                    public void onSuccess(Favorito creado) {
                                        if (creado == null || creado.id.isEmpty()) {
                                            rehabilitar(
                                                    activity,
                                                    btnCompartir,
                                                    "No se pudo preparar el favorito."
                                            );
                                            return;
                                        }
                                        compartir(
                                                activity,
                                                accessToken,
                                                creado.id,
                                                correoDestino,
                                                dialogo,
                                                btnCompartir
                                        );
                                    }

                                    @Override
                                    public void onError(String mensaje) {
                                        rehabilitar(activity, btnCompartir, mensaje);
                                    }
                                }
                        );
                    }

                    @Override
                    public void onError(String mensaje) {
                        rehabilitar(activity, btnCompartir, mensaje);
                    }
                }
        );
    }

    private static void actualizarSeccionYCompartir(
            BaseActivity activity,
            String accessToken,
            Favorito existente,
            String etiquetaElegida,
            String correoDestino,
            AlertDialog dialogo,
            Button btnCompartir) {

        String notaActual = existente.nota == null ? "" : existente.nota;
        SupabaseHelper.actualizarFavorito(
                accessToken,
                existente.id,
                etiquetaElegida,
                notaActual,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito actualizado) {
                        compartir(
                                activity,
                                accessToken,
                                existente.id,
                                correoDestino,
                                dialogo,
                                btnCompartir
                        );
                    }

                    @Override
                    public void onError(String mensaje) {
                        rehabilitar(activity, btnCompartir, mensaje);
                    }
                }
        );
    }

    private static void compartir(
            BaseActivity activity,
            String accessToken,
            String favoritoId,
            String correoDestino,
            AlertDialog dialogo,
            Button btnCompartir) {

        SupabaseHelper.compartirFavorito(
                accessToken,
                favoritoId,
                correoDestino,
                new SupabaseHelper.FavoritoCallback() {
                    @Override
                    public void onSuccess(Favorito actualizado) {
                        activity.runOnUiThread(() -> {
                            dialogo.dismiss();
                            String companero = actualizado != null
                                    ? actualizado.nombreCompanero(
                                            Sesion.usuarioId(activity))
                                    : correoDestino;
                            Toast.makeText(
                                    activity,
                                    "Compartido en conjunto con " + companero + ".",
                                    Toast.LENGTH_LONG
                            ).show();
                        });
                    }

                    @Override
                    public void onError(String mensaje) {
                        rehabilitar(activity, btnCompartir, mensaje);
                    }
                }
        );
    }

    private static void rehabilitar(
            BaseActivity activity,
            Button btnCompartir,
            String mensaje) {

        activity.runOnUiThread(() -> {
            btnCompartir.setEnabled(true);
            btnCompartir.setText("Compartir");
            Toast.makeText(activity, mensaje, Toast.LENGTH_LONG).show();
        });
    }
}
