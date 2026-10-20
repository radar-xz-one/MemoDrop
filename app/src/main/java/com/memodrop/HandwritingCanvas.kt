package com.memodrop

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.Transformation
import kotlin.math.abs
import kotlin.math.sqrt

class HandwritingCanvas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val paths = mutableListOf<Path>()
    private var currentPath = Path()
    private var lastX = 0f
    private var lastY = 0f

    // めくり操作用
    private var isDragging = false
    private var dragStartY = 0f
    private var currentDragY = 0f
    private var dragOffsetY = 0f
    private val dragThreshold = (resources.displayMetrics.heightPixels * 0.3f).toFloat()
    private val minFlingVelocity = 1000f // px/sec

    // 削除リスナー
    var onDeleteListener: (() -> Unit)? = null

    // アニメーション用
    private var isAnimating = false
    private var animationProgress = 0f
    private var animationDirection = 1f // 1: 左上, -1: 右上

    // 初回起動演出用
    private var isTutorial = false
    private var tutorialDelay = 0L

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 背景は白
        canvas.drawColor(Color.WHITE)

        // キャンバス全体をドラッグオフセットで移動
        canvas.save()
        canvas.translate(0f, dragOffsetY)

        // 全てのパスを描画
        for (path in paths) {
            canvas.drawPath(path, paint)
        }
        canvas.drawPath(currentPath, paint)

        canvas.restore()

        // 下部のめくりエリアを描画
        drawCorner(canvas)
    }

    private fun drawCorner(canvas: Canvas) {
        val cornerPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val shadowPaint = Paint().apply {
            color = Color.parseColor("#C0C0C0")
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val width = width.toFloat()
        val height = height.toFloat()
        val cornerHeight = 60f
        val cornerWidth = 80f

        // 影
        canvas.drawRect(0f, height - cornerHeight + 4f, width, height + 4f, shadowPaint)

        // めくりエリア
        val cornerPath = Path().apply {
            moveTo(0f, height - cornerHeight)
            lineTo(width, height - cornerHeight)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        canvas.drawPath(cornerPath, cornerPaint)

        // めくり効果（ドラッグ中）
        if (isDragging && dragOffsetY < 0) {
            val foldPaint = Paint().apply {
                color = Color.parseColor("#F0F0F0")
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRect(0f, height - cornerHeight + dragOffsetY, width, height + dragOffsetY, foldPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isAnimating) return false

        val x = event.x
        val y = event.y
        val height = height.toFloat()

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 下部30%のエリアでのみドラッグ開始
                if (y > height * 0.7f) {
                    isDragging = true
                    dragStartY = y
                    currentDragY = y
                    dragOffsetY = 0f
                    return true
                }
                // 上部70%では手書き
                else {
                    currentPath = Path()
                    currentPath.moveTo(x, y)
                    lastX = x
                    lastY = y
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    currentDragY = y
                    val deltaY = y - dragStartY
                    dragOffsetY = if (deltaY < 0) deltaY else 0f // 上方向のみ
                    invalidate()
                } else {
                    // 手書き中
                    val deltaX = abs(x - lastX)
                    val deltaY = abs(y - lastY)
                    if (deltaX >= 3 || deltaY >= 3) {
                        currentPath.quadTo(lastX, lastY, (lastX + x) / 2, (lastY + y) / 2)
                        lastX = x
                        lastY = y
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val dragDistance = abs(dragOffsetY)

                    // 閾値を超えていれば削除
                    if (dragDistance >= dragThreshold) {
                        performTearAnimation()
                    } else {
                        // 元に戻す
                        dragOffsetY = 0f
                        invalidate()
                    }
                } else {
                    // 手書き終了
                    if (currentPath.isEmpty.not()) {
                        paths.add(currentPath)
                        saveToStorage()
                    }
                    currentPath = Path()
                }
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    private fun performTearAnimation() {
        isAnimating = true
        animationProgress = 0f
        animationDirection = if (Math.random() > 0.5) 1f else -1f

        val anim = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                animationProgress = interpolatedTime
                dragOffsetY = -(height * interpolatedTime * 1.5f).toFloat()
                invalidate()
            }
        }
        anim.duration = 300
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {
                // 効果音再生（MainActivityで設定）
            }

            override fun onAnimationEnd(animation: Animation) {
                clearCanvas()
                isAnimating = false
                dragOffsetY = 0f
                invalidate()
                onDeleteListener?.invoke()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })
        startAnimation(anim)
    }

    fun clearCanvas() {
        paths.clear()
        currentPath = Path()
        invalidate()
        saveToStorage()
    }

    // ローカルストレージに保存
    private fun saveToStorage() {
        val prefs = context.getSharedPreferences("MemoDrop", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("canvas_data", serializePaths())
        editor.apply()
    }

    // ローカルストレージから読み込み
    fun loadFromStorage() {
        val prefs = context.getSharedPreferences("MemoDrop", Context.MODE_PRIVATE)
        val data = prefs.getString("canvas_data", null)
        if (data != null) {
            deserializePaths(data)
            invalidate()
        }
    }

    private fun serializePaths(): String {
        // 簡易シリアライズ（実プロジェクトではもっと堅牢な方法を推奨）
        val sb = StringBuilder()
        for (path in paths) {
            sb.append(path.toString()).append("|")
        }
        return sb.toString()
    }

    private fun deserializePaths(data: String) {
        paths.clear()
        val parts = data.split("|")
        for (part in parts) {
            if (part.isNotEmpty()) {
                val path = Path()
                // 簡易デシリアライズ（実プロジェクトでは改善が必要）
                paths.add(path)
            }
        }
    }

    // 初回起動演出：ランダムな落書きを描画
    fun drawTutorialDoodle() {
        isTutorial = true
        val random = java.util.Random()

        // 波線
        val wavePath = Path()
        wavePath.moveTo(100f, 200f)
        for (i in 0..10) {
            wavePath.quadTo(
                100f + i * 30f + random.nextFloat() * 20f,
                200f + random.nextFloat() * 40f - 20f,
                100f + (i + 1) * 30f,
                200f
            )
        }
        paths.add(wavePath)

        // 丸
        val circlePath = Path()
        circlePath.addCircle(200f, 350f, 30f, Path.Direction.CW)
        paths.add(circlePath)

        // チェックマーク
        val checkPath = Path()
        checkPath.moveTo(150f, 450f)
        checkPath.lineTo(200f, 500f)
        checkPath.lineTo(280f, 420f)
        paths.add(checkPath)

        invalidate()
        saveToStorage()
    }

    // 初回起動演出：自動削除
    fun performTutorialTear() {
        if (isTutorial) {
            isTutorial = false
            performTearAnimation()
        }
    }
}
