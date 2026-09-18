package com.gameboostx.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.gameboostx.app.data.model.GameDetectionSource
import com.gameboostx.app.data.model.GameInfo

/**
 * Detects installed games two ways, both real:
 *  1. Android's own ApplicationInfo.category == CATEGORY_GAME (API 26+, what the Play Store sets).
 *  2. A known-package allowlist for the titles named in the spec, since many popular games
 *     (including some on this list) don't set CATEGORY_GAME correctly.
 * Nothing is detected by guessing app names or icons — spec §7/§26.
 */
class GameLibraryManager(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager

    fun scanInstalledGames(): List<GameInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = try {
            packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        } catch (_: Exception) {
            emptyList()
        }

        return resolved
            .mapNotNull { it.activityInfo?.applicationInfo }
            .distinctBy { it.packageName }
            .mapNotNull { appInfo -> classify(appInfo) }
            .sortedBy { it.displayName.lowercase() }
    }

    private fun classify(appInfo: ApplicationInfo): GameInfo? {
        val packageName = appInfo.packageName
        val knownName = KNOWN_GAME_PACKAGES[packageName]
        val isCategoryGame = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            appInfo.category == ApplicationInfo.CATEGORY_GAME

        val source = when {
            knownName != null -> GameDetectionSource.KNOWN_PACKAGE
            isCategoryGame -> GameDetectionSource.ANDROID_CATEGORY_GAME
            else -> return null // not recognized as a game by either real signal — leave it out rather than guess
        }

        val label = knownName ?: packageManager.getApplicationLabel(appInfo).toString()
        val icon = try {
            packageManager.getApplicationIcon(appInfo)
        } catch (_: Exception) {
            null
        }

        return GameInfo(
            packageName = packageName,
            displayName = label,
            icon = icon,
            detectionSource = source,
        )
    }

    /** For a game the scan missed (spec §7 "allow manual game addition"). */
    fun resolveManualPackage(packageName: String): GameInfo? {
        val appInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val icon = try {
            packageManager.getApplicationIcon(appInfo)
        } catch (_: Exception) {
            null
        }
        return GameInfo(
            packageName = packageName,
            displayName = packageManager.getApplicationLabel(appInfo).toString(),
            icon = icon,
            detectionSource = GameDetectionSource.MANUALLY_ADDED,
        )
    }

    fun launchIntentFor(packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)

    companion object {
        // Package names for the titles the spec names by name. Kept small and explicit
        // rather than an aggressive heuristic that would misclassify non-games.
        val KNOWN_GAME_PACKAGES: Map<String, String> = mapOf(
            "com.mobile.legends" to "Mobile Legends: Bang Bang",
            "com.activision.callofduty.shooter" to "Call of Duty: Mobile",
            "com.roblox.client" to "Roblox",
            "com.tencent.ig" to "PUBG Mobile",
            "com.dts.freefireth" to "Free Fire",
            "com.dts.freefiremax" to "Free Fire MAX",
            "com.mojang.minecraftpe" to "Minecraft",
            "git.artdeell.mjlaunch" to "MJLauncher (Minecraft Java)",
            "com.miHoYo.GenshinImpact" to "Genshin Impact",
            "com.miHoYo.Yuanshen" to "Genshin Impact (CN)",
            "com.HoYoverse.hkrpgoversea" to "Honkai: Star Rail",
        )
    }
}
