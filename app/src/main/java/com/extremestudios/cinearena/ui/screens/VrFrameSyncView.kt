package com.extremestudios.cinearena.ui.screens

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import androidx.media3.exoplayer.ExoPlayer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicLong
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class VrStereoMode {
    Mono, SideBySide, TopBottom
}

class VrFrameSyncView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val renderer = VrFrameRenderer()
    private var attachedSurface: Surface? = null
    private var attachedPlayer: ExoPlayer? = null

    var frameTimestampNs = AtomicLong(0L)
        private set
    var onFrameTimestampChanged: ((Long) -> Unit)? = null

    var player: ExoPlayer?
        get() = attachedPlayer
        set(value) {
            attachedPlayer = value
            mainHandler.post {
                attachedSurface?.let { surface ->
                    value?.setVideoSurface(surface)
                }
            }
        }

    var stereoMode: VrStereoMode
        get() = renderer.stereoMode
        set(value) {
            queueEvent {
                renderer.stereoMode = value
                requestRender()
            }
        }

    var eyeSwap: Boolean
        get() = renderer.eyeSwap
        set(value) {
            queueEvent {
                renderer.eyeSwap = value
                requestRender()
            }
        }

    var contentAspectRatio: Float
        get() = renderer.contentAspectRatio
        set(value) {
            queueEvent {
                renderer.contentAspectRatio = value.coerceAtLeast(0.1f)
                requestRender()
            }
        }

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        renderer.onSurfaceReady = { surface ->
            mainHandler.post {
                attachedSurface = surface
                attachedPlayer?.setVideoSurface(surface)
            }
        }
        renderer.onFrameTimestamp = { timestampNs ->
            frameTimestampNs.set(timestampNs)
            mainHandler.post {
                onFrameTimestampChanged?.invoke(timestampNs)
            }
        }
        renderer.requestDraw = { requestRender() }
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onDetachedFromWindow() {
        val playerToClear = attachedPlayer
        mainHandler.post {
            if (playerToClear === attachedPlayer) {
                playerToClear?.clearVideoSurface(attachedSurface)
            }
        }
        queueEvent { renderer.release() }
        super.onDetachedFromWindow()
    }
}

private class VrFrameRenderer : GLSurfaceView.Renderer {
    var stereoMode: VrStereoMode = VrStereoMode.Mono
    var eyeSwap: Boolean = false
    var contentAspectRatio: Float = 16f / 9f
    var onSurfaceReady: ((Surface) -> Unit)? = null
    var onFrameTimestamp: ((Long) -> Unit)? = null
    var requestDraw: (() -> Unit)? = null

    private var program = 0
    private var textureId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var surface: Surface? = null
    private var frameAvailable = false
    private var viewWidth = 1
    private var viewHeight = 1
    private val textureMatrix = FloatArray(16)
    private val quadPositions = floatBuffer(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        textureId = createExternalTexture()
        surfaceTexture = SurfaceTexture(textureId).apply {
            setOnFrameAvailableListener {
                frameAvailable = true
                requestDraw?.invoke()
            }
        }
        surface = Surface(surfaceTexture).also { onSurfaceReady?.invoke(it) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewWidth = width.coerceAtLeast(1)
        viewHeight = height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        val currentTexture = surfaceTexture ?: return
        if (frameAvailable) {
            currentTexture.updateTexImage()
            currentTexture.getTransformMatrix(textureMatrix)
            onFrameTimestamp?.invoke(currentTexture.timestamp)
            frameAvailable = false
        }

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uTexMatrix"),
            1,
            false,
            textureMatrix,
            0
        )

        val halfWidth = viewWidth / 2
        drawEye(viewportX = 0, viewportWidth = halfWidth, texCoords = eyeTexCoords(leftEye = !eyeSwap))
        drawEye(viewportX = halfWidth, viewportWidth = viewWidth - halfWidth, texCoords = eyeTexCoords(leftEye = eyeSwap))
    }

    fun release() {
        surface?.release()
        surfaceTexture?.release()
        surface = null
        surfaceTexture = null
        if (textureId != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
            textureId = 0
        }
        if (program != 0) {
            GLES20.glDeleteProgram(program)
            program = 0
        }
    }

    private fun drawEye(viewportX: Int, viewportWidth: Int, texCoords: FloatArray) {
        val fitted = fitInside(viewportWidth.coerceAtLeast(1), viewHeight, contentAspectRatio)
        GLES20.glViewport(viewportX + fitted.x, fitted.y, fitted.width, fitted.height)

        val positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        val texHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        quadPositions.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, quadPositions)

        val texBuffer = floatBuffer(*texCoords)
        GLES20.glEnableVertexAttribArray(texHandle)
        GLES20.glVertexAttribPointer(texHandle, 2, GLES20.GL_FLOAT, false, 0, texBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texHandle)
    }

    private fun fitInside(width: Int, height: Int, aspectRatio: Float): ViewportRect {
        val targetHeight = (width / aspectRatio).toInt()
        return if (targetHeight <= height) {
            ViewportRect(0, (height - targetHeight) / 2, width, targetHeight.coerceAtLeast(1))
        } else {
            val targetWidth = (height * aspectRatio).toInt().coerceAtLeast(1)
            ViewportRect((width - targetWidth) / 2, 0, targetWidth, height)
        }
    }

    private fun eyeTexCoords(leftEye: Boolean): FloatArray {
        val left = when (stereoMode) {
            VrStereoMode.Mono, VrStereoMode.TopBottom -> 0f
            VrStereoMode.SideBySide -> if (leftEye) 0f else 0.5f
        }
        val right = when (stereoMode) {
            VrStereoMode.Mono, VrStereoMode.TopBottom -> 1f
            VrStereoMode.SideBySide -> if (leftEye) 0.5f else 1f
        }
        val top = when (stereoMode) {
            VrStereoMode.Mono, VrStereoMode.SideBySide -> 0f
            VrStereoMode.TopBottom -> if (leftEye) 0f else 0.5f
        }
        val bottom = when (stereoMode) {
            VrStereoMode.Mono, VrStereoMode.SideBySide -> 1f
            VrStereoMode.TopBottom -> if (leftEye) 0.5f else 1f
        }
        // Keep both eyes on the same frame and correct upside-down output
        // without horizontal mirroring.
        return floatArrayOf(
            left, top,
            right, top,
            left, bottom,
            right, bottom
        )
    }

    private fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        return GLES20.glCreateProgram().also { createdProgram ->
            GLES20.glAttachShader(createdProgram, vertex)
            GLES20.glAttachShader(createdProgram, fragment)
            GLES20.glLinkProgram(createdProgram)
            val status = IntArray(1)
            GLES20.glGetProgramiv(createdProgram, GLES20.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("CineArenaVR", "Program link failed: ${GLES20.glGetProgramInfoLog(createdProgram)}")
                GLES20.glDeleteProgram(createdProgram)
            }
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                Log.e("CineArenaVR", "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
            }
        }
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = (uTexMatrix * aTexCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}

private data class ViewportRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

private fun floatBuffer(vararg values: Float): FloatBuffer {
    return ByteBuffer
        .allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(values)
            position(0)
        }
}
