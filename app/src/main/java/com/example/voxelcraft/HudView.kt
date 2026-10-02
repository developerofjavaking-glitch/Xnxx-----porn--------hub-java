package com.example.voxelcraft

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/** Touch controls drawn on top of the GL view: joystick, look area, buttons, crosshair, stats. */
class HudView(context: Context, private val shared: Shared) : View(context) {

    private class Btn(val id: Int) {
        var cx = 0f
        var cy = 0f
        var r = 0f
        var pointer = -1
    }

    private companion object {
        const val JUMP = 0
        const val DOWN = 1
        const val BREAK = 2
        const val PLACE = 3
        const val FLY = 4
        const val BLOCK = 5
    }

    private val buttons = listOf(
        Btn(JUMP), Btn(DOWN), Btn(BREAK),
        Btn(PLACE), Btn(FLY), Btn(BLOCK)
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(4f, 1f, 1f, Color.BLACK)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val symbolPath = Path()
    private val symbolRect = RectF()

    private val logo: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.logo)
    private val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val logoRect = RectF()
    private val startTime = SystemClock.uptimeMillis()

    private var unit = 1f
    private var joyPointer = -1
    private var joyOx = 0f
    private var joyOy = 0f
    private var joyX = 0f
    private var joyY = 0f
    private var lookPointer = -1
    private var lastX = 0f
    private var lastY = 0f

