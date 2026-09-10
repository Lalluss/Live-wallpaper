package com.animwall.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LiveWallpaperService extends WallpaperService {

    private static final String PREFS_NAME = "AnimeWallPrefs";
    private static final String LIVE_WALLPAPER_URL =
            "live_wallpaper_url";

    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }

    private class LiveEngine extends Engine {

        private final Handler handler = new Handler();

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        private final Paint glowPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap bitmap;

        private boolean visible = false;

        private long startTime;

        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                drawFrame();

                if (visible) {
                    handler.postDelayed(this, 33);
                }
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);

            startTime = System.currentTimeMillis();

            paint.setFilterBitmap(true);
            paint.setDither(true);

            loadWallpaper();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {

            this.visible = visible;

            if (visible) {

                handler.removeCallbacks(drawRunnable);

                handler.post(drawRunnable);

            } else {

                handler.removeCallbacks(drawRunnable);
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {

            super.onSurfaceCreated(holder);

            loadWallpaper();
        }

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

            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {

            super.onSurfaceDestroyed(holder);

            visible = false;

            handler.removeCallbacks(drawRunnable);
        }

        private void loadWallpaper() {

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );

            String imageUrl =
                    preferences.getString(
                            LIVE_WALLPAPER_URL,
                            ""
                    );

            if (imageUrl == null || imageUrl.isEmpty()) {
                return;
            }

            new Thread(() -> {

                HttpURLConnection connection = null;

                try {

                    URL url = new URL(imageUrl);

                    connection =
                            (HttpURLConnection)
                                    url.openConnection();

                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setUseCaches(true);

                    InputStream input =
                            connection.getInputStream();

                    Bitmap downloadedBitmap =
                            BitmapFactory.decodeStream(input);

                    input.close();

                    if (downloadedBitmap != null) {

                        bitmap = downloadedBitmap;

                        handler.post(() -> {

                            if (visible) {
                                drawFrame();
                            }

                        });
                    }

                } catch (Exception e) {

                    e.printStackTrace();

                } finally {

                    if (connection != null) {
                        connection.disconnect();
                    }
                }

            }).start();
        }

        private void drawFrame() {

            SurfaceHolder holder = getSurfaceHolder();

            Canvas canvas = null;

            try {

                canvas = holder.lockCanvas();

                if (canvas == null) {
                    return;
                }

                int width = canvas.getWidth();
                int height = canvas.getHeight();

                canvas.drawColor(Color.BLACK);

                if (bitmap == null) {
                    return;
                }

                /*
                 * ==========================================
                 * 1. COVER IMAGE
                 * ==========================================
                 */

                float imageWidth = bitmap.getWidth();
                float imageHeight = bitmap.getHeight();

                float scale = Math.max(
                        (float) width / imageWidth,
                        (float) height / imageHeight
                );

                float scaledWidth = imageWidth * scale;
                float scaledHeight = imageHeight * scale;

                /*
                 * VERY SMALL BREATHING MOVEMENT
                 *
                 * Important:
                 * The image does NOT continuously move
                 * to the right anymore.
                 */

                float time =
                        (System.currentTimeMillis() - startTime)
                                / 1000f;

                float breathing =
                        (float) Math.sin(time * 0.45f);

                float zoom =
                        1.0f + (breathing * 0.004f);

                float finalWidth =
                        scaledWidth * zoom;

                float finalHeight =
                        scaledHeight * zoom;

                float left =
                        (width - finalWidth) / 2f;

                float top =
                        (height - finalHeight) / 2f;

                RectF imageRect =
                        new RectF(
                                left,
                                top,
                                left + finalWidth,
                                top + finalHeight
                        );

                canvas.drawBitmap(
                        bitmap,
                        null,
                        imageRect,
                        paint
                );

                /*
                 * ==========================================
                 * 2. SUBTLE LIGHT / GLOW EFFECT
                 * ==========================================
                 *
                 * This gives the wallpaper a very gentle
                 * living/breathing atmosphere.
                 */

                float glowWave =
                        (float)
                                ((Math.sin(time * 1.1f) + 1.0f)
                                        / 2.0f);

                int alpha =
                        (int) (4 + (glowWave * 10));

                glowPaint.setColor(
                        Color.argb(
                                alpha,
                                255,
                                210,
                                80
                        )
                );

                glowPaint.setStyle(
                        Paint.Style.FILL
                );

                /*
                 * Very soft transparent light.
                 *
                 * Small area around the center,
                 * not the entire wallpaper.
                 */

                float glowRadius =
                        Math.min(width, height) * 0.32f;

                float centerX =
                        width * 0.50f;

                float centerY =
                        height * 0.48f;

                canvas.drawCircle(
                        centerX,
                        centerY,
                        glowRadius,
                        glowPaint
                );

                /*
                 * ==========================================
                 * 3. TINY PARALLAX MOVEMENT
                 * ==========================================
                 *
                 * Only a few pixels.
                 *
                 * This prevents the wallpaper from feeling
                 * completely static while keeping the image
                 * visually stable.
                 */

                float parallaxX =
                        (float) Math.sin(time * 0.35f) * 1.5f;

                float parallaxY =
                        (float) Math.cos(time * 0.30f) * 1.0f;

                /*
                 * Small translucent atmospheric layer.
                 */

                Paint atmosphere =
                        new Paint(Paint.ANTI_ALIAS_FLAG);

                atmosphere.setColor(
                        Color.argb(
                                5,
                                255,
                                255,
                                255
                        )
                );

                canvas.save();

                canvas.translate(
                        parallaxX,
                        parallaxY
                );

                canvas.drawRect(
                        0,
                        0,
                        width,
                        height,
                        atmosphere
                );

                canvas.restore();

            } catch (Exception e) {

                e.printStackTrace();

            } finally {

                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
