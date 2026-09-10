package com.animwall.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class LiveWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }

    private class LiveEngine extends Engine {

        private final Handler handler = new Handler();

        private final Paint paint = new Paint(
            Paint.ANTI_ALIAS_FLAG
        );

        private Bitmap bitmap;

        private boolean visible = false;

        private float offset = 0f;

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

                loadWallpaper();

                handler.removeCallbacks(
                    drawRunnable
                );

                handler.post(drawRunnable);

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
        public void onSurfaceDestroyed(
            SurfaceHolder holder
        ) {

            super.onSurfaceDestroyed(holder);

            visible = false;

            handler.removeCallbacks(
                drawRunnable
            );
        }


        private void loadWallpaper() {

            new Thread(() -> {

                try {

                    /*
                     * The selected wallpaper URL
                     * will be connected here from
                     * MainActivity in the next step.
                     */

                    String imageUrl = null;

                    if (imageUrl == null) {
                        return;
                    }

                    URL url =
                        new URL(imageUrl);

                    HttpURLConnection connection =
                        (HttpURLConnection)
                            url.openConnection();

                    connection.setConnectTimeout(
                        15000
                    );

                    connection.setReadTimeout(
                        15000
                    );

                    InputStream input =
                        connection.getInputStream();

                    Bitmap newBitmap =
                        BitmapFactory.decodeStream(
                            input
                        );

                    input.close();

                    connection.disconnect();

                    bitmap = newBitmap;

                } catch (Exception e) {

                    e.printStackTrace();
                }

            }).start();
        }


        private void drawFrame() {

            SurfaceHolder holder =
                getSurfaceHolder();

            Canvas canvas = null;

            try {

                canvas = holder.lockCanvas();

                if (canvas == null) {
                    return;
                }

                canvas.drawColor(
                    android.graphics.Color.BLACK
                );

                if (bitmap != null) {

                    int canvasWidth =
                        canvas.getWidth();

                    int canvasHeight =
                        canvas.getHeight();

                    float scale =
                        Math.max(
                            (float) canvasWidth /
                                bitmap.getWidth(),

                            (float) canvasHeight /
                                bitmap.getHeight()
                        );

                    float imageWidth =
                        bitmap.getWidth() * scale;

                    float imageHeight =
                        bitmap.getHeight() * scale;


                    /*
                     * Small movement.
                     * This is only the base animation.
                     * Later we will replace this with
                     * individual object movement.
                     */

                    offset += 0.15f;

                    if (offset > 20f) {
                        offset = -20f;
                    }


                    float left =
                        (canvasWidth - imageWidth) / 2
                            + offset;

                    float top =
                        (canvasHeight - imageHeight) / 2;


                    canvas.drawBitmap(
                        bitmap,
                        left,
                        top,
                        paint
                    );
                }

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
