package com.azra

import android.graphics.Color
import android.graphics.Paint
import android.view.Surface
import java.util.concurrent.atomic.AtomicBoolean

class FrameInjector(private val surface: Surface, private val width: Int, private val height: Int) {

    private var renderThread: Thread? = null
    private val isRunning = AtomicBoolean(false)

    fun start() {
        if (isRunning.get()) return
        isRunning.set(true)
        
        renderThread = Thread {
            renderLoop()
        }
        renderThread?.start()
    }

    fun stop() {
        isRunning.set(false)
        renderThread?.interrupt()
        renderThread = null
    }

    private fun renderLoop() {
        val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.FILL
        }

        while (isRunning.get() && surface.isValid) {
            try {
                // Lock the canvas, draw, and unlock
                val canvas = surface.lockCanvas(null)
                if (canvas != null) {
                    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                    surface.unlockCanvasAndPost(canvas)
                }
                
                // Pace the frames (e.g., 30 FPS -> ~33ms)
                Thread.sleep(33)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                // Handle or log surface errors
            }
        }
    }
}
