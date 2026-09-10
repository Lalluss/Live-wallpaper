package com.animwall.app;

import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class LiveWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }

    private class LiveEngine extends Engine {

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
        }
    }
}
