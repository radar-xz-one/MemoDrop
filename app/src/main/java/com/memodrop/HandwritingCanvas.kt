package com.memodrop

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class HandwritingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val prefs = context.getSharedPreferences("MemoDrop", Context.MODE_PRIVATE)

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val ghostPaint = Paint(paint).apply { alpha = 70 }
    private val fillPaint = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true }

    private val strokes = mutableListOf<FloatArray>()
    private val strokeColors = mutableListOf<Int>()
    private val paths = mutableListOf<Path>()
    private val currentPts = ArrayList<Float>()
    private var currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    private var isDragging = false
    private var isAnimating = false
    private var ignoring = false
    private var cover = false
    private var dragStartY = 0f
    private var peel = 0f
    private var tiltDeg = 8f
    private val dragThreshold = resources.displayMetrics.heightPixels * 0.3f

    var penColor = Color.BLACK
    var onTearStartListener: (() -> Unit)? = null

    private val curlEdge = Path()
    private val curlShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F0F0F0")
        setShadowLayer(18f * density, 0f, -4f * density, Color.argb(120, 0, 0, 0))
    }
    private val curlFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val curlLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CFCAC3"); style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val curlArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A9A"); style = Paint.Style.STROKE; strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val curlArrow = Path()
    private var curlTopL = 0f
    private var curlTopR = 0f

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val wf = w.toFloat()
        val hf = h.toFloat()
        val s = 56f * density
        val slant = 10f * density
        curlTopL = hf - s - slant / 2
        curlTopR = hf - s + slant / 2
        curlEdge.apply {
            reset(); moveTo(0f, curlTopL); lineTo(wf, curlTopR); lineTo(wf, hf); lineTo(0f, hf); close()
        }
        curlFillPaint.shader = LinearGradient(
            0f, hf - s, 0f, hf,
            intArrayOf(Color.parseColor("#FBFAF8"), Color.parseColor("#EEEBE7")),
            null, Shader.TileMode.CLAMP
        )
        val cx = wf / 2f
        val cy = hf - s * 0.45f
        curlArrow.apply {
            reset(); moveTo(cx - 18f, cy + 8f); lineTo(cx, cy - 8f); lineTo(cx + 18f, cy + 8f)
        }
    }

    private fun drawPage(canvas: Canvas, ghost: Boolean) {
        if (ghost) {
            canvas.drawColor(Color.parseColor("#F3F1EE"))
            if (!cover) paths.forEachIndexed { i, path ->
                ghostPaint.color = strokeColors[i]
                ghostPaint.alpha = 70
                canvas.drawPath(path, ghostPaint)
            }
        } else if (cover) {
            drawCover(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
            paths.forEachIndexed { i, path ->
                paint.color = strokeColors[i]
                canvas.drawPath(path, paint)
            }
            paint.color = penColor
            canvas.drawPath(currentPath, paint)
        }
    }

    private fun halfPlane(px: Float, py: Float, side: Float): Path {
        val l = 6000f
        val rad = Math.toRadians(tiltDeg.toDouble())
        val ux = cos(rad).toFloat()
        val uy = sin(rad).toFloat()
        val nx = -uy * side
        val ny = ux * side
        return Path().apply {
            moveTo(px - ux * l, py - uy * l)
            lineTo(px + ux * l, py + uy * l)
            lineTo(px + ux * l + nx * l, py + uy * l + ny * l)
            lineTo(px - ux * l + nx * l, py - uy * l + ny * l)
            close()
        }
    }

    private fun drawCornerCurl(canvas: Canvas) {
        canvas.drawPath(curlEdge, curlShadowPaint)
        canvas.drawPath(curlEdge, curlFillPaint)
        canvas.drawLine(0f, curlTopL, width.toFloat(), curlTopR, curlLinePaint)
        canvas.drawPath(curlArrow, curlArrowPaint)
    }

    private fun drawFoldShade(canvas: Canvas, px: Float, py: Float, length: Float, alpha: Int) {
        canvas.save()
        canvas.rotate(tiltDeg, px, py)
        fillPaint.shader = LinearGradient(0f, py, 0f, py - length, Color.argb(alpha, 0, 0, 0), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(-6000f, py - length, 6000f, py, fillPaint)
        fillPaint.shader = null
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        if (peel <= 0f) {
            drawPage(canvas, false)
            if (!cover) drawCornerCurl(canvas)
            return
        }

        canvas.drawColor(if (cover) Color.WHITE else Color.parseColor("#D9D9D9"))

        val px = width / 2f
        val py = height - peel

        drawFoldShade(canvas, px, py, 90f, 110)

        // 手前に残る紙
        canvas.save()
        canvas.clipPath(halfPlane(px, py, -1f))
        drawPage(canvas, false)
        canvas.restore()

        // めくれて裏返った紙
        canvas.save()
        canvas.clipPath(halfPlane(px, py, -1f))
        canvas.concat(Matrix().apply {
            postTranslate(-px, -py)
            postRotate(tiltDeg)
            postScale(1f, -1f)
            postRotate(-tiltDeg)
            postTranslate(px, py)
        })
        canvas.clipPath(halfPlane(px, py, 1f))
        drawPage(canvas, true)
        canvas.restore()

        // 裏返った紙の折り目付近のハイライト
        drawFoldShade(canvas, px, py, 40f, 90)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return false

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (y > height - 66f * density) {
                    isDragging = true
                    dragStartY = y
                    peel = 0f
                    tiltDeg = if (x < width / 2f) -8f else 8f
                } else if (cover) {
                    ignoring = true
                } else {
                    currentPath = Path().apply { moveTo(x, y) }
                    currentPts.clear()
                    currentPts.add(x); currentPts.add(y)
                    lastX = x
                    lastY = y
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    peel = (dragStartY - y).coerceAtLeast(0f)
                    invalidate()
                } else if (!ignoring && (abs(x - lastX) >= 1f || abs(y - lastY) >= 1f)) {
                    currentPath.quadTo(lastX, lastY, (lastX + x) / 2, (lastY + y) / 2)
                    lastX = x
                    lastY = y
                    currentPts.add(x); currentPts.add(y)
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    if (peel >= dragThreshold) performTearAnimation() else animatePeel(peel, 0f, 150, null)
                } else if (ignoring) {
                    ignoring = false
                } else {
                    finishStroke()
                }
            }

            else -> return super.onTouchEvent(event)
        }
        return true
    }

    private fun finishStroke() {
        if (currentPts.size == 2) {
            currentPts.add(currentPts[0] + 0.1f); currentPts.add(currentPts[1])
            currentPath = buildPath(currentPts.toFloatArray())
        } else if (currentPts.size > 2) {
            currentPath.lineTo(lastX, lastY)
        }
        if (currentPts.size >= 4) {
            strokes.add(currentPts.toFloatArray())
            strokeColors.add(penColor)
            paths.add(currentPath)
            saveToStorage()
        }
        currentPath = Path()
        currentPts.clear()
        invalidate()
    }

    private fun animatePeel(from: Float, to: Float, duration: Long, onEnd: (() -> Unit)?) {
        isAnimating = true
        ValueAnimator.ofFloat(from, to).apply {
            this.duration = duration
            if (onEnd != null) interpolator = AccelerateInterpolator(1.2f)
            addUpdateListener { peel = it.animatedValue as Float; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun performTearAnimation(duration: Long = 450) {
        onTearStartListener?.invoke()
        animatePeel(peel, height * 1.25f, duration) {
            cover = false
            clearCanvas()
            peel = 0f
            invalidate()
        }
    }

    private fun clearCanvas() {
        paths.clear()
        strokes.clear()
        strokeColors.clear()
        currentPath = Path()
        currentPts.clear()
        invalidate()
        saveToStorage()
    }

    private fun saveToStorage() {
        val arr = JSONArray()
        strokes.forEachIndexed { i, st ->
            arr.put(JSONObject().put("c", strokeColors[i]).put("p", JSONArray().also { a -> st.forEach { a.put(it.toDouble()) } }))
        }
        prefs.edit().putString("canvas_data", arr.toString()).apply()
    }

    fun loadFromStorage() {
        val data = prefs.getString("canvas_data", null) ?: return
        try {
            val arr = JSONArray(data)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                val a = obj?.getJSONArray("p") ?: arr.getJSONArray(i)
                val pts = FloatArray(a.length()) { a.getDouble(it).toFloat() }
                if (pts.size >= 4) {
                    strokes.add(pts)
                    strokeColors.add(obj?.optInt("c", Color.BLACK) ?: Color.BLACK)
                    paths.add(buildPath(pts))
                }
            }
        } catch (e: Exception) {
            strokes.clear()
            strokeColors.clear()
            paths.clear()
        }
        invalidate()
    }

    private fun buildPath(pts: FloatArray): Path {
        val p = Path()
        p.moveTo(pts[0], pts[1])
        if (pts.size == 4) {
            p.lineTo(pts[2], pts[3])
            return p
        }
        var lx = pts[0]
        var ly = pts[1]
        var i = 2
        while (i + 1 < pts.size) {
            p.quadTo(lx, ly, (lx + pts[i]) / 2, (ly + pts[i + 1]) / 2)
            lx = pts[i]
            ly = pts[i + 1]
            i += 2
        }
        p.lineTo(lx, ly)
        return p
    }

    fun hasMemo() = strokes.isNotEmpty()

    fun startIntro() {
        cover = true
        invalidate()
        postDelayed({
            if (cover && !isAnimating && !isDragging) {
                tiltDeg = 8f
                peel = 0f
                performTearAnimation(1100)
            }
        }, 1800)
    }

    private fun drawCover(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val d = density
        canvas.drawColor(Color.parseColor("#2F6F8F"))
        canvas.drawRect(0f, 0f, 14f * d, h, fillPaint.apply { color = Color.argb(40, 255, 255, 255) })

        canvas.save()
        canvas.translate(w / 2f, h * 0.40f)
        canvas.rotate(-32f)
        val len = w
        val th = len * 0.15f
        val x0 = -len / 2f
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3f * d; color = Color.parseColor("#1B2A33"); strokeJoin = Paint.Join.ROUND
        }
        fun fill(c: String) = fillPaint.apply { color = Color.parseColor(c) }
        fun rect(r: RectF, c: String) {
            canvas.drawRect(r, fill(c)); canvas.drawRect(r, outline)
        }

        val eraser = RectF(x0, -th / 2, x0 + len * 0.08f, th / 2)
        canvas.drawRoundRect(eraser, th * 0.15f, th * 0.15f, fill("#E8456B"))
        canvas.drawRoundRect(eraser, th * 0.15f, th * 0.15f, outline)
        val ferrule = RectF(x0 + len * 0.08f, -th / 2, x0 + len * 0.15f, th / 2)
        rect(ferrule, "#B8BEC4")
        for (k in 1..2) {
            val fx = ferrule.left + ferrule.width() * k / 3f
            canvas.drawLine(fx, -th / 2, fx, th / 2, outline)
        }
        val body = RectF(ferrule.right, -th / 2, ferrule.right + len * 0.62f, th / 2)
        rect(body, "#F6B400")
        canvas.drawRect(body.left, -th / 2, body.right, -th * 0.18f, fill("#FFD23F"))
        canvas.drawRect(body.left, th * 0.22f, body.right, th / 2, fill("#D48A00"))
        canvas.drawRect(body, outline)

        val tipX = body.right
        val tipEnd = x0 + len
        val wood = Path().apply { moveTo(tipX, -th / 2); lineTo(tipEnd, 0f); lineTo(tipX, th / 2); close() }
        canvas.drawPath(wood, fill("#F0C48A")); canvas.drawPath(wood, outline)
        val leadStart = tipX + (tipEnd - tipX) * 0.62f
        val lead = Path().apply { moveTo(leadStart, -th * 0.19f); lineTo(tipEnd, 0f); lineTo(leadStart, th * 0.19f); close() }
        canvas.drawPath(lead, fill("#2B2B2B"))
        canvas.restore()

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = Color.WHITE }
        text.textSize = 34f * d
        text.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(context.getString(R.string.app_name), w / 2f, h * 0.68f, text)
        text.textSize = 15f * d
        text.typeface = Typeface.DEFAULT
        text.color = Color.argb(230, 255, 255, 255)
        canvas.drawText(context.getString(R.string.intro_hint), w / 2f, h * 0.68f + 34f * d, text)
        canvas.drawText("\u2191", w / 2f, h - 28f * d, text)
    }
}
