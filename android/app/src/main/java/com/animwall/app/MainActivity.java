package com.animwall.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private final ArrayList<Bitmap> wallpapers =
            new ArrayList<>();

    private LinearLayout wallpaperContainer;

    private static final String PREFS_NAME =
            "AnimeWallPrefs";

    private static final String LIVE_WALLPAPER_URL =
            "live_wallpaper_url";


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        createUI();

        loadAllWallpapers();
    }


    private void createUI() {

        ScrollView scrollView =
                new ScrollView(this);

        wallpaperContainer =
                new LinearLayout(this);

        wallpaperContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        wallpaperContainer.setPadding(
                25,
                40,
                25,
                40
        );

        scrollView.addView(
                wallpaperContainer
        );

        setContentView(scrollView);
    }


    private void addTitle() {

        TextView title =
                new TextView(this);

        title.setText("AnimeWall");

        title.setTextSize(32);

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                0,
                0,
                30
        );

        wallpaperContainer.addView(title);
    }


    private void loadAllWallpapers() {

        new Thread(() -> {

            try {

                URL apiUrl =
                        new URL(
                                "https://live-wallpaper-theta.vercel.app/api/wallpapers"
                        );

                HttpURLConnection connection =
                        (HttpURLConnection)
                                apiUrl.openConnection();

                connection.setRequestMethod(
                        "GET"
                );

                connection.setConnectTimeout(
                        15000
                );

                connection.setReadTimeout(
                        15000
                );

                InputStream input =
                        connection.getInputStream();

                StringBuilder result =
                        new StringBuilder();

                byte[] buffer =
                        new byte[4096];

                int length;

                while (
                        (length =
                                input.read(buffer)) != -1
                ) {

                    result.append(
                            new String(
                                    buffer,
                                    0,
                                    length
                            )
                    );
                }

                input.close();

                connection.disconnect();


                JSONObject response =
                        new JSONObject(
                                result.toString()
                        );

                JSONArray array =
                        response.getJSONArray(
                                "wallpapers"
                        );


                runOnUiThread(() -> {

                    addTitle();

                    if (array.length() == 0) {

                        showMessage(
                                "No wallpapers available"
                        );

                        return;
                    }


                    for (
                            int i = 0;
                            i < array.length();
                            i++
                    ) {

                        try {

                            JSONObject item =
                                    array.getJSONObject(i);


                            String title =
                                    item.optString(
                                            "title",
                                            "Wallpaper"
                                    );


                            String imageUrl =
                                    item.optString(
                                            "image",
                                            ""
                                    );


                            if (
                                    !imageUrl.isEmpty()
                            ) {

                                addWallpaperCard(
                                        title,
                                        imageUrl
                                );
                            }

                        } catch (Exception e) {

                            e.printStackTrace();
                        }
                    }

                });

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "❌ Failed to load wallpapers",
                                Toast.LENGTH_LONG
                        ).show()
                );
            }

        }).start();
    }


    private void addWallpaperCard(
            String title,
            String imageUrl
    ) {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                0,
                20,
                0,
                35
        );


        TextView name =
                new TextView(this);

        name.setText(title);

        name.setTextSize(22);

        name.setPadding(
                0,
                10,
                0,
                15
        );


        ImageView image =
                new ImageView(this);

        image.setAdjustViewBounds(true);

        image.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );


        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        750
                );


        card.addView(name);

        card.addView(
                image,
                imageParams
        );


        // HOME BUTTON
        Button home =
                new Button(this);

        home.setText(
                "🏠 SET HOME WALLPAPER"
        );


        // LOCK BUTTON
        Button lock =
                new Button(this);

        lock.setText(
                "🔒 SET LOCK WALLPAPER"
        );


        // BOTH BUTTON
        Button both =
                new Button(this);

        both.setText(
                "🏠🔒 SET BOTH"
        );


        // LIVE BUTTON
        Button live =
                new Button(this);

        live.setText(
                "✨ SET LIVE WALLPAPER"
        );

        // 3D WALL BUTTON
        Button wall3D = new Button(this);
        wall3D.setText("🧊 3D WALL");
        wall3D.setOnClickListener(v ->
                                  open3DWallpaper(imageUrl)
                                 );
        
        card.addView(wall3D);


        card.addView(home);

        card.addView(lock);

        card.addView(both);

        card.addView(live);


        wallpaperContainer.addView(card);


        // Download image
        new Thread(() -> {

            try {

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


                Bitmap bitmap =
                        BitmapFactory.decodeStream(
                                input
                        );


                input.close();

                connection.disconnect();


                runOnUiThread(() -> {

                    int position =
                            wallpapers.size();


                    wallpapers.add(bitmap);


                    image.setImageBitmap(
                            bitmap
                    );


                    // HOME
                    home.setOnClickListener(v ->
                            setWallpaper(
                                    position,
                                    WallpaperManager.FLAG_SYSTEM
                            )
                    );


                    // LOCK
                    lock.setOnClickListener(v ->
                            setWallpaper(
                                    position,
                                    WallpaperManager.FLAG_LOCK
                            )
                    );


                    // BOTH
                    both.setOnClickListener(v ->
                            setWallpaper(
                                    position,
                                    WallpaperManager.FLAG_SYSTEM |
                                            WallpaperManager.FLAG_LOCK
                            )
                    );


                    // LIVE
                    live.setOnClickListener(v ->
                            openLiveWallpaper(
                                    imageUrl
                            )
                    );

                });

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() ->
                        Toast.makeText(
                                this,
                                "❌ Image loading failed",
                                Toast.LENGTH_SHORT
                        ).show()
                );
            }

        }).start();
    }


    /*
     * ==========================================
     * SET NORMAL WALLPAPER
     * ==========================================
     */

    private void setWallpaper(
            int position,
            int flags
    ) {

        if (
                position < 0 ||
                position >= wallpapers.size()
        ) {

            Toast.makeText(
                    this,
                    "⏳ Wallpaper still loading...",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        Bitmap bitmap =
                wallpapers.get(position);


        try {

            WallpaperManager manager =
                    WallpaperManager.getInstance(
                            this
                    );


            manager.setBitmap(
                    bitmap,
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


    /*
     * ==========================================
     * OPEN LIVE WALLPAPER
     * ==========================================
     */

    private void openLiveWallpaper(
            String imageUrl
    ) {

        try {

            /*
             * Save selected wallpaper URL
             * so LiveWallpaperService can read it.
             */

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );


            preferences.edit()
                    .putString(
                            LIVE_WALLPAPER_URL,
                            imageUrl
                    )
                    .apply();


            /*
             * Open Android Live Wallpaper
             * confirmation/setup screen.
             */

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.N) {

                Intent intent =
                        new Intent(
                                WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER
                        );


                ComponentName componentName =
                        new ComponentName(
                                this,
                                LiveWallpaperService.class
                        );


                intent.putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        componentName
                );


                startActivity(intent);

            } else {

                Intent intent =
                        new Intent(
                                WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER
                        );

                startActivity(intent);
            }


        } catch (Exception e) {

            e.printStackTrace();


            Toast.makeText(
                    this,
                    "❌ Live Wallpaper setup failed",
                    Toast.LENGTH_LONG
            ).show();
        }
    }


    /*
     * ==========================================
     * MESSAGE
     * ==========================================
     */

    private void showMessage(
            String message
    ) {

        TextView text =
                new TextView(this);

        text.setText(message);

        text.setTextSize(18);

        text.setGravity(
                Gravity.CENTER
        );

        text.setPadding(
                0,
                30,
                0,
                30
        );

        wallpaperContainer.addView(text);
    }
}
