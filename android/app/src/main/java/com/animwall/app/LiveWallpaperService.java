package com.animwall.app;

import android.content.SharedPreferences;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

public class LiveWallpaperService extends WallpaperService {

    private static final String TAG = "AnimeWall";
    private static final String PREFS_NAME = "AnimeWallPrefs";
    private static final String LIVE_WALLPAPER_URL = "live_wallpaper_url";

    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }

    private class LiveEngine extends Engine {

        private ExoPlayer player;
        private SurfaceHolder currentHolder;
        private boolean visible = false;
        private boolean surfaceReady = false;
        private boolean destroyed = false;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            currentHolder = holder;
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            currentHolder = holder;
            surfaceReady = true;
            preparePlayer();
        }

        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder,
                int format,
                int width,
                int height) {
            super.onSurfaceChanged(holder, format, width, height);
            currentHolder = holder;

            if (player != null) {
                try {
                    player.setVideoSurfaceHolder(holder);
                } catch (Exception e) {
                    android.util.Log.e(TAG,
                            "Failed to attach wallpaper surface", e);
                }
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;

            if (player != null) {
                try {
                    player.clearVideoSurface();
                } catch (Exception ignored) {
                }
            }

            releasePlayer();

            if (currentHolder == holder) {
                currentHolder = null;
            }

            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;

            if (player == null || !surfaceReady) {
                return;
            }

            try {
                if (visible) {
                    player.play();
                } else {
                    player.pause();
                }
            } catch (Exception e) {
                android.util.Log.e(TAG,
                        "Visibility playback error", e);
            }
        }

        private void preparePlayer() {
            releasePlayer();

            if (destroyed || !surfaceReady || currentHolder == null) {
                return;
            }

            SharedPreferences preferences =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

            String videoUrl = preferences.getString(
                    LIVE_WALLPAPER_URL, "");

            if (videoUrl == null || videoUrl.trim().isEmpty()) {
                android.util.Log.e(TAG,
                        "No live wallpaper URL found");
                return;
            }

            videoUrl = videoUrl.trim();

            if (!videoUrl.startsWith("http://") &&
                    !videoUrl.startsWith("https://")) {
                android.util.Log.e(TAG,
                        "Invalid live wallpaper URL: " + videoUrl);
                return;
            }

            final String finalVideoUrl = videoUrl;

            try {
                player = new ExoPlayer.Builder(
                        LiveWallpaperService.this).build();

                player.setRepeatMode(Player.REPEAT_MODE_ONE);
                player.setVolume(0f);

                // Fill the phone screen without stretching the video.
                // The original aspect ratio is preserved; only the
                // excess edges are cropped when necessary.
                player.setVideoScalingMode(
                        C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                );

                // Render directly to Android WallpaperService surface.
                player.setVideoSurfaceHolder(currentHolder);

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            android.util.Log.d(TAG,
                                    "Live wallpaper video READY");

                            if (visible || surfaceReady) {
                                player.play();
                            }
                        }
                    }

                    @Override
                    public void onPlayerError(PlaybackException error) {
                        android.util.Log.e(TAG,
                                "Live wallpaper playback error: "
                                        + error.getErrorCodeName(),
                                error);
                    }
                });

                player.setMediaItem(MediaItem.fromUri(finalVideoUrl));
                player.setPlayWhenReady(true);
                player.prepare();

                android.util.Log.d(TAG,
                        "Preparing live wallpaper: " + finalVideoUrl);

            } catch (Exception e) {
                android.util.Log.e(TAG,
                        "Failed to create live wallpaper player", e);
                releasePlayer();
            }
        }

        private void releasePlayer() {
            if (player == null) {
                return;
            }

            try {
                player.setPlayWhenReady(false);
                player.clearVideoSurface();
                player.stop();
            } catch (Exception ignored) {
            }

            try {
                player.release();
            } catch (Exception ignored) {
            }

            player = null;
        }

        @Override
        public void onDestroy() {
            destroyed = true;
            visible = false;
            surfaceReady = false;
            releasePlayer();
            currentHolder = null;
            super.onDestroy();
        }
    }
}
