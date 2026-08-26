package com.automatelinux.pt.ui.map

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import org.osmdroid.views.MapView

internal val STOP_MARKER_BLUE: Int = Color.parseColor("#1976D2")

/**
 * A transit stop on the map: a white disc inside a blue ring, gaining a blue centre when
 * it is the stop whose arrivals board is open.
 *
 * A stop used to be a filled blue disc — the same shape, and very nearly the same blue, as
 * the device's own position ([GpsLocationOverlay.createBlueDotBitmap], #4285F4 at 7dp).
 * "Where you are" and "where a bus stops" were separated by eight pixels of diameter and
 * one shade of blue, which is not a distinction anyone reads on a moving map. Filled means
 * you; ringed means a stop. The selected stop keeps the rule — it gains a centre dot
 * rather than becoming a disc, so the one marker the user just tapped cannot be mistaken
 * for themselves either.
 *
 * This matches the web, whose StopsLayer has drawn stops hollow all along; Android was the
 * half that never did.
 *
 * Sizes are dp, not the raw pixel count the old marker used: a pixel radius shrinks as
 * screens get denser, and a ring drawn that thin greys into an unreadable smudge.
 */
internal fun createStopMarkerDrawable(density: Float, selected: Boolean): Drawable {
    val coreRadius = 3.5f * density
    val ringWidth = (if (selected) 3.5f else 3f) * density
    val centreRadius = 2f * density
    val outerRadius = coreRadius + ringWidth
    val size = (outerRadius * 2).toInt() + 2
    return object : Drawable() {
        override fun draw(canvas: Canvas) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

            paint.color = STOP_MARKER_BLUE
            canvas.drawCircle(cx, cy, outerRadius, paint)

            paint.color = Color.WHITE
            canvas.drawCircle(cx, cy, coreRadius, paint)

            if (selected) {
                paint.color = STOP_MARKER_BLUE
                canvas.drawCircle(cx, cy, centreRadius, paint)
            }
        }

        override fun getIntrinsicWidth() = size
        override fun getIntrinsicHeight() = size
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Deprecated("Deprecated")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}

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

/**
 * A bus on the map: a coloured disc carrying a bus glyph, with the line number in a badge.
 *
 * Replaces a bare coloured dot, which said only "something is here". The line number is the
 * one fact that makes a dot actionable — with a dozen of them on screen it is the difference
 * between "buses exist nearby" and "my 64 is two streets away". The web has drawn it this
 * way all along; the Android map was the half that never did.
 *
 * Drawn as vectors rather than the emoji the web uses. An emoji renders as whatever glyph
 * the device ships, cannot be recoloured, and colour is load-bearing here — it is what
 * separates a tracked bus from an ordinary one.
 *
 * Paints and the glyph path are built once per drawable, not per frame: the whole vehicle
 * layer is torn down and rebuilt on every 15s poll, so per-draw allocation would be paid
 * again on every marker of every redraw.
 */
internal fun createBusMarkerDrawable(
    fillColor: Int,
    lineNumber: String,
    density: Float,
    bearingDegrees: Int? = null
): Drawable {
    // 15dp rather than the 13dp this started at: the heading chevron lives INSIDE the
    // disc, and 13dp left it under 1dp clear of both the glyph and the disc edge, which
    // at map scale read as one white smudge. See the chevron block in draw() for why it
    // cannot simply sit outside the ring instead.
    val discRadius = 15f * density
    val ringWidth = 2f * density
    // Square, with the disc dead centre so the marker can keep ANCHOR_CENTER and sit on
    // the bus's real coordinate. The badge lives in the corner slack this leaves.
    val size = ((discRadius + ringWidth) * 2 + 12f * density).toInt()

    val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
    }
    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = fillColor
    }
    val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = fillColor
        textSize = 10f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Points north (up) around the origin; draw() translates to the marker centre and
    // rotates it to the bearing. Built once, like the paints — the whole vehicle layer is
    // rebuilt every 15s poll, so per-draw allocation is paid on every marker every time.
    val chevronPath = Path().apply {
        moveTo(0f, -13.5f * density)
        lineTo(-3f * density, -10f * density)
        lineTo(3f * density, -10f * density)
        close()
    }

    val label = lineNumber.trim()
    val textWidth = if (label.isEmpty()) 0f else badgeTextPaint.measureText(label)
    val badgeHeight = 14f * density
    val badgeWidth = (textWidth + 8f * density).coerceAtLeast(badgeHeight)

    return object : Drawable() {
        override fun draw(canvas: Canvas) {
            val cx = bounds.centerX().toFloat()
            val cy = bounds.centerY().toFloat()

            canvas.drawCircle(cx, cy, discRadius + ringWidth, ringPaint)
            canvas.drawCircle(cx, cy, discRadius, discPaint)

            // Which way the bus is actually pointing. Inside the disc rather than as an
            // arrowhead outside the ring, because the line badge is pinned to the
            // top-right and an orbiting arrow disappears behind it for every heading
            // between roughly NE and ENE — a seventh of the compass, silently. Anything
            // drawn inside the circle collides with nothing at any angle.
            //
            // The map is always north-up (no rotation gesture is enabled), so the
            // compass bearing IS the screen angle. If map rotation is ever added, this
            // has to become `bearing - mapOrientation`.
            if (bearingDegrees != null) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(bearingDegrees.toFloat())
                canvas.drawPath(chevronPath, glyphPaint)
                canvas.restore()
            }

            // The bus, as body + window band + two wheels — the same reading the web's SVG
            // gives, at a size where any more detail would turn to mush.
            val d = density
            val bodyLeft = cx - 6.5f * d
            val bodyRight = cx + 6.5f * d
            val bodyTop = cy - 7f * d
            val bodyBottom = cy + 4.5f * d
            canvas.drawRoundRect(bodyLeft, bodyTop, bodyRight, bodyBottom, 2f * d, 2f * d, glyphPaint)

            // Window cut back out of the body in the disc's colour, so the glyph reads as a
            // bus rather than a white blob at map scale.
            canvas.drawRoundRect(
                bodyLeft + 1.5f * d, bodyTop + 1.5f * d,
                bodyRight - 1.5f * d, bodyTop + 5f * d,
                1f * d, 1f * d, discPaint
            )
            canvas.drawCircle(cx - 4f * d, bodyBottom + 1f * d, 1.6f * d, glyphPaint)
            canvas.drawCircle(cx + 4f * d, bodyBottom + 1f * d, 1.6f * d, glyphPaint)

            if (label.isEmpty()) return

            // Top-right, overlapping the disc's edge — clear of the glyph, and it reads as
            // attached to this bus rather than floating beside it.
            val badgeRight = cx + discRadius + ringWidth + 3f * d
            val badgeLeft = badgeRight - badgeWidth
            val badgeTop = cy - discRadius - ringWidth - 3f * d
            val badgeBottom = badgeTop + badgeHeight
            val corner = badgeHeight / 2f
            canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, corner, corner, badgeFillPaint)
            canvas.drawRoundRect(badgeLeft, badgeTop, badgeRight, badgeBottom, corner, corner, badgeBorderPaint)

            // Centre the text on the cap box rather than the baseline, or short and tall
            // labels ("5" vs "392") sit at visibly different heights.
            val metrics = badgeTextPaint.fontMetrics
            val baseline = (badgeTop + badgeBottom) / 2f - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, (badgeLeft + badgeRight) / 2f, baseline, badgeTextPaint)
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
