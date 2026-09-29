package com.yourname.expensetrackerapp

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin

/**
 * Small animated donut chart for a handful of (label, value, color) slices, drawn with plain
 * Canvas arcs instead of a charting dependency — keeps this in line with the project's existing
 * choice to avoid pulling in a charting library for a few simple visualizations. Each slice big
 * enough to fit a label shows its exact percentage share, rotated to follow the ring's tangent
 * so labels read naturally around the chart instead of overlapping it.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** [displayPercent] is the already-rounded, sum-to-100 share used for the label text —
     *  independent from [value], which still drives the arc's exact sweep angle. */
    data class Slice(val label: String, val value: Double, val color: Int, val displayPercent: Double)

    /** Below this share, a percentage label would overlap its neighbors, so it's skipped. */
    private val minLabelPercent = 6.0

    private var slices: List<Slice> = emptyList()
    private var drawFraction = 0f
    private var animator: ValueAnimator? = null

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context, R.color.surface_variant)
    }
    private val sliceLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val centerAmountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.on_surface)
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }
    private val bounds = RectF()
    private var centerX = 0f
    private var centerY = 0f
    private var labelRadius = 0f
    private var maxCenterTextSize = 0f
    private var minCenterTextSize = 0f
    private var centerTextAvailableWidth = 0f

    fun setSlices(newSlices: List<Slice>) {
        slices = newSlices
        animator?.cancel()
        drawFraction = 0f
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                drawFraction = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val strokeWidth = minOf(w, h) * 0.18f
        arcPaint.strokeWidth = strokeWidth
        trackPaint.strokeWidth = strokeWidth
        val inset = strokeWidth / 2f
        bounds.set(inset, inset, w - inset, h - inset)
        centerX = w / 2f
        centerY = h / 2f
        labelRadius = (minOf(w, h) / 2f) - strokeWidth / 2f
        sliceLabelPaint.textSize = strokeWidth * 0.34f

        // The hole's diameter is the view's size minus the ring itself; the amount text is
        // fitted inside a comfortable fraction of that so it never touches the ring.
        val holeDiameter = minOf(w, h) - 2f * strokeWidth
        centerTextAvailableWidth = holeDiameter * 0.82f
        maxCenterTextSize = minOf(w, h) * 0.14f
        minCenterTextSize = minOf(w, h) * 0.05f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bounds.isEmpty) return
        canvas.drawArc(bounds, 0f, 360f, false, trackPaint)

        val total = slices.sumOf { it.value }
        if (total <= 0.0) return

        var startAngle = -90f
        for (slice in slices) {
            val sweep = (slice.value / total * 360.0).toFloat()
            arcPaint.color = slice.color
            canvas.drawArc(bounds, startAngle, sweep * drawFraction, false, arcPaint)

            if (slice.displayPercent >= minLabelPercent && drawFraction > 0.85f) {
                drawTangentialLabel(canvas, startAngle + sweep / 2f, formatPercent(slice.displayPercent))
            }
            startAngle += sweep
        }

        val amountText = CurrencyFormatter.format(context, total)
        fitCenterTextSize(amountText)
        val amountY = centerY - (centerAmountPaint.ascent() + centerAmountPaint.descent()) / 2f
        canvas.drawText(amountText, centerX, amountY, centerAmountPaint)
    }

    /** Re-fits [centerAmountPaint]'s size to [text] every draw — always starting back from the
     *  max, so a short amount grows back to full size just as readily as a long one shrinks —
     *  down to [minCenterTextSize], below which it's left to slightly overflow rather than
     *  become illegible. */
    private fun fitCenterTextSize(text: String) {
        var size = maxCenterTextSize
        centerAmountPaint.textSize = size
        while (size > minCenterTextSize && centerAmountPaint.measureText(text) > centerTextAvailableWidth) {
            size -= 1f
            centerAmountPaint.textSize = size
        }
    }

    /** Draws [text] centered on the ring at [midAngleDeg], rotated to run along the ring's
     *  tangent — flipped upright whenever that tangent would otherwise render upside-down. */
    private fun drawTangentialLabel(canvas: Canvas, midAngleDeg: Float, text: String) {
        val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
        val labelX = centerX + labelRadius * cos(midAngleRad).toFloat()
        val labelY = centerY + labelRadius * sin(midAngleRad).toFloat()

        var rotationDeg = midAngleDeg + 90f
        val normalized = ((rotationDeg % 360f) + 360f) % 360f
        if (normalized in 90f..270f) rotationDeg += 180f

        canvas.save()
        canvas.translate(labelX, labelY)
        canvas.rotate(rotationDeg)
        val textOffset = -(sliceLabelPaint.ascent() + sliceLabelPaint.descent()) / 2f
        canvas.drawText(text, 0f, textOffset, sliceLabelPaint)
        canvas.restore()
    }

    private fun formatPercent(percent: Double): String = String.format(java.util.Locale.getDefault(), "%.1f%%", percent)
}
