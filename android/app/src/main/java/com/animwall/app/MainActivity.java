package com.animwall.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private Bitmap wallpaperBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 40, 30, 30);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("AnimeWall");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        layout.addView(title);

        ImageView imageView = new ImageView(this);
        imageView.setAdjustViewBounds(true);

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        600
                );

        imageParams.setMargins(0, 30, 0, 30);

        layout.addView(imageView, imageParams);

        Button homeButton = new Button(this);
        homeButton.setText("🏠 Set Home Wallpaper");

        Button lockButton = new Button(this);
        lockButton.setText("🔒 Set Lock Wallpaper");

        Button bothButton = new Button(this);
        bothButton.setText("🏠🔒 Set Both");

        layout.addView(homeButton);
        layout.addView(lockButton);
        layout.addView(bothButton);

        setContentView(layout);

        loadWallpaper(imageView);

        homeButton.setOnClickListener(v ->
                setWallpaper(WallpaperManager.FLAG_SYSTEM)
        );

        lockButton.setOnClickListener(v ->
                setWallpaper(WallpaperManager.FLAG_LOCK)
        );

        bothButton.setOnClickListener(v ->
                setWallpaper(
                        WallpaperManager.FLAG_SYSTEM |
                        WallpaperManager.FLAG_LOCK
                )
        );
    }


    private void loadWallpaper(ImageView imageView) {

        new Thread(() -> {

            try {

                URL url = new URL(
                        "https://live-wallpaper-theta.vercel.app/api/wallpapers"
                );

                HttpURLConnection connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                InputStream input =
                        connection.getInputStream();

                StringBuilder result =
                        new StringBuilder();

                byte[] buffer = new byte[4096];

                int length;

                while (
                        (length = input.read(buffer)) != -1
                ) {
                    result.append(
                            new String(buffer, 0, length)
                    );
                }

                input.close();

                String json = result.toString();

                int imageStart =
                        json.indexOf("\"image\":\"");

                if (imageStart == -1) {
                    throw new Exception(
                            "Wallpaper not found"
                    );
                }

                imageStart += 9;

                int imageEnd =
                        json.indexOf("\"", imageStart);

                String imageUrl =
                        json.substring(
                                imageStart,
                                imageEnd
                        );

                imageUrl =
                        imageUrl.replace("\\/", "/");

                downloadImage(
                        imageUrl,
                        imageView
                );

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "❌ Failed to load wallpaper",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }

        }).start();
    }


    private void downloadImage(
            String imageUrl,
            ImageView imageView
    ) {

        new Thread(() -> {

            try {

                URL url =
                        new URL(imageUrl);

                HttpURLConnection connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(15000);

                InputStream input =
                        connection.getInputStream();

                Bitmap bitmap =
                        BitmapFactory.decodeStream(input);

                input.close();

                wallpaperBitmap = bitmap;

                new Handler(
                        Looper.getMainLooper()
                ).post(() ->
                        imageView.setImageBitmap(bitmap)
                );

            } catch (Exception e) {

                e.printStackTrace();

            }

        }).start();
    }


    private void setWallpaper(int flags) {

        if (wallpaperBitmap == null) {

            Toast.makeText(
                    this,
                    "⏳ Wallpaper still loading...",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            WallpaperManager manager =
                    WallpaperManager.getInstance(this);

            manager.setBitmap(
                    wallpaperBitmap,
                    null,
                    true,
                    flags
            );

            Toast.makeText(
                    this,
                    "✅ Wallpaper applied!",
                    Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            e.printStackTrace();

            Toast.makeText(
                    this,
                    "❌ Failed to set wallpaper",
                    Toast.LENGTH_LONG
            ).show();
        }
    }
          }
