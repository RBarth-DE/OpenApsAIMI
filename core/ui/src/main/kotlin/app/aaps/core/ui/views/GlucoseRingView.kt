package app.aaps.core.ui.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import app.aaps.core.ui.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GlucoseRingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Config from XML attrs
    private var step1MaxMgdl = 130f
    private var step2MaxMgdl = 180f
    private var step3MaxMgdl = 220f
    private var stepColor1 = Color.parseColor("#00C853")
    private var stepColor2 = Color.parseColor("#FFD600")
    private var stepColor3 = Color.parseColor("#FF6D00")
    private var stepColor4 = Color.parseColor("#D50000")
    private var ringBackgroundColor = Color.TRANSPARENT
    private var ringStrokeWidthPx = 12f
    private var ringTextColor = Color.WHITE
    private var ringSubTextColor = Color.LTGRAY

    // Dynamic data
    private var bgMgdl = 0
    private var mainText = "--"
    private var subLeftText = ""
    private var subRightText = ""
    private var noseAngleDeg: Float? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val nosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    init {
        context.theme.obtainStyledAttributes(attrs, R.styleable.GlucoseRingView, 0, 0).apply {
            try {
                step1MaxMgdl = getFloat(R.styleable.GlucoseRingView_glucoseRingStep1MaxMgdl, 130f)
                step2MaxMgdl = getFloat(R.styleable.GlucoseRingView_glucoseRingStep2MaxMgdl, 180f)
                step3MaxMgdl = getFloat(R.styleable.GlucoseRingView_glucoseRingStep3MaxMgdl, 220f)
                stepColor1 = getColor(R.styleable.GlucoseRingView_glucoseRingStepColor1, Color.parseColor("#00C853"))
                stepColor2 = getColor(R.styleable.GlucoseRingView_glucoseRingStepColor2, Color.parseColor("#FFD600"))
                stepColor3 = getColor(R.styleable.GlucoseRingView_glucoseRingStepColor3, Color.parseColor("#FF6D00"))
                stepColor4 = getColor(R.styleable.GlucoseRingView_glucoseRingStepColor4, Color.parseColor("#D50000"))
                ringBackgroundColor = getColor(R.styleable.GlucoseRingView_ringBackgroundColor, Color.TRANSPARENT)
                ringStrokeWidthPx = getDimension(R.styleable.GlucoseRingView_ringStrokeWidth, 12f)
                ringTextColor = getColor(R.styleable.GlucoseRingView_ringTextColor, Color.WHITE)
                ringSubTextColor = getColor(R.styleable.GlucoseRingView_ringSubTextColor, Color.LTGRAY)
            } finally {
                recycle()
            }
        }
    }

    fun update(bgMgdl: Int, mainText: String, subLeftText: String = "", subRightText: String = "", noseAngleDeg: Float? = null) {
        this.bgMgdl = bgMgdl
        this.mainText = mainText
        this.subLeftText = subLeftText
        this.subRightText = subRightText
        this.noseAngleDeg = noseAngleDeg
        invalidate()
    }

    private fun glucoseColor(): Int = when {
        bgMgdl <= 0 -> Color.GRAY
        bgMgdl < step1MaxMgdl -> stepColor1
        bgMgdl < step2MaxMgdl -> stepColor2
        bgMgdl < step3MaxMgdl -> stepColor3
        else -> stepColor4
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = ringStrokeWidthPx
        val r = (min(w, h) - stroke) / 2f
        val cx = w / 2f
        val cy = h / 2f

        oval.set(cx - r, cy - r, cx + r, cy + r)

        // Background ring
        if (ringBackgroundColor != Color.TRANSPARENT) {
            bgPaint.color = ringBackgroundColor
            bgPaint.strokeWidth = stroke
            canvas.drawArc(oval, 0f, 360f, false, bgPaint)
        }

        // Glucose ring
        ringPaint.color = glucoseColor()
        ringPaint.strokeWidth = stroke
        ringPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(oval, -90f, 360f, false, ringPaint)

        // Nose (trend direction indicator)
        noseAngleDeg?.let { angle ->
            val rad = Math.toRadians(angle.toDouble())
            val noseR = r + stroke / 2f
            val nx = (cx + noseR * sin(rad)).toFloat()
            val ny = (cy - noseR * cos(rad)).toFloat()
            nosePaint.color = glucoseColor()
            canvas.drawCircle(nx, ny, stroke * 0.7f, nosePaint)
        }

        // Main text (glucose value)
        textPaint.color = ringTextColor
        textPaint.textSize = r * 0.5f
        textPaint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText(mainText, cx, cy + textPaint.textSize * 0.35f, textPaint)

        // Sub texts
        subTextPaint.color = ringSubTextColor
        subTextPaint.textSize = r * 0.22f
        if (subLeftText.isNotEmpty()) canvas.drawText(subLeftText, cx - r * 0.4f, cy + r * 0.55f, subTextPaint)
        if (subRightText.isNotEmpty()) canvas.drawText(subRightText, cx + r * 0.4f, cy + r * 0.55f, subTextPaint)
    }
}
