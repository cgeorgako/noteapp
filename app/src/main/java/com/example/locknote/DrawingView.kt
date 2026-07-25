package com.example.locknote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * A finger-as-stylus drawing surface with pen and eraser tools.
 *
 * Committed strokes are rendered onto an offscreen ARGB bitmap so the eraser
 * can genuinely remove ink (PorterDuff.CLEAR) rather than paint over it. The
 * stroke list is kept so undo/redo simply re-renders the bitmap.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private data class Stroke(val path: Path, val paint: Paint)

    private val strokes = ArrayList<Stroke>()
    private val undone = ArrayList<Stroke>()

    /** Persisted previous drawing, kept beneath the live strokes. */
    private var baseline: Bitmap? = null
    private var pendingBaseline: Bitmap? = null

    private var buffer: Bitmap? = null
    private var bufferCanvas: Canvas? = null

    private var current: Stroke? = null
    private var lastX = 0f
    private var lastY = 0f

    var isEraser: Boolean = false
    var strokeColor: Int = Color.WHITE
    var strokeWidth: Float = 10f

    init {
        // Software layer so PorterDuff.CLEAR reliably erases to transparent,
        // letting the pad background show through.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun isEmpty(): Boolean = strokes.isEmpty() && baseline == null && current == null

    private fun makePaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = this@DrawingView.strokeWidth
        if (isEraser) {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            color = Color.TRANSPARENT
        } else {
            color = strokeColor
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        buffer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bufferCanvas = Canvas(buffer!!)
        pendingBaseline?.let {
            baseline = Bitmap.createScaledBitmap(it, w, h, true)
            pendingBaseline = null
        }
        redrawAll()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        buffer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        // Live preview of the stroke in progress.
        current?.let { canvas.drawPath(it.path, it.paint) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                undone.clear()
                val path = Path().apply {
                    moveTo(x, y)
                    lineTo(x + 0.1f, y + 0.1f) // a tap leaves a dot
                }
                current = Stroke(path, makePaint())
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val midX = (lastX + x) / 2f
                val midY = (lastY + y) / 2f
                current?.path?.quadTo(lastX, lastY, midX, midY)
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                current?.let {
                    strokes.add(it)
                    bufferCanvas?.drawPath(it.path, it.paint)
                }
                current = null
            }
            else -> return false
        }
        invalidate()
        return true
    }

    private fun redrawAll() {
        val c = bufferCanvas ?: return
        buffer?.eraseColor(Color.TRANSPARENT)
        baseline?.let { c.drawBitmap(it, 0f, 0f, null) }
        for (s in strokes) c.drawPath(s.path, s.paint)
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            undone.add(strokes.removeAt(strokes.size - 1))
            redrawAll()
        }
    }

    fun redo() {
        if (undone.isNotEmpty()) {
            val s = undone.removeAt(undone.size - 1)
            strokes.add(s)
            bufferCanvas?.drawPath(s.path, s.paint)
            invalidate()
        }
    }

    fun clear() {
        strokes.clear()
        undone.clear()
        baseline = null
        pendingBaseline = null
        current = null
        buffer?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    /** Restore a previously saved drawing as the base layer. */
    fun setBaselineBitmap(bmp: Bitmap) {
        if (width > 0 && height > 0) {
            baseline = Bitmap.createScaledBitmap(bmp, width, height, true)
            redrawAll()
        } else {
            pendingBaseline = bmp
        }
    }

    /** Flatten the drawing (transparent ink) onto a solid background. */
    fun exportBitmap(background: Int = Color.rgb(22, 22, 26)): Bitmap {
        val w = if (width > 0) width else 1080
        val h = if (height > 0) height else 1920
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(background)
        buffer?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        current?.let { canvas.drawPath(it.path, it.paint) }
        return out
    }

    /** Transparent-background snapshot for lightweight persistence. */
    fun snapshot(): Bitmap? {
        val src = buffer ?: return null
        return src.copy(Bitmap.Config.ARGB_8888, false)
    }
}
