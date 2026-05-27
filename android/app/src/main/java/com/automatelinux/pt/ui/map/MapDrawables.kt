package com.automatelinux.pt.ui.map

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import org.osmdroid.views.MapView

internal fun createCircleDrawable(
    fillColor: Int,
    radius: Int,
    strokeColor: Int = Color.TRANSPARENT,
    strokeWidth: Float = 0f
): Drawable {
    val size = (radius * 2 + strokeWidth * 2).toInt()
    return object : Drawable() {
        override fun draw(canvas: Canvas) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            if (strokeWidth > 0f) {
                paint.color = strokeColor
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, radius + strokeWidth, paint)
            }

            paint.color = fillColor
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius.toFloat(), paint)
        }

        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

internal fun createDiamondPinDrawable(density: Float): Drawable {
    val width = (30 * density).toInt()
    val height = (40 * density).toInt()

    return object : Drawable() {
        override fun draw(canvas: Canvas) {
            val cx = bounds.centerX().toFloat()
            val top = bounds.top.toFloat()
            val bottom = bounds.bottom.toFloat()
            val d = density
            val diamondCy = top + 11f * d

            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cx, top + 20f * d, cx, bottom,
                    Color.parseColor("#E65100"), Color.parseColor("#BF360C"),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                cx - 1.5f * d, top + 20f * d,
                cx + 1.5f * d, bottom,
                1.5f * d, 1.5f * d, stemPaint
            )

            canvas.save()
            canvas.rotate(45f, cx, diamondCy)

            val dSize = 15f * d
            val diamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    cx - dSize / 2f, diamondCy - dSize / 2f,
                    cx + dSize / 2f, diamondCy + dSize / 2f,
                    intArrayOf(
                        Color.parseColor("#FFB74D"),
                        Color.parseColor("#E65100"),
                        Color.parseColor("#FF8A65")
                    ),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(
                cx - dSize / 2f, diamondCy - dSize / 2f,
                cx + dSize / 2f, diamondCy + dSize / 2f,
                3f * d, 3f * d, diamondPaint
            )

            val innerSize = 5.5f * d
            val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(216, 255, 255, 255)
            }
            canvas.drawRoundRect(
                cx - innerSize / 2f, diamondCy - innerSize / 2f,
                cx + innerSize / 2f, diamondCy + innerSize / 2f,
                1f * d, 1f * d, innerPaint
            )

            canvas.restore()
        }

        override fun getIntrinsicWidth() = width
        override fun getIntrinsicHeight() = height
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

class AnimatedOriginDrawable(
    private val density: Float,
    private val mapView: MapView
) : Drawable() {

    private var progress = 0f
    private val coreRadius = 7f * density
    private val maxRippleRadius = coreRadius * 2.2f
    private val totalRadius = maxRippleRadius + 3f * density
    private val size = (totalRadius * 2).toInt()

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2400L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { anim ->
            progress = anim.animatedFraction
            invalidateSelf()
            mapView.postInvalidate()
        }
        start()
    }

    override fun draw(canvas: Canvas) {
        val cx = bounds.centerX().toFloat()
        val cy = bounds.centerY().toFloat()

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx, cy, coreRadius * 2.5f,
                Color.argb(70, 0, 188, 212), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, coreRadius * 2.5f, glowPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        for (i in 0..2) {
            val ringProgress = (progress + i / 3f) % 1f
            val scale = 0.5f + ringProgress * 1.7f
            ringPaint.color = Color.parseColor("#00BCD4")
            ringPaint.alpha = ((1f - ringProgress) * 178).toInt().coerceIn(0, 255)
            canvas.drawCircle(cx, cy, coreRadius * scale, ringPaint)
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        canvas.drawCircle(cx, cy, coreRadius + 2f * density, borderPaint)

        val corePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = RadialGradient(
                cx - coreRadius * 0.15f, cy - coreRadius * 0.15f, coreRadius,
                Color.parseColor("#4DD0E1"), Color.parseColor("#00838F"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, coreRadius, corePaint)
    }

    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    fun stopAnimation() {
        animator.cancel()
    }
}
