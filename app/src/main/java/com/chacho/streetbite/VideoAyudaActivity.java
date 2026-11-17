package com.chacho.streetbite;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

/**
 * Muestra el recorrido de StreetBite dentro de la propia app.
 *
 * El video vive en res/raw para que esté siempre disponible, incluso sin
 * conexión. Se reproduce apenas se abre la pantalla y el control nativo
 * permite pausarlo, avanzarlo o verlo a pantalla completa.
 */
public class VideoAyudaActivity extends BaseActivity {

    private VideoView videoRecorrido;
    private MediaController controles;
    private boolean reanudarAlVolver = false;
    private int posicion = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_ayuda);

        videoRecorrido = findViewById(R.id.videoRecorrido);

        findViewById(R.id.btnVolverVideo).setOnClickListener(v -> finish());

        Uri video = Uri.parse(
                "android.resource://" + getPackageName()
                        + "/" + R.raw.streetbite_recorrido
        );

        controles = new MediaController(this);
        controles.setAnchorView(videoRecorrido);
        videoRecorrido.setMediaController(controles);
        videoRecorrido.setVideoURI(video);

        videoRecorrido.setOnPreparedListener(mp -> {
            mp.setVideoScalingMode(
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
            );

            if (posicion > 0) {
                videoRecorrido.seekTo(posicion);
            }

            videoRecorrido.start();
            controles.show();
        });

        videoRecorrido.setOnCompletionListener(mp -> {
            reanudarAlVolver = false;
            Toast.makeText(
                    VideoAyudaActivity.this,
                    "Fin del recorrido.",
                    Toast.LENGTH_SHORT
            ).show();
        });

        videoRecorrido.setOnErrorListener((mp, what, extra) -> {
            reanudarAlVolver = false;
            Toast.makeText(
                    VideoAyudaActivity.this,
                    "No se pudo reproducir el video.",
                    Toast.LENGTH_LONG
            ).show();
            return true;
        });
    }

    @Override
    protected void onPause() {
        reanudarAlVolver = videoRecorrido != null
                && videoRecorrido.isPlaying();

        if (videoRecorrido != null && videoRecorrido.isPlaying()) {
            posicion = videoRecorrido.getCurrentPosition();
            videoRecorrido.pause();
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (reanudarAlVolver && videoRecorrido != null) {
            videoRecorrido.start();
        }
    }

    @Override
    protected void onDestroy() {
        if (videoRecorrido != null) {
            videoRecorrido.stopPlayback();
            videoRecorrido.setMediaController(null);
        }

        super.onDestroy();
    }
}
