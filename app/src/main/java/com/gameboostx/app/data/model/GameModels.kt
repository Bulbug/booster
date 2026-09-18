package com.gameboostx.app.data.model

import android.graphics.drawable.Drawable

enum class ProfileType(val label: String) {
    SAFE("Safe"),
    BALANCED("Balanced"),
    PERFORMANCE("Performance"),
    CUSTOM("Custom"),
}

/** How confident the library is that a detected app is actually a game (spec doesn't mandate a source, but honesty does — see §26). */
enum class GameDetectionSource {
    KNOWN_PACKAGE,       // matched our known-games list (Mobile Legends, COD Mobile, etc.)
    ANDROID_CATEGORY_GAME, // ApplicationInfo.category == CATEGORY_GAME, Android's own signal
    MANUALLY_ADDED,
}

data class GameInfo(
    val packageName: String,
    val displayName: String,
    val icon: Drawable?,
    val detectionSource: GameDetectionSource,
    val profile: ProfileType = ProfileType.BALANCED,
    val lastOptimizedAtMillis: Long? = null,
)

data class BoostCheckItem(
    val label: String,
    val passed: Boolean,
    val detail: String,
)

data class BoostPlanResult(
    val checks: List<BoostCheckItem>,
    val alreadyOptimal: Boolean,
    val summary: String,
)
