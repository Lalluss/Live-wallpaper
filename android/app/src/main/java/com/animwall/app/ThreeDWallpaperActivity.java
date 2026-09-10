package com.animwall.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ThreeDWallpaperActivity extends Activity
        implements SensorEventListener {

    private ThreeDView threeDView;

    private SensorManager sensorManager;
    private Sensor gyroscope;

    private float sensorX = 0f;
    private float sensorY = 0f;

    private float smoothSensorX = 0f;
    private float smoothSensorY = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        requestWindowFeature(
                Window.FEATURE_NO_TITLE
        );

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        String imageUrl =
                getIntent().getStringExtra("image_url");

        threeDView =
                new ThreeDView(imageUrl);

        setContentView(threeDView);

        /*
         * ==========================================
         * GYROSCOPE
         * ==========================================
         */

        sensorManager =
                (SensorManager)
                        getSystemService(
                                SENSOR_SERVICE
                        );

        gyroscope =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_GYROSCOPE
                );
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (gyroscope != null) {

            sensorManager.registerListener(
                    this,
                    gyroscope,
                    SensorManager.SENSOR_DELAY_GAME
            );
        }
    }

    @Override
    protected void onPause() {

        super.onPause();

        if (sensorManager != null) {

            sensorManager.unregisterListener(
                    this
            );
        }
    }

    /*
     * ==========================================
     * SENSOR DATA
     * ==========================================
     */

    @Override
    public void onSensorChanged(
            SensorEvent event
    ) {

        if (event.sensor.getType()
                != Sensor.TYPE_GYROSCOPE) {

            return;
        }

        /*
         * Gyroscope values.
         *
         * We deliberately keep movement
         * very small for a 3D illusion.
         */

        sensorX =
                event.values[1];

        sensorY =
                event.values[0];

        /*
         * Smooth the sensor movement.
         */

        smoothSensorX +=
                (sensorX - smoothSensorX)
                        * 0.08f;

        smoothSensorY +=
                (sensorY - smoothSensorY)
                        * 0.08f;

        if (threeDView != null) {

            threeDView.setSensorMovement(
                    smoothSensorX,
                    smoothSensorY
            );
        }
    }

    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy
    ) {
        // Not required.
    }


    /*
     * ==========================================
     * 3D VIEW
     * ==========================================
     */

    private class ThreeDView extends View {

        private Bitmap bitmap;

        private final Paint paint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG |
                        Paint.FILTER_BITMAP_FLAG
                );

        private float targetX = 0f;
        private float targetY = 0f;

        private float currentX = 0f;
        private float currentY = 0f;

        private float touchX = 0f;
        private float touchY = 0f;

        ThreeDView(String imageUrl) {

            super(
                    ThreeDWallpaperActivity.this
            );

            setLayerType(
                    View.LAYER_TYPE_HARDWARE,
                    null
            );

            loadImage(imageUrl);
        }

        /*
         * ==========================================
         * LOAD IMAGE
         * ==========================================
         */

        private void loadImage(
                String imageUrl
        ) {

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

                    InputStream input =
                            connection.getInputStream();

                    Bitmap loaded =
                            BitmapFactory.decodeStream(
                                    input
                            );

                    input.close();

                    connection.disconnect();

                    bitmap = loaded;

                    postInvalidate();

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
         * SENSOR MOVEMENT
         * ==========================================
         */

        void setSensorMovement(
                float x,
                float y
        ) {

            /*
             * Amplify only slightly.
             */

            targetX =
                    clamp(
                            x * 18f,
                            -28f,
                            28f
                    );

            targetY =
                    clamp(
                            y * 18f,
                            -28f,
                            28f
                    );
        }

        /*
         * ==========================================
         * DRAW
         * ==========================================
         */

        @Override
        protected void onDraw(
                Canvas canvas
        ) {

            super.onDraw(canvas);

            if (bitmap == null) {

                canvas.drawColor(
                        android.graphics.Color.BLACK
                );

                return;
            }

            int width =
                    getWidth();

            int height =
                    getHeight();

            float imageWidth =
                    bitmap.getWidth();

            float imageHeight =
                    bitmap.getHeight();

            /*
             * Cover the screen.
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
             * Slight enlargement prevents
             * black edges during movement.
             */

            float zoom =
                    1.045f;

            float finalWidth =
                    drawWidth * zoom;

            float finalHeight =
                    drawHeight * zoom;

            /*
             * Smooth movement.
             */

            currentX +=
                    (targetX - currentX)
                            * 0.07f;

            currentY +=
                    (targetY - currentY)
                            * 0.07f;

            /*
             * Center + 3D movement.
             */

            float left =
                    (width - finalWidth) / 2f
                    + currentX;

            float top =
                    (height - finalHeight) / 2f
                    + currentY;

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
             * Keep rendering smoothly.
             */

            postInvalidateDelayed(16);
        }

        /*
         * ==========================================
         * TOUCH
         * ==========================================
         */

        @Override
        public boolean onTouchEvent(
                MotionEvent event
        ) {

            switch (
                    event.getActionMasked()
            ) {

                case MotionEvent.ACTION_DOWN:

                    touchX =
                            event.getX();

                    touchY =
                            event.getY();

                    return true;


                case MotionEvent.ACTION_MOVE:

                    float x =
                            event.getX();

                    float y =
                            event.getY();

                    float dx =
                            x - touchX;

                    float dy =
                            y - touchY;

                    targetX +=
                            dx * 0.12f;

                    targetY +=
                            dy * 0.12f;

                    targetX =
                            clamp(
                                    targetX,
                                    -35f,
                                    35f
                            );

                    targetY =
                            clamp(
                                    targetY,
                                    -35f,
                                    35f
                            );

                    touchX = x;
                    touchY = y;

                    return true;


                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:

                    /*
                     * Return smoothly to center.
                     */

                    targetX = 0f;
                    targetY = 0f;

                    return true;
            }

            return true;
        }

        /*
         * ==========================================
         * CLAMP
         * ==========================================
         */

        private float clamp(
                float value,
                float min,
                float max
        ) {

            return Math.max(
                    min,
                    Math.min(
                            max,
                            value
                    )
            );
        }
    }
}
