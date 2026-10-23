package com.gameforge.booster

import android.view.Choreographer

/**
 * Honesty note: this measures the frame rate of GameForge's own overlay
 * window via Choreographer, which tracks the device's vsync pulses system-
 * wide — it's a real, live number, but it reflects compositor throughput
 * for the overlay surface, not instrumented per-frame timing read out of
 * whatever game is running underneath (no non-root API exposes that).
 * Treat it as "is the system currently keeping up with vsync", a solid
 * proxy for jank, not a frame-perfect in-game FPS meter.
 */
class FpsCounter(private val onUpdate: (Int) -> Unit) {

    private var frameCount = 0
    private var windowStartNanos = 0L
    private var running = false

    private val callback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (windowStartNanos == 0L) windowStartNanos = frameTimeNanos
            frameCount++
            val elapsedNanos = frameTimeNanos - windowStartNanos
            if (elapsedNanos >= 1_000_000_000L) {
                onUpdate(frameCount)
                frameCount = 0
                windowStartNanos = frameTimeNanos
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        running = true
        frameCount = 0
        windowStartNanos = 0L
        Choreographer.getInstance().postFrameCallback(callback)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(callback)
    }
}