    private val repeater = object : Runnable {
        override fun run() {
            var any = false
            for (b in buttons) {
                if (b.pointer == -1) continue
                if (b.id == BREAK) {
                    shared.input.actions.add(Input.ACT_BREAK); any = true
                } else if (b.id == PLACE) {
                    shared.input.actions.add(Input.ACT_PLACE); any = true
                }
            }
            if (any) postDelayed(this, 250)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        unit = min(w, h) / 10f
        val u = unit
        fun place(id: Int, x: Float, y: Float, r: Float) {
            val b = buttons[id]
            b.cx = x; b.cy = y; b.r = r
        }
        place(JUMP, w - 2.4f * u, h - 2.4f * u, 1.3f * u)
        place(BREAK, w - 5.2f * u, h - 2.0f * u, 1.0f * u)
        place(PLACE, w - 4.2f * u, h - 4.8f * u, 1.0f * u)
        place(DOWN, w - 1.6f * u, h - 5.2f * u, 0.8f * u)
        place(FLY, w - 1.5f * u, 1.3f * u, 0.9f * u)
        place(BLOCK, w - 3.7f * u, 1.3f * u, 0.9f * u)
    }

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                onDown(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) onMove(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                onUp(e.getPointerId(e.actionIndex))
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until e.pointerCount) onUp(e.getPointerId(i))
            }
        }
        return true
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        for (b in buttons) {
            if (hypot(x - b.cx, y - b.cy) <= b.r * 1.1f && b.pointer == -1) {
                b.pointer = id
                when (b.id) {
                    JUMP -> shared.input.jump = true
                    DOWN -> shared.input.down = true
                    BREAK -> startRepeat(Input.ACT_BREAK)
                    PLACE -> startRepeat(Input.ACT_PLACE)
                    FLY -> shared.input.actions.add(Input.ACT_FLY)
                    BLOCK -> shared.input.actions.add(Input.ACT_BLOCK)
                }
                return
            }
        }
        if (x < width * 0.4f && joyPointer == -1) {
            joyPointer = id
            joyOx = x; joyOy = y
            joyX = x; joyY = y
        } else if (lookPointer == -1) {
            lookPointer = id
            lastX = x; lastY = y
        }
    }

    private fun startRepeat(action: Int) {
        shared.input.actions.add(action)
        removeCallbacks(repeater)
        postDelayed(repeater, 350)
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        if (id == joyPointer) {
            val radius = unit * 1.5f
            var dx = x - joyOx
            var dy = y - joyOy
            val len = hypot(dx, dy)
            if (len > radius) {
                dx = dx / len * radius
                dy = dy / len * radius
            }
            joyX = joyOx + dx
            joyY = joyOy + dy
            shared.input.moveX = dx / radius
            shared.input.moveY = -dy / radius
        } else if (id == lookPointer) {
            shared.input.addLook(x - lastX, y - lastY)
            lastX = x; lastY = y
        }
    }

    private fun onUp(id: Int) {
        if (id == joyPointer) {
            joyPointer = -1
            shared.input.moveX = 0f
            shared.input.moveY = 0f
        }
        if (id == lookPointer) lookPointer = -1
        for (b in buttons) {
            if (b.pointer == id) {
                b.pointer = -1
                if (b.id == JUMP) shared.input.jump = false
                if (b.id == DOWN) shared.input.down = false
            }
        }
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        val u = unit
        val w = width.toFloat()
        val h = height.toFloat()

        // stats
        textPaint.textSize = u * 0.42f
        var ty = u * 0.8f
        for (line in shared.hudText.split("\n")) {
            canvas.drawText(line, u * 0.4f, ty, textPaint)
            ty += u * 0.5f
        }

        // crosshair
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.argb(200, 255, 255, 255)
        canvas.drawLine(w / 2 - u * 0.35f, h / 2, w / 2 + u * 0.35f, h / 2, paint)
        canvas.drawLine(w / 2, h / 2 - u * 0.35f, w / 2, h / 2 + u * 0.35f, paint)

        // selected block
        val sel = Blocks.palette[shared.blockIndex % Blocks.palette.size]
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(255, (Blocks.top[sel] shr 16) and 255, (Blocks.top[sel] shr 8) and 255, Blocks.top[sel] and 255)
        canvas.drawRect(w / 2 - u * 0.4f, u * 0.3f, w / 2 + u * 0.4f, u * 1.1f, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        canvas.drawRect(w / 2 - u * 0.4f, u * 0.3f, w / 2 + u * 0.4f, u * 1.1f, paint)
        textPaint.textSize = u * 0.4f
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(Blocks.names[sel], w / 2, u * 1.6f, textPaint)

        // joystick
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        if (joyPointer != -1) {
            paint.color = Color.argb(110, 255, 255, 255)
            canvas.drawCircle(joyOx, joyOy, u * 1.5f, paint)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(140, 255, 255, 255)
            canvas.drawCircle(joyX, joyY, u * 0.6f, paint)
        } else {
            paint.color = Color.argb(50, 255, 255, 255)
            canvas.drawCircle(u * 2.6f, h - u * 2.8f, u * 1.5f, paint)
        }

        // buttons
        for (b in buttons) {
            val active = b.pointer != -1 || (b.id == FLY && shared.flying)
            paint.style = Paint.Style.FILL
            paint.color = if (active) Color.argb(170, 70, 130, 240) else Color.argb(85, 255, 255, 255)
            canvas.drawCircle(b.cx, b.cy, b.r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3.5f
            paint.color = if (active) Color.argb(220, 100, 180, 255) else Color.argb(170, 255, 255, 255)
            canvas.drawCircle(b.cx, b.cy, b.r, paint)
            drawButtonSymbol(canvas, b)
        }
        textPaint.textAlign = Paint.Align.LEFT

        drawLogo(canvas, w, h)
        drawUnderwaterHud(canvas, w, h, u)
        postInvalidateOnAnimation()
    }

    private fun drawUnderwaterHud(canvas: Canvas, w: Float, h: Float, u: Float) {
        if (!shared.underwater) return

        // 1. Soft aquatic screen border vignette
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = u * 0.45f
        paint.color = Color.argb(45, 20, 140, 220)
        canvas.drawRect(0f, 0f, w, h, paint)

        // 2. Minecraft-style 10 oxygen breath bubbles
        val timeMs = SystemClock.uptimeMillis()
        val numBubbles = 10
        val bubbleR = u * 0.14f
        val bubbleSpacing = bubbleR * 2.3f
        val totalWidth = numBubbles * bubbleSpacing
        val startX = (w - totalWidth) / 2f + bubbleR
        val bubbleY = h / 2f - u * 0.9f

        for (i in 0 until numBubbles) {
            val bx = startX + i * bubbleSpacing
            val wobbleY = bubbleY + kotlin.math.sin((timeMs / 180.0) + i).toFloat() * (u * 0.03f)

            // Outer bubble outline
            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = 2.5f
            iconPaint.color = Color.argb(220, 160, 230, 255)
            canvas.drawCircle(bx, wobbleY, bubbleR, iconPaint)

            // Bubble body
            iconFillPaint.color = Color.argb(80, 70, 170, 240)
            canvas.drawCircle(bx, wobbleY, bubbleR, iconFillPaint)

            // Specular shine dot
            iconFillPaint.color = Color.argb(230, 255, 255, 255)
            canvas.drawCircle(bx - bubbleR * 0.35f, wobbleY - bubbleR * 0.35f, bubbleR * 0.28f, iconFillPaint)
        }

        // 3. Gentle rising water bubbles in view
        for (i in 0 until 6) {
            val speed = 0.0003f + (i % 3) * 0.0001f
            val cycle = (timeMs * speed + i * 0.17f) % 1.0f
            val py = h * (1.0f - cycle)
            val px = (w * 0.2f) + ((i * 197 % 1000) / 1000f) * (w * 0.6f) + kotlin.math.sin(timeMs * 0.003 + i).toFloat() * 15f
            val r = (u * 0.06f) + (i % 3) * (u * 0.04f)

            iconPaint.style = Paint.Style.STROKE
            iconPaint.strokeWidth = 2f
            iconPaint.color = Color.argb(140, 180, 240, 255)
            canvas.drawCircle(px, py, r, iconPaint)

            iconFillPaint.color = Color.argb(50, 100, 200, 255)
            canvas.drawCircle(px, py, r, iconFillPaint)
        }
    }

    private fun drawButtonSymbol(canvas: Canvas, b: Btn) {
        val cx = b.cx
        val cy = b.cy
        val r = b.r
        when (b.id) {
            JUMP -> {
                val s = r * 0.44f
                iconPaint.strokeWidth = r * 0.13f
                iconPaint.color = Color.WHITE
                iconPaint.style = Paint.Style.STROKE
                // Up arrow shaft
                canvas.drawLine(cx, cy + s * 0.45f, cx, cy - s * 0.6f, iconPaint)
                // Arrowhead
                symbolPath.reset()
                symbolPath.moveTo(cx - s * 0.55f, cy - s * 0.1f)
                symbolPath.lineTo(cx, cy - s * 0.65f)
                symbolPath.lineTo(cx + s * 0.55f, cy - s * 0.1f)
                canvas.drawPath(symbolPath, iconPaint)
                // Jump ground line
                canvas.drawLine(cx - s * 0.55f, cy + s * 0.75f, cx + s * 0.55f, cy + s * 0.75f, iconPaint)
            }
            DOWN -> {
                val s = r * 0.44f
                iconPaint.strokeWidth = r * 0.13f
                iconPaint.color = Color.WHITE
                iconPaint.style = Paint.Style.STROKE
                // Down arrow shaft
                canvas.drawLine(cx, cy - s * 0.45f, cx, cy + s * 0.6f, iconPaint)
                // Arrowhead
                symbolPath.reset()
                symbolPath.moveTo(cx - s * 0.55f, cy + s * 0.1f)
                symbolPath.lineTo(cx, cy + s * 0.65f)
                symbolPath.lineTo(cx + s * 0.55f, cy + s * 0.1f)
                canvas.drawPath(symbolPath, iconPaint)
                // Top barrier line
                canvas.drawLine(cx - s * 0.55f, cy - s * 0.75f, cx + s * 0.55f, cy - s * 0.75f, iconPaint)
            }
            BREAK -> {
                // Pickaxe icon
                val s = r * 0.48f
                // Wood handle
                iconPaint.strokeWidth = r * 0.12f
                iconPaint.color = Color.rgb(225, 185, 130)
                iconPaint.style = Paint.Style.STROKE
                canvas.drawLine(cx - s * 0.5f, cy + s * 0.55f, cx + s * 0.3f, cy - s * 0.25f, iconPaint)
                // Metal curved pickaxe head
                iconPaint.strokeWidth = r * 0.14f
                iconPaint.color = Color.WHITE
                symbolPath.reset()
                symbolPath.moveTo(cx - s * 0.35f, cy - s * 0.65f)
                symbolPath.quadTo(cx + s * 0.35f, cy - s * 0.35f, cx + s * 0.65f, cy + s * 0.35f)
                canvas.drawPath(symbolPath, iconPaint)
            }
            PLACE -> {
                // Block with + symbol
                val s = r * 0.44f
                iconPaint.strokeWidth = r * 0.11f
                iconPaint.color = Color.WHITE
                iconPaint.style = Paint.Style.STROKE
                symbolRect.set(cx - s * 0.75f, cy - s * 0.75f, cx + s * 0.75f, cy + s * 0.75f)
                canvas.drawRoundRect(symbolRect, s * 0.22f, s * 0.22f, iconPaint)
                // Bold Plus inside
                iconPaint.strokeWidth = r * 0.15f
                canvas.drawLine(cx - s * 0.42f, cy, cx + s * 0.42f, cy, iconPaint)
                canvas.drawLine(cx, cy - s * 0.42f, cx, cy + s * 0.42f, iconPaint)
            }
            FLY -> {
                // Wings symbol
                val s = r * 0.52f
                iconPaint.style = Paint.Style.STROKE
                iconPaint.strokeWidth = r * 0.12f
                iconPaint.color = if (shared.flying) Color.rgb(255, 235, 100) else Color.WHITE
                // Left wing
                symbolPath.reset()
                symbolPath.moveTo(cx - s * 0.15f, cy + s * 0.2f)
                symbolPath.cubicTo(cx - s * 0.4f, cy - s * 0.15f, cx - s * 0.75f, cy - s * 0.4f, cx - s * 0.85f, cy - s * 0.65f)
                symbolPath.cubicTo(cx - s * 0.55f, cy - s * 0.35f, cx - s * 0.3f, cy - s * 0.1f, cx - s * 0.15f, cy)
                canvas.drawPath(symbolPath, iconPaint)
                // Right wing
                symbolPath.reset()
                symbolPath.moveTo(cx + s * 0.15f, cy + s * 0.2f)
                symbolPath.cubicTo(cx + s * 0.4f, cy - s * 0.15f, cx + s * 0.75f, cy - s * 0.4f, cx + s * 0.85f, cy - s * 0.65f)
                symbolPath.cubicTo(cx + s * 0.55f, cy - s * 0.35f, cx + s * 0.3f, cy - s * 0.1f, cx + s * 0.15f, cy)
                canvas.drawPath(symbolPath, iconPaint)
                // Center body
                iconFillPaint.color = iconPaint.color
                canvas.drawCircle(cx, cy, s * 0.16f, iconFillPaint)
            }
            BLOCK -> {
                // Isometric 3D Voxel block preview
                val sel = Blocks.palette[shared.blockIndex % Blocks.palette.size]
                val s = r * 0.44f

                // Top face
                symbolPath.reset()
                symbolPath.moveTo(cx, cy - s * 0.7f)
                symbolPath.lineTo(cx + s * 0.6f, cy - s * 0.35f)
                symbolPath.lineTo(cx, cy)
                symbolPath.lineTo(cx - s * 0.6f, cy - s * 0.35f)
                symbolPath.close()
                val topCol = Blocks.top[sel]
                iconFillPaint.color = Color.rgb((topCol shr 16) and 255, (topCol shr 8) and 255, topCol and 255)
                canvas.drawPath(symbolPath, iconFillPaint)

                // Left face
                symbolPath.reset()
                symbolPath.moveTo(cx - s * 0.6f, cy - s * 0.35f)
                symbolPath.lineTo(cx, cy)
                symbolPath.lineTo(cx, cy + s * 0.65f)
                symbolPath.lineTo(cx - s * 0.6f, cy + s * 0.3f)
                symbolPath.close()
                val sideCol = Blocks.side[sel]
                val sr = ((sideCol shr 16) and 255) * 0.85f
                val sg = ((sideCol shr 8) and 255) * 0.85f
                val sb = (sideCol and 255) * 0.85f
                iconFillPaint.color = Color.rgb(sr.toInt(), sg.toInt(), sb.toInt())
                canvas.drawPath(symbolPath, iconFillPaint)

                // Right face
                symbolPath.reset()
                symbolPath.moveTo(cx + s * 0.6f, cy - s * 0.35f)
                symbolPath.lineTo(cx, cy)
                symbolPath.lineTo(cx, cy + s * 0.65f)
                symbolPath.lineTo(cx + s * 0.6f, cy + s * 0.3f)
                symbolPath.close()
                val dr = ((sideCol shr 16) and 255) * 0.65f
                val dg = ((sideCol shr 8) and 255) * 0.65f
                val db = (sideCol and 255) * 0.65f
                iconFillPaint.color = Color.rgb(dr.toInt(), dg.toInt(), db.toInt())
                canvas.drawPath(symbolPath, iconFillPaint)

                // Outer wireframe edges
                iconPaint.strokeWidth = 2.5f
                iconPaint.color = Color.WHITE
                iconPaint.style = Paint.Style.STROKE
                symbolPath.reset()
                symbolPath.moveTo(cx, cy - s * 0.7f)
                symbolPath.lineTo(cx + s * 0.6f, cy - s * 0.35f)
                symbolPath.lineTo(cx + s * 0.6f, cy + s * 0.3f)
                symbolPath.lineTo(cx, cy + s * 0.65f)
                symbolPath.lineTo(cx - s * 0.6f, cy + s * 0.3f)
                symbolPath.lineTo(cx - s * 0.6f, cy - s * 0.35f)
                symbolPath.close()
                canvas.drawPath(symbolPath, iconPaint)

                // Inner Y-lines connecting to center
                canvas.drawLine(cx, cy, cx, cy + s * 0.65f, iconPaint)
                canvas.drawLine(cx, cy, cx - s * 0.6f, cy - s * 0.35f, iconPaint)
                canvas.drawLine(cx, cy, cx + s * 0.6f, cy - s * 0.35f, iconPaint)
            }
        }
    }

    /** Title logo: fully visible for 2.5 s, then fades out over 1 s. */
    private fun drawLogo(canvas: Canvas, w: Float, h: Float) {
        val bmp = logo ?: return
        val t = SystemClock.uptimeMillis() - startTime
        val alpha = when {
            t < 2500 -> 255
            t < 3500 -> (255 - (t - 2500) * 255 / 1000).toInt()
            else -> return
        }
        val lw = min(w * 0.55f, h * 1.3f)
        val lh = lw * bmp.height / bmp.width
        logoRect.set(w / 2 - lw / 2, h * 0.5f - lh / 2 - h * 0.05f, w / 2 + lw / 2, h * 0.5f + lh / 2 - h * 0.05f)
        logoPaint.alpha = alpha
        canvas.drawBitmap(bmp, null, logoRect, logoPaint)
    }
}
