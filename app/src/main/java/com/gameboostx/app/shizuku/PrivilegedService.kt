package com.gameboostx.app.shizuku

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku launches this class directly (via app_process, inside the shell — or root, if the
 * user granted Shizuku via root — UID process), bypassing the normal Android component
 * lifecycle: no manifest entry, no Context. It has a public no-arg constructor as Shizuku's
 * UserService mechanism requires.
 *
 * Both operations below go through Android's own documented command-line tools (`settings`,
 * `cmd`) rather than a hidden/internal API — the same tools `adb shell` uses, just executed
 * as this process's own UID instead of over adb. There is deliberately no generic "run
 * arbitrary shell command" method here (spec §48) — only these two specific, named operations.
 */
class PrivilegedService : IPrivilegedService.Stub() {

    override fun getEnvironmentInfo(): String =
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL} · uid=${android.os.Process.myUid()}"

    override fun setPeakRefreshRate(hz: Float): Boolean {
        val a = runShell("settings put system min_refresh_rate $hz").first
        val b = runShell("settings put system peak_refresh_rate $hz").first
        return a && b
    }

    override fun clearRefreshRateOverride(): Boolean {
        val a = runShell("settings delete system min_refresh_rate").first
        val b = runShell("settings delete system peak_refresh_rate").first
        return a && b
    }

    override fun setGameMode(packageName: String, gameMode: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val modeName = gameModeName(gameMode) ?: return false
        return runShell("cmd game set --mode $modeName $packageName").first
    }

    override fun getGameMode(packageName: String): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return -1
        val (success, output) = runShell("cmd game mode $packageName")
        if (!success) return -1
        val text = output.lowercase()
        return when {
            text.contains("performance") -> 2
            text.contains("battery") -> 3
            text.contains("standard") -> 1
            else -> -1 // unrecognized output across Android versions — report unsupported rather than guess (spec §26)
        }
    }

    override fun destroy() {
        // Shizuku tears this process down itself; nothing persistent to release here.
    }

    private fun gameModeName(gameMode: Int): String? = when (gameMode) {
        1 -> "standard"
        2 -> "performance"
        3 -> "battery"
        else -> null
    }

    /** Executes as this process's own UID (shell, or root if Shizuku itself is running as root). */
    private fun runShell(command: String): Pair<Boolean, String> {
        return try {
            val process = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val output = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val exitCode = process.waitFor()
            (exitCode == 0) to output
        } catch (_: Exception) {
            false to ""
        }
    }
}
