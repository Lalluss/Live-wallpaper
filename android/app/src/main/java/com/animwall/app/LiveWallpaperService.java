package com.animwall.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LiveWallpaperService extends WallpaperService {

    private static final String PREFS_NAME =
            "AnimeWallPrefs";

    private static final String LIVE_WALLPAPER_URL =
            "live_wallpaper_url";


    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }


    private class LiveEngine extends Engine {

        private final Handler handler =
                new Handler();

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private Bitmap bitmap;

        private boolean visible = false;

        private float animationOffset = 0f;


        private final Runnable drawRunnable =
                new Runnable() {

                    @Override
                    public void run() {

                        drawFrame();

                        if (visible) {

                            handler.postDelayed(
                                    this,
                                    40
                            );
                        }
                    }
                };


        @Override
        public void onVisibilityChanged(
                boolean visible
        ) {

            this.visible = visible;

            if (visible) {

                if (bitmap == null) {
                    loadWallpaper();
                }

                handler.removeCallbacks(
                        drawRunnable
                );

                handler.post(
                        drawRunnable
                );

            } else {

                handler.removeCallbacks(
                        drawRunnable
                );
            }
        }


        @Override
        public void onSurfaceCreated(
                SurfaceHolder holder
        ) {

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
        public void onSurfaceDestroyed(
                SurfaceHolder holder
        ) {

            super.onSurfaceDestroyed(holder);

            visible = false;

            handler.removeCallbacks(
                    drawRunnable
            );
        }


        /*
         * ==========================================
         * LOAD SELECTED WALLPAPER
         * ==========================================
         */

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


            if (
                    imageUrl == null ||
                    imageUrl.isEmpty()
            ) {

                return;
            }


            new Thread(() -> {

                HttpURLConnection connection =
                        null;

                try {

                    URL url =
                            new URL(imageUrl);


                    connection =
                            (HttpURLConnection)
                                    url.openConnection();


                    connection.setConnectTimeout(
                            15000
                    );

                    connection.setReadTimeout(
                            15000
                    );


                    connection.setUseCaches(false);


                    InputStream input =
                            connection.getInputStream();


                    Bitmap downloadedBitmap =
                            BitmapFactory.decodeStream(
                                    input
                            );


                    input.close();


                    if (
                            downloadedBitmap != null
                    ) {

                        bitmap =
                                downloadedBitmap;


                        if (visible) {

                            handler.post(
                                    this::drawFrame
                            );
                        }
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


        /*
         * ==========================================
         * DRAW LIVE WALLPAPER
         * ==========================================
         */

        private void drawFrame() {

            SurfaceHolder holder =
                    getSurfaceHolder();


            Canvas canvas = null;


            try {

                canvas =
                        holder.lockCanvas();


                if (canvas == null) {
                    return;
                }


                canvas.drawColor(
                        Color.BLACK
                );


                if (bitmap == null) {
                    return;
                }


                int canvasWidth =
                        canvas.getWidth();

                int canvasHeight =
                        canvas.getHeight();


                int imageWidth =
                        bitmap.getWidth();

                int imageHeight =
                        bitmap.getHeight();


                /*
                 * Scale image so it fills
                 * the complete screen.
                 */

                float scale =
                        Math.max(
                                (float) canvasWidth /
                                        imageWidth,

                                (float) canvasHeight /
                                        imageHeight
                        );


                float scaledWidth =
                        imageWidth * scale;


                float scaledHeight =
                        imageHeight * scale;


                /*
                 * Small smooth movement.
                 */

                animationOffset +=
                        0.12f;


                if (
                        animationOffset > 12f
                ) {

                    animationOffset = -12f;
                }


                float left =
                        (canvasWidth -
                                scaledWidth) / 2f
                                + animationOffset;


                float top =
                        (canvasHeight -
                                scaledHeight) / 2f;


                canvas.drawBitmap(
                        bitmap,
                        null,
                        new android.graphics.RectF(
                                left,
                                top,
                                left + scaledWidth,
                                top + scaledHeight
                        ),
                        paint
                );


            } catch (Exception e) {

                e.printStackTrace();

            } finally {

                if (canvas != null) {

                    holder.unlockCanvasAndPost(
                            canvas
                    );
                }
            }
        }
    }
}
