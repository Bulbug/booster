package com.gameboostx.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

data class OemGameModeInfo(
    val vendorLabel: String,
    val appLabel: String,
    val packageName: String,
)

/**
 * Many phones ship a manufacturer game-mode app (Samsung Game Booster, MIUI Game Turbo, ColorOS/
 * OnePlus Game Space, Vivo/iQOO Game Mode) that goes well beyond what AOSP's GameManager API
 * exposes — but there's no public, documented API to control these programmatically, and their
 * internals change across ROM versions. Per spec §11 ("never pretend a mode exists if the device
 * does not expose it" / "do not use undocumented manufacturer hacks"), this only DETECTS whether
 * one is installed and offers a real launch into the vendor's own UI — it never tries to toggle
 * their settings itself.
 */
class OemGameModeManager(private val context: Context) {

    /** Returns the first detected vendor game-mode app on this device, or null if none of the known ones are installed. */
    fun detect(): OemGameModeInfo? {
        val pm = context.packageManager
        return CANDIDATES.firstOrNull { isInstalled(pm, it.packageName) }
    }

    fun launchIntent(packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        pm.getApplicationInfo(packageName, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        // Package names observed for each vendor's own game-mode app. Listed in order per
        // vendor since the exact package has changed across ROM versions; the first match wins.
        // If a device has none of these, detect() simply returns null — no fallback guessing.
        private val CANDIDATES = listOf(
            OemGameModeInfo("Samsung", "Game Booster", "com.samsung.android.game.gametools"),
            OemGameModeInfo("Samsung", "Game Launcher", "com.samsung.android.game.gamehome"),
            OemGameModeInfo("Xiaomi / MIUI", "Game Turbo", "com.miui.game"),
            OemGameModeInfo("ColorOS / OnePlus", "Game Space", "com.oplus.games"),
            OemGameModeInfo("ColorOS / Realme", "Game Space", "com.coloros.gamespaceui"),
            OemGameModeInfo("OnePlus (older OxygenOS)", "Game Space", "com.oneplus.gamespace"),
            OemGameModeInfo("Vivo / iQOO", "Game Mode", "com.iqoo.game"),
            OemGameModeInfo("Vivo", "Game Mode", "com.vivo.game"),
            OemGameModeInfo("ASUS ROG Phone", "Game Genie", "com.asus.game.gameoptiflex"),
        )
    }
}
