package com.example.ocr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val cornerPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
    }
    private val fillPaint = Paint().apply {
        color = Color.parseColor("#44FF0000")
        style = Paint.Style.FILL
    }

    var isCropEnabled: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var cropRectBitmapContent: RectF? = null
    
    // Interaction states
    private enum class DragState {
        NONE, DRAWING_NEW, DRAG_CENTER, DRAG_TL, DRAG_TR, DRAG_BL, DRAG_BR, DRAG_LEFT, DRAG_TOP, DRAG_RIGHT, DRAG_BOTTOM
    }
    
    private var dragState = DragState.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    // Temporarily holds screen coordinates of the rect
    private var currentScreenRect: RectF = RectF()
    private val touchRadius = 50f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isCropEnabled || drawable == null) return super.onTouchEvent(event)

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
                
                if (cropRectBitmapContent != null) {
                    updateCurrentScreenRect()
                    val r = currentScreenRect
                    
                    // Hit testing corners
                    if (isHit(x, y, r.left, r.top)) dragState = DragState.DRAG_TL
                    else if (isHit(x, y, r.right, r.top)) dragState = DragState.DRAG_TR
                    else if (isHit(x, y, r.left, r.bottom)) dragState = DragState.DRAG_BL
                    else if (isHit(x, y, r.right, r.bottom)) dragState = DragState.DRAG_BR
                    else if (isHit(x, y, r.left, (r.top + r.bottom) / 2)) dragState = DragState.DRAG_LEFT
                    else if (isHit(x, y, r.right, (r.top + r.bottom) / 2)) dragState = DragState.DRAG_RIGHT
                    else if (isHit(x, y, (r.left + r.right) / 2, r.top)) dragState = DragState.DRAG_TOP
                    else if (isHit(x, y, (r.left + r.right) / 2, r.bottom)) dragState = DragState.DRAG_BOTTOM
                    else if (r.contains(x, y)) dragState = DragState.DRAG_CENTER
                    else {
                        // Clicked outside, draw new
                        dragState = DragState.DRAWING_NEW
                        currentScreenRect.set(x, y, x, y)
                    }
                } else {
                    dragState = DragState.DRAWING_NEW
                    currentScreenRect.set(x, y, x, y)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastTouchX
                val dy = y - lastTouchY
                
                when (dragState) {
                    DragState.DRAWING_NEW -> {
                        currentScreenRect.right = x
                        currentScreenRect.bottom = y
                    }
                    DragState.DRAG_CENTER -> currentScreenRect.offset(dx, dy)
                    DragState.DRAG_TL -> { currentScreenRect.left += dx; currentScreenRect.top += dy }
                    DragState.DRAG_TR -> { currentScreenRect.right += dx; currentScreenRect.top += dy }
                    DragState.DRAG_BL -> { currentScreenRect.left += dx; currentScreenRect.bottom += dy }
                    DragState.DRAG_BR -> { currentScreenRect.right += dx; currentScreenRect.bottom += dy }
                    DragState.DRAG_LEFT -> currentScreenRect.left += dx
                    DragState.DRAG_RIGHT -> currentScreenRect.right += dx
                    DragState.DRAG_TOP -> currentScreenRect.top += dy
                    DragState.DRAG_BOTTOM -> currentScreenRect.bottom += dy
                    DragState.NONE -> {}
                }
                
                lastTouchX = x
                lastTouchY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragState != DragState.NONE) {
                    // Standardize rect (make sure left < right, top < bottom)
                    val r = currentScreenRect
                    val left = min(r.left, r.right)
                    val right = max(r.left, r.right)
                    val top = min(r.top, r.bottom)
                    val bottom = max(r.top, r.bottom)
                    currentScreenRect.set(left, top, right, bottom)
                    
                    saveScreenRectToBitmapCoords()
                    dragState = DragState.NONE
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isHit(x: Float, y: Float, targetX: Float, targetY: Float): Boolean {
        return abs(x - targetX) < touchRadius && abs(y - targetY) < touchRadius
    }

    private fun updateCurrentScreenRect() {
        cropRectBitmapContent?.let { bitmapRect ->
            imageMatrix.mapRect(currentScreenRect, bitmapRect)
        }
    }

    private fun saveScreenRectToBitmapCoords() {
        val drawable = drawable ?: return
        
        val inverseMatrix = Matrix()
        imageMatrix.invert(inverseMatrix)
        
        val bitmapRect = RectF()
        inverseMatrix.mapRect(bitmapRect, currentScreenRect)

        bitmapRect.left = max(0f, bitmapRect.left)
        bitmapRect.top = max(0f, bitmapRect.top)
        bitmapRect.right = min(drawable.intrinsicWidth.toFloat(), bitmapRect.right)
        bitmapRect.bottom = min(drawable.intrinsicHeight.toFloat(), bitmapRect.bottom)

        if (bitmapRect.width() > 10 && bitmapRect.height() > 10) {
            cropRectBitmapContent = bitmapRect
        } else {
            cropRectBitmapContent = null
        }
    }

    fun onNewImageLoaded() {
        val drawable = drawable ?: return
        cropRectBitmapContent?.let { rect ->
            rect.left = max(0f, rect.left)
            rect.top = max(0f, rect.top)
            rect.right = min(drawable.intrinsicWidth.toFloat(), rect.right)
            rect.bottom = min(drawable.intrinsicHeight.toFloat(), rect.bottom)
            if (rect.right <= rect.left || rect.bottom <= rect.top) {
                cropRectBitmapContent = null
            }
        }
        invalidate()
    }

    fun getCropRectBitmapCoords(): RectF? {
        if (!isCropEnabled) return null
        return cropRectBitmapContent
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isCropEnabled) return

        if (dragState != DragState.NONE) {
            // Draw currently moving rect
            val left = min(currentScreenRect.left, currentScreenRect.right)
            val right = max(currentScreenRect.left, currentScreenRect.right)
            val top = min(currentScreenRect.top, currentScreenRect.bottom)
            val bottom = max(currentScreenRect.top, currentScreenRect.bottom)
            canvas.drawRect(left, top, right, bottom, fillPaint)
            canvas.drawRect(left, top, right, bottom, paint)
        } else {
            cropRectBitmapContent?.let { _ ->
                updateCurrentScreenRect()
                canvas.drawRect(currentScreenRect, fillPaint)
                canvas.drawRect(currentScreenRect, paint)
                
                // Draw corner handles
                val r = currentScreenRect
                val rad = 15f
                canvas.drawCircle(r.left, r.top, rad, cornerPaint)
                canvas.drawCircle(r.right, r.top, rad, cornerPaint)
                canvas.drawCircle(r.left, r.bottom, rad, cornerPaint)
                canvas.drawCircle(r.right, r.bottom, rad, cornerPaint)
            }
        }
    }
}
