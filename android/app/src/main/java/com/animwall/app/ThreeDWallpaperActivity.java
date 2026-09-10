package com.animwall.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ThreeDWallpaperActivity extends Activity {

    private ThreeDView threeDView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        String imageUrl =
                getIntent().getStringExtra("image_url");

        threeDView =
                new ThreeDView(imageUrl);

        setContentView(threeDView);
    }

    private class ThreeDView extends View {

        private Bitmap bitmap;

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG |
                        Paint.FILTER_BITMAP_FLAG);

        private float targetX = 0;
        private float targetY = 0;

        private float currentX = 0;
        private float currentY = 0;

        private float lastTouchX;
        private float lastTouchY;

        private boolean touching = false;

        private long lastTime;

        ThreeDView(String imageUrl) {
            super(ThreeDWallpaperActivity.this);

            setLayerType(
                    View.LAYER_TYPE_HARDWARE,
                    null
            );

            loadImage(imageUrl);
        }

        private void loadImage(String imageUrl) {

            if (imageUrl == null ||
                    imageUrl.isEmpty()) {
                return;
            }

            new Thread(() -> {

                HttpURLConnection connection = null;

                try {

                    URL url =
                            new URL(imageUrl);

                    connection =
                            (HttpURLConnection)
                                    url.openConnection();

                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);

                    InputStream input =
                            connection.getInputStream();

                    Bitmap loaded =
                            BitmapFactory.decodeStream(input);

                    input.close();

                    if (connection != null) {
                        connection.disconnect();
                    }

                    bitmap = loaded;

                    postInvalidate();

                } catch (Exception e) {

                    e.printStackTrace();
                }

            }).start();
        }

        @Override
        protected void onDraw(Canvas canvas) {

            super.onDraw(canvas);

            if (bitmap == null) {
                canvas.drawColor(
                        android.graphics.Color.BLACK
                );
                return;
            }

            int width = getWidth();
            int height = getHeight();

            float imageWidth =
                    bitmap.getWidth();

            float imageHeight =
                    bitmap.getHeight();

            /*
             * Cover screen.
             */

            float scale =
                    Math.max(
                            width / imageWidth,
                            height / imageHeight
                    );

            float drawWidth =
                    imageWidth * scale;

            float drawHeight =
                    imageHeight * scale;

            /*
             * Center image.
             */

            float baseLeft =
                    (width - drawWidth) / 2f;

            float baseTop =
                    (height - drawHeight) / 2f;

            /*
             * Smooth movement.
             */

            currentX +=
                    (targetX - currentX) * 0.08f;

            currentY +=
                    (targetY - currentY) * 0.08f;

            /*
             * Small depth effect.
             */

            float movementX =
                    currentX * 1.0f;

            float movementY =
                    currentY * 1.0f;

            /*
             * Draw slightly enlarged image
             * so edges don't become visible.
             */

            float extra =
                    1.035f;

            float finalWidth =
                    drawWidth * extra;

            float finalHeight =
                    drawHeight * extra;

            float left =
                    (width - finalWidth) / 2f
                    + movementX;

            float top =
                    (height - finalHeight) / 2f
                    + movementY;

            canvas.drawBitmap(
                    bitmap,
                    null,
                    new android.graphics.RectF(
                            left,
                            top,
                            left + finalWidth,
                            top + finalHeight
                    ),
                    paint
            );

            /*
             * Continue animation.
             */

            postInvalidateDelayed(16);
        }

        @Override
        public boolean onTouchEvent(
                MotionEvent event
        ) {

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:

                    touching = true;

                    lastTouchX =
                            event.getX();

                    lastTouchY =
                            event.getY();

                    return true;

                case MotionEvent.ACTION_MOVE:

                    float x =
                            event.getX();

                    float y =
                            event.getY();

                    float dx =
                            x - lastTouchX;

                    float dy =
                            y - lastTouchY;

                    targetX +=
                            dx * 0.12f;

                    targetY +=
                            dy * 0.12f;

                    /*
                     * Limit movement.
                     */

                    targetX =
                            Math.max(
                                    -35,
                                    Math.min(
                                            35,
                                            targetX
                                    )
                            );

                    targetY =
                            Math.max(
                                    -35,
                                    Math.min(
                                            35,
                                            targetY
                                    )
                            );

                    lastTouchX = x;
                    lastTouchY = y;

                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:

                    touching = false;

                    /*
                     * Slowly return to center.
                     */

                    targetX = 0;
                    targetY = 0;

                    return true;
            }

            return true;
        }
    }
      }
