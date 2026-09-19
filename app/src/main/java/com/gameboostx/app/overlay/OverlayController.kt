package com.gameboostx.app.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.gameboostx.app.data.OverlayPermissionHelper
import com.gameboostx.app.session.SessionUiState

/**
 * A minimal always-on-top window using WindowManager directly (no Activity/Compose lifecycle
 * available here). Shows only device-level numbers this app actually measured — FPS is shown
 * as "N/A" because there is no reliable source for another app's real frame rate (spec §17/§24).
 */
class OverlayController(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var line1: TextView? = null
    private var line2: TextView? = null

    val isShowing: Boolean get() = overlayView != null

    fun show(): Boolean {
        if (isShowing) return true
        if (!OverlayPermissionHelper.isGranted(context)) return false

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.argb(190, 0, 0, 0))
        }
        val l1 = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        val l2 = TextView(context).apply {
            setTextColor(Color.parseColor("#39FF88"))
            textSize = 12f
            typeface = Typeface.MONOSPACE
        }
        container.addView(l1)
        container.addView(l2)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 100
        }

        return try {
            wm.addView(container, params)
            overlayView = container
            line1 = l1
            line2 = l2
            true
        } catch (_: Exception) {
            false
        }
    }

    fun update(state: SessionUiState) {
        val snap = state.latestSnapshot ?: return
        val cpu = snap.cpu.utilizationFraction?.let { "${(it * 100).toInt()}%" } ?: "?"
        val ram = "${snap.memory.usedBytes / (1024 * 1024 * 1024.0)}".take(3)
        val temp = snap.thermal.batteryTempCelsius?.let { "%.0f°C".format(it) } ?: "?"
        line1?.text = "FPS: N/A   ${snap.display.currentRefreshRateHz.toInt()}Hz"
        line2?.text = "CPU $cpu  RAM ${ram}GB  $temp  ${state.elapsedSeconds / 60}:${(state.elapsedSeconds % 60).toString().padStart(2, '0')}"
    }

    fun hide() {
        val wm = windowManager ?: return
        overlayView?.let {
            try { wm.removeView(it) } catch (_: Exception) { }
        }
        overlayView = null
        line1 = null
        line2 = null
    }
}
