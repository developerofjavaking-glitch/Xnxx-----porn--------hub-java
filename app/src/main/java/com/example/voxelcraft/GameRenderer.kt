package com.example.voxelcraft

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GameRenderer(private val shared: Shared) : GLSurfaceView.Renderer {

    val world = World()
    private val player = Player()
    private val input = shared.input

    private var program = 0
    private var uVP = 0
    private var uOffset = 0
    private var uFogColor = 0
    private var uFogStart = 0
    private var uFogEnd = 0
    private var aPos = 0
    private var aColor = 0

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val planes = FloatArray(24)
    private val look = FloatArray(2)
    private var aspect = 1f

    private var lastNanos = 0L
    private var fpsTimer = 0f
    private var fpsFrames = 0
    private var fps = 0
    private var blockIndex = 0

    private val skyR = 0.53f
    private val skyG = 0.81f
    private val skyB = 0.92f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(skyR, skyG, skyB, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)
        GLES20.glFrontFace(GLES20.GL_CCW)

        program = buildProgram(VERTEX_SRC, FRAGMENT_SRC)
        uVP = GLES20.glGetUniformLocation(program, "uVP")
        uOffset = GLES20.glGetUniformLocation(program, "uOffset")
        uFogColor = GLES20.glGetUniformLocation(program, "uFogColor")
        uFogStart = GLES20.glGetUniformLocation(program, "uFogStart")
        uFogEnd = GLES20.glGetUniformLocation(program, "uFogEnd")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aColor = GLES20.glGetAttribLocation(program, "aColor")

        world.onContextLost()
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspect = width.toFloat() / maxOf(1, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        if (dt > 0.1f) dt = 0.1f

        // ---- input
        input.consumeLook(look)
        player.yaw += look[0] * LOOK_SENS
        player.pitch = (player.pitch - look[1] * LOOK_SENS).coerceIn(-1.55f, 1.55f)

        // ---- world streaming around the player
        val pcx = Math.floor(player.x).toInt() shr 4
        val pcz = Math.floor(player.z).toInt() shr 4
        world.update(pcx, pcz)
        world.uploadMeshes(Config.UPLOADS_PER_FRAME)

        if (!player.spawned) {
            val c = world.getChunk(0, 0)
            if (c != null && c.dataReady) {
                player.x = 8.5
                player.z = 8.5
                player.y = world.surfaceY(8, 8) + 2.0
                player.spawned = true
            }
        }

        handleActions()
        player.update(dt, input, world)

        // ---- camera (rendered relative to the eye so huge coordinates keep their precision)
        val ex = player.x
        val ey = player.y + Player.EYE
        val ez = player.z
        val cp = cos(player.pitch)
        val fx = sin(player.yaw) * cp
        val fy = sin(player.pitch)
        val fz = -cos(player.yaw) * cp

        val farDist = (Config.RENDER_RADIUS + 1) * Config.CHUNK_SIZE * 1.2f
        Matrix.setLookAtM(view, 0, 0f, 0f, 0f, fx, fy, fz, 0f, 1f, 0f)
        Matrix.perspectiveM(proj, 0, 70f, aspect, 0.1f, farDist)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        extractFrustum(vp)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0)
        val fogEnd = Config.RENDER_RADIUS * Config.CHUNK_SIZE - 6f
        GLES20.glUniform3f(uFogColor, skyR, skyG, skyB)
        GLES20.glUniform1f(uFogStart, fogEnd * 0.55f)
        GLES20.glUniform1f(uFogEnd, fogEnd)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aColor)

        var drawn = 0
        var meshed = 0
        val s = Config.CHUNK_SIZE
        for (c in world.chunks.values) {
            val vbo = c.vbo
            val count = c.vertexCount
            if (vbo == 0 || count == 0) continue
            meshed++
            val ox = (c.cx * s.toDouble() - ex).toFloat()
            val oy = (0.0 - ey).toFloat()
            val oz = (c.cz * s.toDouble() - ez).toFloat()
            if (!inFrustum(ox + s / 2f, oy + Config.WORLD_HEIGHT / 2f, oz + s / 2f, 40f)) continue
            GLES20.glUniform3f(uOffset, ox, oy, oz)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, 0)
            GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, 24, 12)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count)
            drawn++
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // ---- HUD text (twice per second)
        fpsFrames++
        fpsTimer += dt
        if (fpsTimer >= 0.5f) {
            fps = (fpsFrames / fpsTimer).toInt()
            fpsFrames = 0
            fpsTimer = 0f
            shared.hudText = String.format(
                "FPS %d   engine: %s   chunks loaded %d / meshed %d / drawn %d\nXYZ %.1f %.1f %.1f   chunk (%d, %d)",
                fps, if (NativeLib.available) "C/C++" else "Kotlin", world.chunks.size, meshed, drawn, player.x, player.y, player.z, pcx, pcz
            )
        }
    }

    private fun handleActions() {
        while (true) {
            val a = input.actions.poll() ?: break
            when (a) {
                Input.ACT_FLY -> {
                    player.flying = !player.flying
                    player.vy = 0.0
                    shared.flying = player.flying
                }
                Input.ACT_BLOCK -> {
                    blockIndex = (blockIndex + 1) % Blocks.palette.size
                    shared.blockIndex = blockIndex
                }
                Input.ACT_BREAK, Input.ACT_PLACE -> {
                    if (!player.spawned) continue
                    val cp = cos(player.pitch).toDouble()
                    val hit = Raycast.cast(
                        world,
                        player.x, player.y + Player.EYE, player.z,
                        sin(player.yaw) * cp, sin(player.pitch).toDouble(), -cos(player.yaw) * cp,
                        6.0
                    ) ?: continue
                    if (a == Input.ACT_BREAK) {
                        if (hit.y > 0) world.setBlock(hit.x, hit.y, hit.z, Blocks.AIR)
                    } else if (!player.intersectsBlock(hit.px, hit.py, hit.pz)) {
                        world.setBlock(hit.px, hit.py, hit.pz, Blocks.palette[blockIndex])
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ frustum culling

    private fun extractFrustum(m: FloatArray) {
        for (k in 0 until 6) {
            val axis = k / 2
            val sgn = if (k % 2 == 0) 1f else -1f
            var a = m[3] + sgn * m[axis]
            var b = m[4 + 3] + sgn * m[4 + axis]
            var c = m[8 + 3] + sgn * m[8 + axis]
            var d = m[12 + 3] + sgn * m[12 + axis]
            val len = sqrt(a * a + b * b + c * c)
            if (len > 0f) {
                a /= len; b /= len; c /= len; d /= len
            }
            planes[k * 4] = a
            planes[k * 4 + 1] = b
            planes[k * 4 + 2] = c
            planes[k * 4 + 3] = d
        }
    }

    private fun inFrustum(x: Float, y: Float, z: Float, radius: Float): Boolean {
        for (k in 0 until 6) {
            val dist = planes[k * 4] * x + planes[k * 4 + 1] * y + planes[k * 4 + 2] * z + planes[k * 4 + 3]
            if (dist < -radius) return false
        }
        return true
    }

    // ------------------------------------------------------------------ shaders

    private fun buildProgram(vs: String, fs: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw RuntimeException("Program link failed: $log")
        }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val status = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(s)
            GLES20.glDeleteShader(s)
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }

    companion object {
        private const val LOOK_SENS = 0.0045f

        private const val VERTEX_SRC = """
            uniform mat4 uVP;
            uniform vec3 uOffset;
            attribute vec3 aPos;
            attribute vec3 aColor;
            varying vec3 vColor;
            varying float vDist;
            void main() {
                vec3 p = aPos + uOffset;
                vDist = length(p);
                vColor = aColor;
                gl_Position = uVP * vec4(p, 1.0);
            }
        """

        private const val FRAGMENT_SRC = """
            precision mediump float;
            varying vec3 vColor;
            varying float vDist;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            void main() {
                float f = clamp((uFogEnd - vDist) / (uFogEnd - uFogStart), 0.0, 1.0);
                gl_FragColor = vec4(mix(uFogColor, vColor, f), 1.0);
            }
        """
    }
}
