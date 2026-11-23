package com.example.watermarkeraser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Shows an image and lets the user paint over the region to be removed.
 * The painted mask is kept in image coordinates.
 */
class MaskView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null
    private var maskCanvas: Canvas? = null

    private val toView = Matrix()
    private val toImage = Matrix()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskOverlayPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 150 }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Brush diameter in screen pixels. */
    var brushScreenPx: Float = 40f

    private var lastX = 0f
    private var lastY = 0f
    private var hasMask = false

    fun setImage(bmp: Bitmap) {
        bitmap = bmp
        maskBitmap?.recycle()
        maskBitmap = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        maskCanvas = Canvas(maskBitmap!!)
        hasMask = false
        updateMatrix()
        invalidate()
    }

    fun hasImage() = bitmap != null
    fun hasMask() = hasMask
    fun currentBitmap(): Bitmap? = bitmap

    fun clearMask() {
        maskBitmap?.eraseColor(Color.TRANSPARENT)
        hasMask = false
        invalidate()
    }

    /** Returns the mask as a boolean array (true = remove), row-major in image pixels. */
    fun getMask(): BooleanArray? {
        val m = maskBitmap ?: return null
        val w = m.width
        val h = m.height
        val px = IntArray(w * h)
        m.getPixels(px, 0, w, 0, 0, w, h)
        return BooleanArray(w * h) { Color.alpha(px[it]) > 0 }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateMatrix()
    }

    private fun updateMatrix() {
        val b = bitmap ?: return
        if (width == 0 || height == 0) return
        toView.setRectToRect(
            RectF(0f, 0f, b.width.toFloat(), b.height.toFloat()),
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Matrix.ScaleToFit.CENTER
        )
        toView.invert(toImage)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = bitmap ?: return
        canvas.drawBitmap(b, toView, bitmapPaint)
        maskBitmap?.let { canvas.drawBitmap(it, toView, maskOverlayPaint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val mc = maskCanvas ?: return false
        val pts = floatArrayOf(event.x, event.y)
        toImage.mapPoints(pts)
        val scale = FloatArray(9).also { toView.getValues(it) }[Matrix.MSCALE_X]
        strokePaint.strokeWidth = brushScreenPx / scale

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = pts[0]; lastY = pts[1]
                mc.drawPoint(lastX, lastY, strokePaint)
                hasMask = true
            }
            MotionEvent.ACTION_MOVE -> {
                mc.drawLine(lastX, lastY, pts[0], pts[1], strokePaint)
                lastX = pts[0]; lastY = pts[1]
            }
            MotionEvent.ACTION_UP -> performClick()
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
