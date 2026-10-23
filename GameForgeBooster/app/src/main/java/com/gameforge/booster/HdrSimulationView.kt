package com.gameforge.booster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.View

/**
 * "Virtual HDR / vivid mode" for a standard (non-HDR-panel) display, the
 * Redmi 9A included.
 *
 * How it actually works: this is a translucent, click-through overlay
 * window drawn on top of everything else (same technique blue-light-filter
 * apps like Twilight use). The system compositor alpha-blends this layer's
 * color with whatever is rendered underneath, which lets it warm up, cool
 * down, or richen the overall picture — a real, visible effect. What it
 * can NOT do is true per-pixel contrast/saturation boosting of the content
 * below (that needs to read and rewrite those pixels, which requires
 * either root or a screen-capture pipeline with real-time re-render —
 * heavy, laggy, and not what "HDR simulation" should cost you in a game).
 *
 * It is also not real HDR: true HDR needs an HDR-capable panel plus
 * HDR10/Dolby Vision metadata encoded in the content itself — neither can
 * be added by software after the fact. Note separately: the "HDR / HDRA /
 * HDR+ / AI" labels your stock camera app shows belong to the camera ISP's
 * tone-mapping pipeline, a completely different subsystem from the display
 * panel — on paper the Redmi 9A's panel spec is standard HD+, not HDR, and
 * that doesn't change based on what the camera app displays.
 *
 * Bottom line: treat this toggle as a tasteful color-tint filter you can
 * judge with your own eyes and switch off instantly — not a magic
 * upgrade of your panel's dynamic range.
 */
class HdrSimulationView(context: Context) : View(context) {

    /** 0f..1f — how strong the tint blend is. */
    var intensity: Float = 0.30f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    /** Warm/vivid tint base color; alpha is driven by [intensity]. */
    var tintColor: Int = Color.rgb(255, 196, 120)

    override fun onDraw(canvas: Canvas) {
        val alpha = (intensity * 40).toInt().coerceIn(0, 60) // keep it subtle, never opaque
        val c = Color.argb(alpha, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        canvas.drawColor(c)
        super.onDraw(canvas)
    }
}
