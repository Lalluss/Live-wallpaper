package com.animwall.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final String API_URL =
            "https://live-wallpaper-theta.vercel.app/api/wallpapers";

    private static final String PREFS_NAME =
            "AnimeWallPrefs";

    private static final String LIVE_WALLPAPER_URL =
            "live_wallpaper_url";

    private LinearLayout rootContainer;
    private LinearLayout wallpaperContainer;

    private final ArrayList<WallpaperItem> wallpaperItems =
            new ArrayList<>();

    private final ArrayList<ExoPlayer> livePlayers =
            new ArrayList<>();

    private String currentMode = "wall";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createUI();
        loadAllWallpapers();
    }

    // ============================================================
    // MODEL
    // ============================================================

    private static class WallpaperItem {
        String id;
        String title;
        String imageUrl;
        String type;
        String mediaType;

        WallpaperItem(
                String id,
                String title,
                String imageUrl,
                String type,
                String mediaType
        ) {
            this.id = id;
            this.title = title;
            this.imageUrl = imageUrl;
            this.type = type;
            this.mediaType = mediaType;
        }

        boolean isLive() {
            return "live".equalsIgnoreCase(type)
                    || "video".equalsIgnoreCase(mediaType);
        }
    }

    // ============================================================
    // UI
    // ============================================================

    private void createUI() {

        ScrollView scrollView = new ScrollView(this);

        rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setPadding(
                dp(20),
                dp(28),
                dp(20),
                dp(40)
        );

        scrollView.addView(rootContainer);

        setContentView(scrollView);

        addHeader();
        addModeButtons();

        wallpaperContainer =
                new LinearLayout(this);

        wallpaperContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        rootContainer.addView(
                wallpaperContainer,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void addHeader() {

        TextView title = new TextView(this);

        title.setText("AnimeWall");
        title.setTextSize(32);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));

        rootContainer.addView(title);

        TextView subtitle = new TextView(this);

        subtitle.setText(
                "Anime wallpapers with cinematic motion"
        );

        subtitle.setTextSize(16);
        subtitle.setTextColor(Color.LTGRAY);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(
                0,
                0,
                0,
                dp(24)
        );

        rootContainer.addView(subtitle);
    }

    private void addModeButtons() {

        LinearLayout modeRow =
                new LinearLayout(this);

        modeRow.setOrientation(
                LinearLayout.HORIZONTAL
        );

        modeRow.setGravity(Gravity.CENTER);
        modeRow.setPadding(0, 0, 0, dp(22));

        Button wallButton =
                createModeButton(
                        "🖼️ WALL",
                        true
                );

        wallButton.setOnClickListener(
                v -> switchMode("wall")
        );

        Button liveButton =
                createModeButton(
                        "🎬 LIVE WALL",
                        false
                );

        liveButton.setOnClickListener(
                v -> switchMode("live")
        );

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        dp(72),
                        1
                );

        params.setMargins(
                dp(4),
                0,
                dp(4),
                0
        );

        modeRow.addView(wallButton, params);
        modeRow.addView(liveButton, params);

        rootContainer.addView(modeRow);

        // Keep references using tags.
        wallButton.setTag("wallModeButton");
        liveButton.setTag("liveModeButton");
    }

    private Button createModeButton(
            String text,
            boolean active
    ) {

        Button button = new Button(this);

        button.setText(text);
        button.setTextSize(17);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);

        updateModeButton(
                button,
                active
        );

        return button;
    }

    private void updateModeButton(
            Button button,
            boolean active
    ) {

        GradientDrawable bg =
                new GradientDrawable();

        bg.setCornerRadius(dp(18));

        if (active) {
            bg.setColor(
                    Color.rgb(235, 64, 166)
            );
        } else {
            bg.setColor(
                    Color.rgb(27, 29, 40)
            );

            bg.setStroke(
                    dp(1),
                    Color.rgb(48, 51, 66)
            );
        }

        button.setBackground(bg);
    }

    private void switchMode(String mode) {

        currentMode =
                "live".equals(mode)
                        ? "live"
                        : "wall";

        View wall =
                rootContainer.findViewWithTag(
                        "wallModeButton"
                );

        View live =
                rootContainer.findViewWithTag(
                        "liveModeButton"
                );

        if (wall instanceof Button) {
            updateModeButton(
                    (Button) wall,
                    currentMode.equals("wall")
            );
        }

        if (live instanceof Button) {
            updateModeButton(
                    (Button) live,
                    currentMode.equals("live")
            );
        }

        renderCurrentMode();
    }

    // ============================================================
    // LOAD API
    // ============================================================

    private void loadAllWallpapers() {

        new Thread(() -> {

            HttpURLConnection connection = null;

            try {

                URL apiUrl =
                        new URL(API_URL);

                connection =
                        (HttpURLConnection)
                                apiUrl.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                InputStream input =
                        connection.getInputStream();

                StringBuilder result =
                        new StringBuilder();

                byte[] buffer =
                        new byte[8192];

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

                JSONObject response =
                        new JSONObject(
                                result.toString()
                        );

                JSONArray array =
                        response.getJSONArray(
                                "wallpapers"
                        );

                wallpaperItems.clear();

                for (int i = 0;
                     i < array.length();
                     i++) {

                    JSONObject item =
                            array.getJSONObject(i);

                    String id =
                            item.optString(
                                    "id",
                                    ""
                            );

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

                    String type =
                            item.optString(
                                    "type",
                                    "wall"
                            );

                    String mediaType =
                            item.optString(
                                    "mediaType",
                                    ""
                            );

                    // Old records are treated as static walls.
                    if (!"live".equalsIgnoreCase(type)
                            && !"video".equalsIgnoreCase(mediaType)) {
                        type = "wall";

                        if (mediaType.isEmpty()) {
                            mediaType = "image";
                        }
                    }

                    if (!imageUrl.isEmpty()) {

                        wallpaperItems.add(
                                new WallpaperItem(
                                        id,
                                        title,
                                        imageUrl,
                                        type,
                                        mediaType
                                )
                        );
                    }
                }

                runOnUiThread(
                        this::renderCurrentMode
                );

            } catch (Exception e) {

                e.printStackTrace();

                runOnUiThread(() -> {

                    wallpaperContainer.removeAllViews();

                    showMessage(
                            "❌ Failed to load wallpapers"
                    );
                });

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }

        }).start();
    }

    // ============================================================
    // RENDER
    // ============================================================

    private void renderCurrentMode() {

        if (wallpaperContainer == null) {
            return;
        }

        releaseLivePlayers();
        wallpaperContainer.removeAllViews();

        int count = 0;

        for (WallpaperItem item :
                wallpaperItems) {

            if ("live".equals(currentMode)
                    && !item.isLive()) {
                continue;
            }

            if ("wall".equals(currentMode)
                    && item.isLive()) {
                continue;
            }

            if (item.isLive()) {
                addLiveCard(item);
            } else {
                addStaticCard(item);
            }

            count++;
        }

        if (count == 0) {

            showMessage(
                    "live".equals(currentMode)
                            ? "🎬 No Live Wallpapers available"
                            : "🖼️ No Wallpapers available"
            );
        }
    }

    // ============================================================
    // STATIC WALL CARD
    // ============================================================

    private void addStaticCard(
            WallpaperItem item
    ) {

        LinearLayout card =
                createCard();

        TextView name =
                createCardTitle(
                        "🖼️ " + item.title
                );

        ImageView image =
                new ImageView(this);

        image.setAdjustViewBounds(true);
        image.setScaleType(
                ImageView.ScaleType.CENTER_CROP
        );

        LinearLayout.LayoutParams imageParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(520)
                );

        card.addView(name);
        card.addView(image, imageParams);

        Button home =
                createActionButton(
                        "🏠 SET HOME WALLPAPER"
                );

        Button lock =
                createActionButton(
                        "🔒 SET LOCK WALLPAPER"
                );

        Button both =
                createActionButton(
                        "🏠🔒 SET BOTH"
                );

        card.addView(home);
        card.addView(lock);
        card.addView(both);

        wallpaperContainer.addView(card);

        loadBitmap(
                item.imageUrl,
                bitmap -> {

                    image.setImageBitmap(bitmap);

                    home.setOnClickListener(
                            v -> setBitmapWallpaper(
                                    bitmap,
                                    WallpaperManager.FLAG_SYSTEM
                            )
                    );

                    lock.setOnClickListener(
                            v -> setBitmapWallpaper(
                                    bitmap,
                                    WallpaperManager.FLAG_LOCK
                            )
                    );

                    both.setOnClickListener(
                            v -> setBitmapWallpaper(
                                    bitmap,
                                    WallpaperManager.FLAG_SYSTEM
                                            | WallpaperManager.FLAG_LOCK
                            )
                    );
                }
        );
    }

    // ============================================================
    // LIVE WALL CARD
    // ============================================================

    private void addLiveCard(
            WallpaperItem item
    ) {

        LinearLayout card = createCard();

        TextView name = createCardTitle(
                "🎬 " + item.title
        );

        PlayerView playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(false);
        playerView.setKeepContentOnPlayerReset(true);
        playerView.setResizeMode(
                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        );

        LinearLayout.LayoutParams videoParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(520)
                );

        card.addView(name);
        card.addView(playerView, videoParams);

        Button live = createActionButton(
                "✨ SET LIVE WALLPAPER"
        );

        card.addView(live);
        wallpaperContainer.addView(card);

        try {
            ExoPlayer player =
                    new ExoPlayer.Builder(this).build();

            livePlayers.add(player);
            playerView.setPlayer(player);

            player.setRepeatMode(
                    Player.REPEAT_MODE_ONE
            );

            player.setVolume(0f);

            player.addListener(
                    new Player.Listener() {

                        @Override
                        public void onPlaybackStateChanged(
                                int playbackState
                        ) {
                            if (playbackState ==
                                    Player.STATE_READY) {

                                playerView.setVisibility(
                                        View.VISIBLE
                                );

                                player.play();
                            }
                        }

                        @Override
                        public void onPlayerError(
                                PlaybackException error
                        ) {
                            android.util.Log.e(
                                    "AnimeWall",
                                    "Live video playback error: "
                                            + error.getErrorCodeName()
                                            + " / "
                                            + error.getMessage(),
                                    error
                            );

                            playerView.setVisibility(
                                    View.GONE
                            );

                            TextView errorText =
                                    new TextView(
                                            MainActivity.this
                                    );

                            errorText.setText(
                                    "❌ Video cannot be played"
                            );

                            errorText.setTextColor(
                                    Color.LTGRAY
                            );

                            errorText.setGravity(
                                    Gravity.CENTER
                            );

                            card.addView(
                                    errorText,
                                    1
                            );
                        }
                    }
            );

            MediaItem mediaItem =
                    MediaItem.fromUri(
                            android.net.Uri.parse(
                                    item.imageUrl
                            )
                    );

            player.setMediaItem(mediaItem);
            player.prepare();
            player.play();

        } catch (Exception e) {

            e.printStackTrace();

            TextView errorText =
                    new TextView(this);

            errorText.setText(
                    "❌ Video preview failed"
            );

            errorText.setTextColor(
                    Color.LTGRAY
            );

            errorText.setGravity(
                    Gravity.CENTER
            );

            card.addView(
                    errorText,
                    1
            );
        }

        live.setOnClickListener(
                v -> openLiveWallpaper(
                        item.imageUrl
                )
        );
    }

    private void releaseLivePlayers() {

        for (ExoPlayer player : livePlayers) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }

            try {
                player.release();
            } catch (Exception ignored) {
            }
        }

        livePlayers.clear();
    }

    // ============================================================
    // CARD HELPERS
    // ============================================================

    private LinearLayout createCard() {

        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setPadding(
                0,
                dp(12),
                0,
                dp(30)
        );

        return card;
    }

    private TextView createCardTitle(
            String title
    ) {

        TextView name =
                new TextView(this);

        name.setText(title);
        name.setTextSize(21);
        name.setTextColor(Color.WHITE);
        name.setTypeface(
                null,
                android.graphics.Typeface.BOLD
        );

        name.setPadding(
                dp(4),
                dp(8),
                dp(4),
                dp(12)
        );

        return name;
    }

    private Button createActionButton(
            String text
    ) {

        Button button =
                new Button(this);

        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTextColor(Color.DKGRAY);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(58)
                );

        params.setMargins(
                0,
                dp(8),
                0,
                0
        );

        button.setLayoutParams(params);

        return button;
    }

    // ============================================================
    // IMAGE DOWNLOAD
    // ============================================================

    private interface BitmapCallback {
        void onLoaded(Bitmap bitmap);
    }

    private void loadBitmap(
            String imageUrl,
            BitmapCallback callback
    ) {

        new Thread(() -> {

            try {

                URL url =
                        new URL(imageUrl);

                HttpURLConnection connection =
                        (HttpURLConnection)
                                url.openConnection();

                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);

                InputStream input =
                        connection.getInputStream();

                Bitmap bitmap =
                        BitmapFactory.decodeStream(
                                input
                        );

                input.close();
                connection.disconnect();

                if (bitmap == null) {
                    return;
                }

                runOnUiThread(
                        () -> callback.onLoaded(bitmap)
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }).start();
    }

    // ============================================================
    // STATIC WALLPAPER SET
    // ============================================================

    private void setBitmapWallpaper(
            Bitmap bitmap,
            int flags
    ) {

        try {

            WallpaperManager manager =
                    WallpaperManager.getInstance(
                            this
                    );

            if (Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.N) {

                manager.setBitmap(
                        bitmap,
                        null,
                        true,
                        flags
                );

            } else {

                manager.setBitmap(bitmap);
            }

            android.widget.Toast.makeText(
                    this,
                    "✅ Wallpaper applied!",
                    android.widget.Toast.LENGTH_SHORT
            ).show();

        } catch (Exception e) {

            e.printStackTrace();

            android.widget.Toast.makeText(
                    this,
                    "❌ Failed to set wallpaper",
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    // ============================================================
    // LIVE WALLPAPER
    // ============================================================

    private void openLiveWallpaper(
            String videoUrl
    ) {

        try {

            SharedPreferences preferences =
                    getSharedPreferences(
                            PREFS_NAME,
                            MODE_PRIVATE
                    );

            preferences.edit()
                    .putString(
                            LIVE_WALLPAPER_URL,
                            videoUrl
                    )
                    .apply();

            Intent intent =
                    new Intent(
                            WallpaperManager
                                    .ACTION_CHANGE_LIVE_WALLPAPER
                    );

            ComponentName componentName =
                    new ComponentName(
                            this,
                            LiveWallpaperService.class
                    );

            intent.putExtra(
                    WallpaperManager
                            .EXTRA_LIVE_WALLPAPER_COMPONENT,
                    componentName
            );

            startActivity(intent);

        } catch (Exception e) {

            e.printStackTrace();

            android.widget.Toast.makeText(
                    this,
                    "❌ Live Wallpaper setup failed",
                    android.widget.Toast.LENGTH_LONG
            ).show();
        }
    }

    @Override
    protected void onDestroy() {
        releaseLivePlayers();
        super.onDestroy();
    }

    // ============================================================
    // MESSAGE
    // ============================================================

    private void showMessage(
            String message
    ) {

        TextView text =
                new TextView(this);

        text.setText(message);
        text.setTextSize(18);
        text.setTextColor(Color.LTGRAY);
        text.setGravity(Gravity.CENTER);

        text.setPadding(
                0,
                dp(40),
                0,
                dp(40)
        );

        wallpaperContainer.addView(text);
    }

    private int dp(int value) {
        return Math.round(
                value *
                        getResources()
                                .getDisplayMetrics()
                                .density
        );
    }
}
