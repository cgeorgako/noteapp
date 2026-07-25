package com.example.locknote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * A finger-as-stylus drawing surface.
 *
 * Strokes are drawn as smoothed quadratic curves. Line width varies with the
 * speed of the finger (fast = thin, slow = thick) to imitate the pressure feel
 * of a real pen, and it also honours real stylus pressure when the hardware
 * reports it.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    /** A single completed segment, so we can redraw and export losslessly. */
    private data class Segment(val path: Path, val paint: Paint)

    private val segments = ArrayList<Segment>()
    private val undone = ArrayList<Segment>()

    private var activePath = Path()
    private var lastX = 0f
    private var lastY = 0f

    var strokeColor: Int = Color.WHITE
        set(value) {
            field = value
            livePaint.color = value
        }

    /** Base stroke width in px; the actual width modulates around this. */
    var baseStrokeWidth: Float = 9f

    private val livePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = baseStrokeWidth
    }

    fun isEmpty(): Boolean = segments.isEmpty() && activePath.isEmpty

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (s in segments) canvas.drawPath(s.path, s.paint)
        canvas.drawPath(activePath, livePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                undone.clear()
                activePath = Path()
                activePath.moveTo(x, y)
                // draw a dot so a single tap leaves a mark
                activePath.lineTo(x + 0.1f, y + 0.1f)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (lastX + x) / 2f
                val midY = (lastY + y) / 2f
                activePath.quadTo(lastX, lastY, midX, midY)
                livePaint.strokeWidth = widthFor(event, hypot(x - lastX, y - lastY))
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // commit the stroke with its own paint snapshot
                val committed = Paint(livePaint)
                segments.add(Segment(activePath, committed))
                activePath = Path()
            }
            else -> return false
        }
        invalidate()
        return true
    }

    /** Width from stylus pressure if available, otherwise from finger speed. */
    private fun widthFor(event: MotionEvent, distance: Float): Float {
        val pressure = event.pressure
        if (pressure > 0f && pressure < 1f && event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            return (baseStrokeWidth * (0.4f + pressure * 1.6f)).coerceIn(2f, baseStrokeWidth * 2.2f)
        }
        // speed-based: slow strokes get fatter, fast strokes thin out
        val speedFactor = (1f - (distance / 60f)).coerceIn(0f, 1f)
        return (baseStrokeWidth * (0.45f + speedFactor)).coerceIn(3f, baseStrokeWidth * 1.6f)
    }

    fun undo() {
        if (segments.isNotEmpty()) {
            undone.add(segments.removeAt(segments.size - 1))
            invalidate()
        }
    }

    fun redo() {
        if (undone.isNotEmpty()) {
            segments.add(undone.removeAt(undone.size - 1))
            invalidate()
        }
    }

    fun clear() {
        segments.clear()
        undone.clear()
        activePath = Path()
        invalidate()
    }

    /** Export the current drawing onto a solid background for saving. */
    fun exportBitmap(background: Int = Color.rgb(22, 22, 26)): Bitmap {
        val w = if (width > 0) width else 1080
        val h = if (height > 0) height else 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(background)
        for (s in segments) canvas.drawPath(s.path, s.paint)
        canvas.drawPath(activePath, livePaint)
        return bmp
    }
}
