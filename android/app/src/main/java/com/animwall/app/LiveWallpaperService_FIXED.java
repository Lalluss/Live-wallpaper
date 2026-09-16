package com.animwall.app;

import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class LiveWallpaperService
        extends WallpaperService {

    private static final String PREFS_NAME =
            "AnimeWallPrefs";

    private static final String LIVE_WALLPAPER_URL =
            "live_wallpaper_url";

    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }

    private class LiveEngine extends Engine {

        private MediaPlayer mediaPlayer;
        private boolean visible = false;
        private boolean surfaceReady = false;
        private boolean prepared = false;

        @Override
        public void onCreate(
                SurfaceHolder holder
        ) {

            super.onCreate(holder);

            holder.setFormat(
                    android.graphics.PixelFormat.OPAQUE
            );
        }

        // ============================================================
        // SURFACE CREATED
        // ============================================================

        @Override
        public void onSurfaceCreated(
                SurfaceHolder holder
        ) {

            super.onSurfaceCreated(holder);

            surfaceReady = true;

            preparePlayer(holder);
        }

        // ============================================================
        // SURFACE CHANGED
        // ============================================================

        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder,
                int format,
                int width,
                int height
        ) {

            super.onSurfaceChanged(
                    holder,
                    format,
                    width,
                    height
            );

            if (mediaPlayer != null) {
                mediaPlayer.setDisplay(holder);
            }
        }

        // ============================================================
        // SURFACE DESTROYED
        // ============================================================

        @Override
        public void onSurfaceDestroyed(
                SurfaceHolder holder
        ) {

            surfaceReady = false;

            stopAndReleasePlayer();

            super.onSurfaceDestroyed(holder);
        }

        // ============================================================
        // VISIBILITY
        // ============================================================

        @Override
        public void onVisibilityChanged(
                boolean isVisible
        ) {

            visible = isVisible;

            if (mediaPlayer == null || !prepared) {
                return;
            }

            try {

                if (visible) {

                    mediaPlayer.start();

                } else {

                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.pause();
                    }
                }

            } catch (Exception ignored) {
            }
        }

        // ============================================================
        // PREPARE VIDEO
        // ============================================================

        private void preparePlayer(
                SurfaceHolder holder
        ) {

            stopAndReleasePlayer();
            prepared = false;

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );

            String videoUrl =
                    preferences.getString(
                            LIVE_WALLPAPER_URL,
                            ""
                    );

            if (videoUrl == null ||
                    videoUrl.trim().isEmpty()) {

                return;
            }

            try {

                mediaPlayer =
                        new MediaPlayer();

                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(
                                        AudioAttributes.USAGE_MEDIA
                                )
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_MOVIE
                                )
                                .build()
                );

                // Live wallpapers should be silent.
                mediaPlayer.setVolume(
                        0f,
                        0f
                );

                mediaPlayer.setLooping(true);

                mediaPlayer.setDisplay(holder);

                mediaPlayer.setOnPreparedListener(
                        mp -> {

                            if (visible ||
                                    surfaceReady) {

                                try {
                                    mp.start();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                );

                mediaPlayer.setOnCompletionListener(
                        mp -> {

                            try {
                                mp.seekTo(0);
                                mp.start();
                            } catch (Exception ignored) {
                            }
                        }
                );

                mediaPlayer.setOnErrorListener(
                        (mp, what, extra) -> {

                            android.util.Log.e(
                                    "AnimeWall",
                                    "Live video error: "
                                            + what
                                            + " / "
                                            + extra
                            );

                            stopAndReleasePlayer();

                            return true;
                        }
                );

                mediaPlayer.setDataSource(
                        this@LiveWallpaperService,
                        Uri.parse(videoUrl)
                );

                mediaPlayer.prepareAsync();

            } catch (Exception e) {

                e.printStackTrace();

                stopAndReleasePlayer();
            }
        }

        // ============================================================
        // RELEASE
        // ============================================================

        private void stopAndReleasePlayer() {

            prepared = false;

            if (mediaPlayer == null) {
                return;
            }

            try {

                mediaPlayer.stop();

            } catch (Exception ignored) {
            }

            try {

                mediaPlayer.reset();

            } catch (Exception ignored) {
            }

            try {

                mediaPlayer.release();

            } catch (Exception ignored) {
            }

            mediaPlayer = null;
        }

        // ============================================================
        // DESTROY
        // ============================================================

        @Override
        public void onDestroy() {

            visible = false;
            surfaceReady = false;

            stopAndReleasePlayer();

            super.onDestroy();
        }
    }
}
