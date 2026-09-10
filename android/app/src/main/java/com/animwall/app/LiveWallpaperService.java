package com.animwall.app;

import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

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

        private WebView webView;

        private boolean visible = false;

        private int surfaceWidth = 1080;
        private int surfaceHeight = 1920;

        private final Runnable drawRunnable =
                new Runnable() {
                    @Override
                    public void run() {

                        drawFrame();

                        if (visible) {
                            handler.postDelayed(
                                    this,
                                    33
                            );
                        }
                    }
                };

        @Override
        public void onCreate(
                SurfaceHolder surfaceHolder
        ) {
            super.onCreate(surfaceHolder);

            createWebView();

            loadLiveWallpaper();
        }

        /*
         * ==========================================
         * CREATE HTML/CSS/JS ENGINE
         * ==========================================
         */

        private void createWebView() {

            webView = new WebView(
                    LiveWallpaperService.this
            );

            WebSettings settings =
                    webView.getSettings();

            settings.setJavaScriptEnabled(true);

            settings.setDomStorageEnabled(true);

            settings.setLoadWithOverviewMode(false);

            settings.setUseWideViewPort(false);

            settings.setBuiltInZoomControls(false);

            settings.setDisplayZoomControls(false);

            webView.setBackgroundColor(
                    Color.TRANSPARENT
            );

            webView.setLayerType(
                    View.LAYER_TYPE_HARDWARE,
                    null
            );

            webView.setWebViewClient(
                    new WebViewClient() {

                        @Override
                        public void onPageFinished(
                                WebView view,
                                String url
                        ) {

                            super.onPageFinished(
                                    view,
                                    url
                            );

                            handler.post(
                                    () -> {

                                        if (webView != null) {
                                            webView.measure(
                                                    View.MeasureSpec.makeMeasureSpec(
                                                            surfaceWidth,
                                                            View.MeasureSpec.EXACTLY
                                                    ),
                                                    View.MeasureSpec.makeMeasureSpec(
                                                            surfaceHeight,
                                                            View.MeasureSpec.EXACTLY
                                                    )
                                            );

                                            webView.layout(
                                                    0,
                                                    0,
                                                    surfaceWidth,
                                                    surfaceHeight
                                            );
                                        }

                                    }
                            );
                        }
                    }
            );
        }

        /*
         * ==========================================
         * LOAD SELECTED WALLPAPER
         * ==========================================
         */

        private void loadLiveWallpaper() {

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

            /*
             * Safely encode the image URL.
             */

            String encodedUrl =
                    Uri.encode(imageUrl);

            String htmlUrl =
                    "file:///android_asset/live.html"
                            + "?image="
                            + encodedUrl;

            handler.post(() -> {

                if (webView != null) {

                    webView.loadUrl(
                            htmlUrl
                    );
                }

            });
        }

        /*
         * ==========================================
         * VISIBILITY
         * ==========================================
         */

        @Override
        public void onVisibilityChanged(
                boolean visible
        ) {

            this.visible = visible;

            if (visible) {

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

        /*
         * ==========================================
         * SURFACE CREATED
         * ==========================================
         */

        @Override
        public void onSurfaceCreated(
                SurfaceHolder holder
        ) {

            super.onSurfaceCreated(holder);

            loadLiveWallpaper();
        }

        /*
         * ==========================================
         * SURFACE SIZE
         * ==========================================
         */

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

            surfaceWidth = width;
            surfaceHeight = height;

            if (webView != null) {

                webView.measure(
                        View.MeasureSpec.makeMeasureSpec(
                                width,
                                View.MeasureSpec.EXACTLY
                        ),
                        View.MeasureSpec.makeMeasureSpec(
                                height,
                                View.MeasureSpec.EXACTLY
                        )
                );

                webView.layout(
                        0,
                        0,
                        width,
                        height
                );
            }

            drawFrame();
        }

        /*
         * ==========================================
         * DRAW HTML/CSS/JS TO WALLPAPER
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

                if (webView == null) {
                    return;
                }

                /*
                 * Make sure WebView matches
                 * the wallpaper size.
                 */

                if (
                        webView.getWidth()
                                != surfaceWidth
                                ||
                        webView.getHeight()
                                != surfaceHeight
                ) {

                    webView.measure(
                            View.MeasureSpec.makeMeasureSpec(
                                    surfaceWidth,
                                    View.MeasureSpec.EXACTLY
                            ),
                            View.MeasureSpec.makeMeasureSpec(
                                    surfaceHeight,
                                    View.MeasureSpec.EXACTLY
                            )
                    );

                    webView.layout(
                            0,
                            0,
                            surfaceWidth,
                            surfaceHeight
                    );
                }

                /*
                 * Render the HTML page.
                 *
                 * CSS animations and JavaScript
                 * animations are already running
                 * inside the WebView.
                 */

                webView.draw(canvas);

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

        /*
         * ==========================================
         * SURFACE DESTROYED
         * ==========================================
         */

        @Override
        public void onSurfaceDestroyed(
                SurfaceHolder holder
        ) {

            super.onSurfaceDestroyed(holder);

            visible = false;

            handler.removeCallbacks(
                    drawRunnable
            );

            destroyWebView();
        }

        /*
         * ==========================================
         * CLEANUP
         * ==========================================
         */

        private void destroyWebView() {

            if (webView != null) {

                handler.post(() -> {

                    try {

                        webView.stopLoading();

                        webView.loadUrl(
                                "about:blank"
                        );

                        webView.clearHistory();

                        webView.removeAllViews();

                        webView.destroy();

                    } catch (Exception e) {

                        e.printStackTrace();
                    }

                    webView = null;
                });
            }
        }
    }
}
