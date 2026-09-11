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

        private EGLDisplay eglDisplay;
        private EGLContext eglContext;
        private EGLSurface eglSurface;

        private int program;
        private int textureId;

        private int positionHandle;
        private int texCoordHandle;
        private int textureHandle;
        private int timeHandle;
        private int resolutionHandle;

        private int surfaceWidth = 1;
        private int surfaceHeight = 1;

        private Bitmap bitmap;

        private final FloatBuffer vertexBuffer;
        private final FloatBuffer texBuffer;

        private long startTime;

        private final float[] vertices = {
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
        };

        private final float[] texCoords = {
                0f, 1f,
                1f, 1f,
                0f, 0f,
                1f, 0f
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

            texBuffer = ByteBuffer
                    .allocateDirect(texCoords.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();

            texBuffer.put(texCoords);
            texBuffer.position(0);
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            startTime = System.currentTimeMillis();

            loadWallpaper();
        }

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

                try {

                    URL url = new URL(imageUrl);

                    HttpURLConnection connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);

                    connection.setDoInput(true);

                    connection.connect();

                    InputStream input =
                            connection.getInputStream();

                    Bitmap loadedBitmap =
                            BitmapFactory.decodeStream(input);

                    input.close();

                    connection.disconnect();

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
                }

            }).start();
        }

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

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {

            super.onSurfaceCreated(holder);

            surfaceWidth = holder.getSurfaceFrame().width();
            surfaceHeight = holder.getSurfaceFrame().height();

            initOpenGL(holder);

            if (bitmap != null) {
                uploadTexture();
            }
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

            surfaceWidth = width;
            surfaceHeight = height;

            if (eglContext != null) {

                GLES20.glViewport(
                        0,
                        0,
                        width,
                        height
                );
            }
        }

        private void initOpenGL(SurfaceHolder holder) {

            eglDisplay = EGL14.eglGetDisplay(
                    EGL14.EGL_DEFAULT_DISPLAY
            );

            int[] version = new int[2];

            EGL14.eglInitialize(
                    eglDisplay,
                    version,
                    0,
                    version,
                    1
            );

            int[] configAttributes = {
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

                    EGL14.EGL_NONE
            };

            EGLConfig[] configs =
                    new EGLConfig[1];

            int[] numConfigs = new int[1];

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

            EGL14.eglMakeCurrent(
                    eglDisplay,
                    eglSurface,
                    eglSurface,
                    eglContext
            );

            String vertexShader =
                    "attribute vec2 aPosition;" +
                    "attribute vec2 aTexCoord;" +
                    "varying vec2 vTexCoord;" +

                    "void main() {" +
                    "    gl_Position = vec4(aPosition, 0.0, 1.0);" +
                    "    vTexCoord = aTexCoord;" +
                    "}";

            String fragmentShader =
                    "precision mediump float;" +

                    "uniform sampler2D uTexture;" +
                    "uniform float uTime;" +
                    "uniform vec2 uResolution;" +

                    "varying vec2 vTexCoord;" +

                    "void main() {" +

                    "    vec2 uv = vTexCoord;" +

                    "    float t = uTime;" +

                    "    // Gentle vertical wave" +
                    "    float wave1 = sin(uv.y * 12.0 + t * 1.2);" +

                    "    float wave2 = sin(uv.y * 25.0 - t * 0.8);" +

                    "    float movement = " +
                    "        (wave1 * 0.0015 + wave2 * 0.0007);" +

                    "    // Stronger movement near image edges" +
                    "    float edge = " +
                    "        smoothstep(0.15, 0.75, uv.y);" +

                    "    uv.x += movement * edge;" +

                    "    // Very subtle breathing movement" +
                    "    float zoom = " +
                    "        1.0 + sin(t * 0.35) * 0.006;" +

                    "    uv = (uv - 0.5) / zoom + 0.5;" +

                    "    // Soft atmospheric distortion" +
                    "    float atmosphere = " +
                    "        sin((uv.x + uv.y) * 8.0 + t * 0.5) * 0.001;" +

                    "    uv.x += atmosphere;" +

                    "    vec4 color = " +
                    "        texture2D(uTexture, uv);" +

                    "    gl_FragColor = color;" +

                    "}";

            int vertexShaderId =
                    compileShader(
                            GLES20.GL_VERTEX_SHADER,
                            vertexShader
                    );

            int fragmentShaderId =
                    compileShader(
                            GLES20.GL_FRAGMENT_SHADER,
                            fragmentShader
                    );

            program =
                    GLES20.glCreateProgram();

            GLES20.glAttachShader(
                    program,
                    vertexShaderId
            );

            GLES20.glAttachShader(
                    program,
                    fragmentShaderId
            );

            GLES20.glLinkProgram(program);

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

            GLES20.glViewport(
                    0,
                    0,
                    surfaceWidth,
                    surfaceHeight
            );

            startTime =
                    System.currentTimeMillis();
        }

        private int compileShader(
                int type,
                String source
        ) {

            int shader =
                    GLES20.glCreateShader(type);

            GLES20.glShaderSource(
                    shader,
                    source
            );

            GLES20.glCompileShader(shader);

            int[] status = new int[1];

            GLES20.glGetShaderiv(
                    shader,
                    GLES20.GL_COMPILE_STATUS,
                    status,
                    0
            );

            if (status[0] == 0) {

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

        private void uploadTexture() {

            if (bitmap == null || eglContext == null) {
                return;
            }

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

            int[] textures = new int[1];

            GLES20.glGenTextures(
                    1,
                    textures,
                    0
            );

            textureId = textures[0];

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

        private void drawFrame() {

            if (!visible) {
                return;
            }

            if (eglDisplay == null ||
                    eglSurface == null ||
                    eglContext == null) {
                return;
            }

            if (program == 0) {
                return;
            }

            if (bitmap != null &&
                    textureId == 0) {

                uploadTexture();
            }

            if (textureId == 0) {
                return;
            }

            try {

                EGL14.eglMakeCurrent(
                        eglDisplay,
                        eglSurface,
                        eglSurface,
                        eglContext
                );

                GLES20.glViewport(
                        0,
                        0,
                        surfaceWidth,
                        surfaceHeight
                );

                GLES20.glClearColor(
                        0f,
                        0f,
                        0f,
                        1f
                );

                GLES20.glClear(
                        GLES20.GL_COLOR_BUFFER_BIT
                );

                GLES20.glUseProgram(program);

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

                texBuffer.position(0);

                GLES20.glEnableVertexAttribArray(
                        texCoordHandle
                );

                GLES20.glVertexAttribPointer(
                        texCoordHandle,
                        2,
                        GLES20.GL_FLOAT,
                        false,
                        0,
                        texBuffer
                );

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

                float time =
                        (System.currentTimeMillis()
                                - startTime) / 1000f;

                GLES20.glUniform1f(
                        timeHandle,
                        time
                );

                GLES20.glUniform2f(
                        resolutionHandle,
                        surfaceWidth,
                        surfaceHeight
                );

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

                EGL14.eglSwapBuffers(
                        eglDisplay,
                        eglSurface
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
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

            releaseOpenGL();
        }

        private void releaseOpenGL() {

            try {

                if (textureId != 0) {

                    int[] textures = {
                            textureId
                    };

                    GLES20.glDeleteTextures(
                            1,
                            textures,
                            0
                    );

                    textureId = 0;
                }

                if (program != 0) {

                    GLES20.glDeleteProgram(
                            program
                    );

                    program = 0;
                }

                if (eglDisplay != null &&
                        eglDisplay != EGL14.EGL_NO_DISPLAY) {

                    EGL14.eglMakeCurrent(
                            eglDisplay,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_SURFACE,
                            EGL14.EGL_NO_CONTEXT
                    );

                    if (eglSurface != null &&
                            eglSurface != EGL14.EGL_NO_SURFACE) {

                        EGL14.eglDestroySurface(
                                eglDisplay,
                                eglSurface
                        );
                    }

                    if (eglContext != null &&
                            eglContext != EGL14.EGL_NO_CONTEXT) {

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

                eglDisplay = null;
                eglContext = null;
                eglSurface = null;
            }
        }
    }
}
