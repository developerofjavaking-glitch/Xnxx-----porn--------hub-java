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
    private var uTime = 0
    private var uUnderwater = 0
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
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uUnderwater = GLES20.glGetUniformLocation(program, "uUnderwater")
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

        // Check if camera eye is submerged underwater
        val eyeBx = Math.floor(ex).toInt()
        val eyeBy = Math.floor(ey).toInt()
        val eyeBz = Math.floor(ez).toInt()
        val blockAtEye = world.getBlock(eyeBx, eyeBy, eyeBz)
        val isUnderwater = blockAtEye == Blocks.WATER || (ey < Config.SEA_LEVEL && world.getBlock(eyeBx, Math.floor(ey - 0.4).toInt(), eyeBz) == Blocks.WATER)
        shared.underwater = isUnderwater

        val waterFogR = 0.05f
        val waterFogG = 0.22f
        val waterFogB = 0.40f

        if (isUnderwater) {
            GLES20.glClearColor(waterFogR, waterFogG, waterFogB, 1f)
        } else {
            GLES20.glClearColor(skyR, skyG, skyB, 1f)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uVP, 1, false, vp, 0)
        val fogEnd = Config.RENDER_RADIUS * Config.CHUNK_SIZE - 6f
        if (isUnderwater) {
            GLES20.glUniform3f(uFogColor, waterFogR, waterFogG, waterFogB)
            GLES20.glUniform1f(uFogStart, 0.5f)
            GLES20.glUniform1f(uFogEnd, 20.0f)
            GLES20.glUniform1f(uUnderwater, 1.0f)
        } else {
            GLES20.glUniform3f(uFogColor, skyR, skyG, skyB)
            GLES20.glUniform1f(uFogStart, fogEnd * 0.55f)
            GLES20.glUniform1f(uFogEnd, fogEnd)
            GLES20.glUniform1f(uUnderwater, 0.0f)
        }
        val elapsedSec = (System.nanoTime() / 1_000_000 % 3600000) / 1000f
        GLES20.glUniform1f(uTime, elapsedSec)
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
            varying vec3 vLocalPos;
            varying vec3 vWorldPos;
            void main() {
                vec3 p = aPos + uOffset;
                vDist = length(p);
                vColor = aColor;
                vLocalPos = aPos;
                vWorldPos = p;
                gl_Position = uVP * vec4(p, 1.0);
            }
        """

        private const val FRAGMENT_SRC = """
            precision mediump float;
            varying vec3 vColor;
            varying float vDist;
            varying vec3 vLocalPos;
            varying vec3 vWorldPos;
            uniform vec3 uFogColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uTime;
            uniform float uUnderwater;

            float hash2(vec2 p) {
                p = fract(p * vec2(123.34, 456.21));
                p += dot(p, p + 45.32);
                return fract(p.x * p.y);
            }

            void main() {
                vec3 col = vColor;

                // Detect if current block is foliage / leaves
                bool isLeaves = (vColor.g > 0.28 && vColor.g > vColor.r * 1.25 && vColor.g > vColor.b * 1.25 && vColor.r < 0.35);

                // Detect if current block is water
                bool isWater = (vColor.b > 0.45 && vColor.b > vColor.r * 1.5 && vColor.g > 0.25);

                if (isLeaves) {
                    // Minecraft 16x16 pixel-art leaf texture
                    vec3 bCoord = floor(vLocalPos);
                    vec3 fCoord = fract(vLocalPos);
                    vec2 uv = fract(fCoord.xy + fCoord.yz + fCoord.xz);
                    vec2 pixel = floor(uv * 16.0);
                    float h = hash2(pixel + bCoord.xy * 7.0 + bCoord.z * 13.0);

                    if (h < 0.20) {
                        col *= 0.65; // dark leaf shadow / cutout void
                    } else if (h < 0.48) {
                        col *= 0.85; // deep emerald foliage
                    } else if (h < 0.78) {
                        col *= 1.05; // lush standard foliage
                    } else if (h < 0.94) {
                        col = mix(col * 1.28, vec3(0.35, 0.68, 0.24), 0.4); // bright sunlit leaf edge
                    } else {
                        col = vec3(0.30, 0.46, 0.18); // twig / vein detail
                    }
                } else if (isWater) {
                    // Animated surface wave shimmer
                    float wave = sin(vWorldPos.x * 2.2 + uTime * 2.8) * cos(vWorldPos.z * 2.2 + uTime * 2.2);
                    col += vec3(wave * 0.04, wave * 0.07, wave * 0.10);
                }

                // Underwater atmosphere & effects (Minecraft style)
                if (uUnderwater > 0.5) {
                    // Water light absorption (reds fade out, cool blues & aquas dominate)
                    col.r *= 0.42;
                    col.g = col.g * 0.88 + 0.04;
                    col.b = col.b * 1.15 + 0.08;

                    // Animated dancing sunlight caustics on underwater terrain & seabed
                    float c1 = sin(vWorldPos.x * 1.8 + vWorldPos.z * 1.4 + uTime * 3.2);
                    float c2 = cos(vWorldPos.x * 1.2 - vWorldPos.z * 2.0 + uTime * 2.7);
                    float caustic = max(0.0, c1 * c2) * 0.22;
                    col += vec3(caustic * 0.35, caustic * 0.75, caustic * 1.1);

                    // Ambient ocean tint
                    col = mix(col, vec3(0.05, 0.24, 0.42), 0.18);
                }

                // Distance fog: atmospheric haze in air, dense cyan-blue ocean fog underwater
                float f = clamp((uFogEnd - vDist) / (uFogEnd - uFogStart), 0.0, 1.0);
                gl_FragColor = vec4(mix(uFogColor, col, f), 1.0);
            }
        """
    }
}
