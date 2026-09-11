package com.animwall.app;

import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public class LiveWallpaperService extends WallpaperService {

    private static final String PREFS_NAME = "AnimeWallPrefs";
    private static final String LIVE_WALLPAPER_URL = "live_wallpaper_url";

    @Override
    public Engine onCreateEngine() {
        return new LiveEngine();
    }

    private class LiveEngine extends Engine {

        private final Handler handler = new Handler();

        private boolean visible = false;

        private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
        private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;

        private int program = 0;
        private int textureId = 0;

        private int positionHandle = -1;
        private int texCoordHandle = -1;
        private int textureHandle = -1;
        private int timeHandle = -1;
        private int resolutionHandle = -1;

        private int surfaceWidth = 1;
        private int surfaceHeight = 1;

        private Bitmap bitmap;

        private long startTime;

        private final FloatBuffer vertexBuffer;
        private final FloatBuffer texCoordBuffer;

        private final float[] vertices = {
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
        };

        private final float[] texCoords = {
                0.0f, 1.0f,
                1.0f, 1.0f,
                0.0f, 0.0f,
                1.0f, 0.0f
        };

        private final Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {

                if (visible) {
                    drawFrame();
                    handler.postDelayed(this, 33);
                }
            }
        };

        LiveEngine() {

            vertexBuffer = ByteBuffer
                    .allocateDirect(vertices.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();

            vertexBuffer.put(vertices);
            vertexBuffer.position(0);

            texCoordBuffer = ByteBuffer
                    .allocateDirect(texCoords.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();

            texCoordBuffer.put(texCoords);
            texCoordBuffer.position(0);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            startTime = System.currentTimeMillis();

            loadWallpaper();
        }

        // ============================================================
        // LOAD SELECTED WALLPAPER
        // ============================================================

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
                InputStream input = null;

                try {

                    URL url = new URL(imageUrl);

                    connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    connection.setDoInput(true);

                    connection.connect();

                    input = connection.getInputStream();

                    Bitmap loadedBitmap =
                            BitmapFactory.decodeStream(input);

                    if (loadedBitmap == null) {
                        return;
                    }

                    bitmap = loadedBitmap;

                    handler.post(() -> {

                        if (visible) {
                            drawFrame();
                        }

                    });

                } catch (Exception e) {

                    e.printStackTrace();

                } finally {

                    try {
                        if (input != null) {
                            input.close();
                        }
                    } catch (Exception ignored) {
                    }

                    if (connection != null) {
                        connection.disconnect();
                    }
                }

            }).start();
        }

        // ============================================================
        // VISIBILITY
        // ============================================================

        @Override
        public void onVisibilityChanged(boolean isVisible) {

            visible = isVisible;

            if (visible) {

                handler.removeCallbacks(drawRunnable);

                handler.post(drawRunnable);

            } else {

                handler.removeCallbacks(drawRunnable);
            }
        }

        // ============================================================
        // SURFACE CREATED
        // ============================================================

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {

            super.onSurfaceCreated(holder);

            surfaceWidth =
                    holder.getSurfaceFrame().width();

            surfaceHeight =
                    holder.getSurfaceFrame().height();

            if (surfaceWidth <= 0) {
                surfaceWidth = 1080;
            }

            if (surfaceHeight <= 0) {
                surfaceHeight = 1920;
            }

            startTime = System.currentTimeMillis();

            initializeEGL(holder);

            if (eglContext != EGL14.EGL_NO_CONTEXT) {

                initializeShader();

                if (bitmap != null) {
                    uploadTexture();
                }
            }
        }

        // ============================================================
        // SURFACE CHANGED
        // ============================================================

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

            surfaceWidth = Math.max(width, 1);
            surfaceHeight = Math.max(height, 1);

            if (eglContext != EGL14.EGL_NO_CONTEXT) {

                makeCurrent();

                GLES20.glViewport(
                        0,
                        0,
                        surfaceWidth,
                        surfaceHeight
                );
            }

            drawFrame();
        }

        // ============================================================
        // EGL INITIALIZATION
        // ============================================================

        private void initializeEGL(SurfaceHolder holder) {

            releaseEGL();

            eglDisplay =
                    EGL14.eglGetDisplay(
                            EGL14.EGL_DEFAULT_DISPLAY
                    );

            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException(
                        "Unable to get EGL display"
                );
            }

            int[] version = new int[2];

            if (!EGL14.eglInitialize(
                    eglDisplay,
                    version,
                    0,
                    version,
                    1
            )) {

                throw new RuntimeException(
                        "Unable to initialize EGL"
                );
            }

            int[] configAttributes = {

                    EGL14.EGL_SURFACE_TYPE,
                    EGL14.EGL_WINDOW_BIT,

                    EGL14.EGL_RENDERABLE_TYPE,
                    EGL14.EGL_OPENGL_ES2_BIT,

                    EGL14.EGL_RED_SIZE,
                    8,

                    EGL14.EGL_GREEN_SIZE,
                    8,

                    EGL14.EGL_BLUE_SIZE,
                    8,

                    EGL14.EGL_ALPHA_SIZE,
                    8,

                    EGL14.EGL_DEPTH_SIZE,
                    0,

                    EGL14.EGL_STENCIL_SIZE,
                    0,

                    EGL14.EGL_NONE
            };

            EGLConfig[] configs =
                    new EGLConfig[1];

            int[] numConfigs =
                    new int[1];

            boolean configFound =
                    EGL14.eglChooseConfig(
                            eglDisplay,
                            configAttributes,
                            0,
                            configs,
                            0,
                            1,
                            numConfigs,
                            0
                    );

            if (!configFound ||
                    numConfigs[0] <= 0 ||
                    configs[0] == null) {

                throw new RuntimeException(
                        "No suitable EGL configuration"
                );
            }

            EGLConfig config = configs[0];

            int[] contextAttributes = {

                    EGL14.EGL_CONTEXT_CLIENT_VERSION,
                    2,

                    EGL14.EGL_NONE
            };

            eglContext =
                    EGL14.eglCreateContext(
                            eglDisplay,
                            config,
                            EGL14.EGL_NO_CONTEXT,
                            contextAttributes,
                            0
                    );

            if (eglContext == EGL14.EGL_NO_CONTEXT) {

                throw new RuntimeException(
                        "Unable to create EGL context: "
                                + getEglError()
                );
            }

            int[] surfaceAttributes = {
                    EGL14.EGL_NONE
            };

            eglSurface =
                    EGL14.eglCreateWindowSurface(
                            eglDisplay,
                            config,
                            holder,
                            surfaceAttributes,
                            0
                    );

            if (eglSurface == EGL14.EGL_NO_SURFACE) {

                throw new RuntimeException(
                        "Unable to create EGL surface: "
                                + getEglError()
                );
            }

            makeCurrent();

            GLES20.glViewport(
                    0,
                    0,
                    surfaceWidth,
                    surfaceHeight
            );
        }

        private void makeCurrent() {

            if (eglDisplay == EGL14.EGL_NO_DISPLAY ||
                    eglSurface == EGL14.EGL_NO_SURFACE ||
                    eglContext == EGL14.EGL_NO_CONTEXT) {

                return;
            }

            boolean success =
                    EGL14.eglMakeCurrent(
                            eglDisplay,
                            eglSurface,
                            eglSurface,
                            eglContext
                    );

            if (!success) {

                throw new RuntimeException(
                        "eglMakeCurrent failed: "
                                + getEglError()
                );
            }
        }

        private String getEglError() {

            return "0x" +
                    Integer.toHexString(
                            EGL14.eglGetError()
                    );
        }

        // ============================================================
        // SHADER
        // ============================================================

        private void initializeShader() {

            String vertexShaderSource =

                    "attribute vec2 aPosition;" +

                    "attribute vec2 aTexCoord;" +

                    "varying vec2 vTexCoord;" +

                    "void main() {" +

                    "    gl_Position = vec4(" +
                    "        aPosition, 0.0, 1.0);" +

                    "    vTexCoord = aTexCoord;" +

                    "}";

            String fragmentShaderSource =

                    "precision mediump float;" +

                    "uniform sampler2D uTexture;" +

                    "uniform float uTime;" +

                    "uniform vec2 uResolution;" +

                    "varying vec2 vTexCoord;" +

                    "void main() {" +

                    "    vec2 uv = vTexCoord;" +

                    "    float t = uTime;" +

                    // Very gentle vertical flowing movement
                    "    float waveA = sin(" +
                    "        uv.y * 14.0 + t * 0.8" +
                    "    );" +

                    "    float waveB = sin(" +
                    "        uv.y * 28.0 - t * 0.55" +
                    "    );" +

                    "    float movement = " +
                    "        waveA * 0.0009 +" +
                    "        waveB * 0.00035;" +

                    // Movement stronger away from center
                    "    float edgeMask = " +
                    "        smoothstep(0.10, 0.80, uv.y);" +

                    "    uv.x += movement * edgeMask;" +

                    // Tiny breathing effect
                    "    float zoom = " +
                    "        1.0 +" +
                    "        sin(t * 0.25) * 0.003;" +

                    "    uv = " +
                    "        (uv - 0.5) / zoom + 0.5;" +

                    // Keep texture coordinates valid
                    "    uv = clamp(" +
                    "        uv, " +
                    "        vec2(0.001), " +
                    "        vec2(0.999)" +
                    "    );" +

                    "    vec4 color = " +
                    "        texture2D(" +
                    "            uTexture," +
                    "            uv" +
                    "        );" +

                    "    gl_FragColor = color;" +

                    "}";

            int vertexShader =
                    compileShader(
                            GLES20.GL_VERTEX_SHADER,
                            vertexShaderSource
                    );

            int fragmentShader =
                    compileShader(
                            GLES20.GL_FRAGMENT_SHADER,
                            fragmentShaderSource
                    );

            program =
                    GLES20.glCreateProgram();

            if (program == 0) {

                throw new RuntimeException(
                        "Unable to create GL program"
                );
            }

            GLES20.glAttachShader(
                    program,
                    vertexShader
            );

            GLES20.glAttachShader(
                    program,
                    fragmentShader
            );

            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];

            GLES20.glGetProgramiv(
                    program,
                    GLES20.GL_LINK_STATUS,
                    linkStatus,
                    0
            );

            if (linkStatus[0] == 0) {

                String error =
                        GLES20.glGetProgramInfoLog(
                                program
                        );

                GLES20.glDeleteProgram(program);

                program = 0;

                throw new RuntimeException(
                        "Program link error: " + error
                );
            }

            positionHandle =
                    GLES20.glGetAttribLocation(
                            program,
                            "aPosition"
                    );

            texCoordHandle =
                    GLES20.glGetAttribLocation(
                            program,
                            "aTexCoord"
                    );

            textureHandle =
                    GLES20.glGetUniformLocation(
                            program,
                            "uTexture"
                    );

            timeHandle =
                    GLES20.glGetUniformLocation(
                            program,
                            "uTime"
                    );

            resolutionHandle =
                    GLES20.glGetUniformLocation(
                            program,
                            "uResolution"
                    );

            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);
        }

        private int compileShader(
                int type,
                String source
        ) {

            int shader =
                    GLES20.glCreateShader(type);

            if (shader == 0) {

                throw new RuntimeException(
                        "Unable to create shader"
                );
            }

            GLES20.glShaderSource(
                    shader,
                    source
            );

            GLES20.glCompileShader(shader);

            int[] compileStatus =
                    new int[1];

            GLES20.glGetShaderiv(
                    shader,
                    GLES20.GL_COMPILE_STATUS,
                    compileStatus,
                    0
            );

            if (compileStatus[0] == 0) {

                String error =
                        GLES20.glGetShaderInfoLog(
                                shader
                        );

                GLES20.glDeleteShader(shader);

                throw new RuntimeException(
                        "Shader compile error: " + error
                );
            }

            return shader;
        }

        // ============================================================
        // TEXTURE
        // ============================================================

        private void uploadTexture() {

            if (bitmap == null ||
                    eglContext == EGL14.EGL_NO_CONTEXT) {

                return;
            }

            makeCurrent();

            if (textureId != 0) {

                int[] oldTexture = {
                        textureId
                };

                GLES20.glDeleteTextures(
                        1,
                        oldTexture,
                        0
                );

                textureId = 0;
            }

            int[] textures =
                    new int[1];

            GLES20.glGenTextures(
                    1,
                    textures,
                    0
            );

            textureId =
                    textures[0];

            GLES20.glBindTexture(
                    GLES20.GL_TEXTURE_2D,
                    textureId
            );

            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR
            );

            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR
            );

            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE
            );

            GLES20.glTexParameteri(
                    GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE
            );

            GLUtils.texImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    bitmap,
                    0
            );

            GLES20.glBindTexture(
                    GLES20.GL_TEXTURE_2D,
                    0
            );
        }

        // ============================================================
        // DRAW
        // ============================================================

        private void drawFrame() {

            if (!visible) {
                return;
            }

            if (eglDisplay ==
                    EGL14.EGL_NO_DISPLAY) {
                return;
            }

            if (eglContext ==
                    EGL14.EGL_NO_CONTEXT) {
                return;
            }

            if (eglSurface ==
                    EGL14.EGL_NO_SURFACE) {
                return;
            }

            if (program == 0) {
                return;
            }

            makeCurrent();

            if (textureId == 0) {

                if (bitmap != null) {
                    uploadTexture();
                } else {
                    return;
                }
            }

            GLES20.glViewport(
                    0,
                    0,
                    surfaceWidth,
                    surfaceHeight
            );

            GLES20.glClearColor(
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f
            );

            GLES20.glClear(
                    GLES20.GL_COLOR_BUFFER_BIT
            );

            GLES20.glUseProgram(program);

            // Position
            vertexBuffer.position(0);

            GLES20.glEnableVertexAttribArray(
                    positionHandle
            );

            GLES20.glVertexAttribPointer(
                    positionHandle,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    vertexBuffer
            );

            // Texture coordinates
            texCoordBuffer.position(0);

            GLES20.glEnableVertexAttribArray(
                    texCoordHandle
            );

            GLES20.glVertexAttribPointer(
                    texCoordHandle,
                    2,
                    GLES20.GL_FLOAT,
                    false,
                    0,
                    texCoordBuffer
            );

            // Texture
            GLES20.glActiveTexture(
                    GLES20.GL_TEXTURE0
            );

            GLES20.glBindTexture(
                    GLES20.GL_TEXTURE_2D,
                    textureId
            );

            GLES20.glUniform1i(
                    textureHandle,
                    0
            );

            // Animation time
            float time =
                    (System.currentTimeMillis()
                            - startTime) / 1000.0f;

            GLES20.glUniform1f(
                    timeHandle,
                    time
            );

            GLES20.glUniform2f(
                    resolutionHandle,
                    (float) surfaceWidth,
                    (float) surfaceHeight
            );

            // Draw fullscreen quad
            GLES20.glDrawArrays(
                    GLES20.GL_TRIANGLE_STRIP,
                    0,
                    4
            );

            GLES20.glDisableVertexAttribArray(
                    positionHandle
            );

            GLES20.glDisableVertexAttribArray(
                    texCoordHandle
            );

            GLES20.glBindTexture(
                    GLES20.GL_TEXTURE_2D,
                    0
            );

            boolean swapped =
                    EGL14.eglSwapBuffers(
                            eglDisplay,
                            eglSurface
                    );

            if (!swapped) {

                int error =
                        EGL14.eglGetError();

                if (error ==
                        EGL14.EGL_CONTEXT_LOST) {

                    releaseEGL();
                }
            }
        }

        // ============================================================
        // SURFACE DESTROYED
        // ============================================================

        @Override
        public void onSurfaceDestroyed(
                SurfaceHolder holder
        ) {

            super.onSurfaceDestroyed(holder);

            visible = false;

            handler.removeCallbacks(
                    drawRunnable
            );

            releaseEGL();
        }

        // ============================================================
        // EGL CLEANUP
        // ============================================================

        private void releaseEGL() {

            try {

                if (eglDisplay !=
                        EGL14.EGL_NO_DISPLAY) {

                    EGL14.eglMakeCurrent(
                            eglDisplay,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_CONTEXT
                    );

                    if (eglSurface !=
                            EGL14.EGL_NO_SURFACE) {

                        EGL14.eglDestroySurface(
                                eglDisplay,
                                eglSurface
                        );
                    }

                    if (eglContext !=
                            EGL14.EGL_NO_CONTEXT) {

                        EGL14.eglDestroyContext(
                                eglDisplay,
                                eglContext
                        );
                    }

                    EGL14.eglTerminate(
                            eglDisplay
                    );
                }

            } catch (Exception e) {

                e.printStackTrace();

            } finally {

                eglDisplay =
                        EGL14.EGL_NO_DISPLAY;

                eglContext =
                        EGL14.EGL_NO_CONTEXT;

                eglSurface =
                        EGL14.EGL_NO_SURFACE;

                program = 0;
                textureId = 0;
            }
        }

        // ============================================================
        // FINAL CLEANUP
        // ============================================================

        @Override
        public void onDestroy() {

            visible = false;

            handler.removeCallbacks(
                    drawRunnable
            );

            releaseEGL();

            if (bitmap != null &&
                    !bitmap.isRecycled()) {

                bitmap.recycle();
                bitmap = null;
            }

            super.onDestroy();
        }
    }
}
