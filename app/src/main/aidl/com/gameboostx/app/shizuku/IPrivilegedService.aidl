// Runs inside the process Shizuku launches at the shell (or root, if the user granted Shizuku
// via root) UID — never inside our own app process. Every method here maps to one real,
// documented Android operation; nothing here is a generic "run this shell string" backdoor
// (spec §48 explicitly forbids exposing raw shell execution to the user).
package com.gameboostx.app.shizuku;

interface IPrivilegedService {

    /** Android version/manufacturer info as seen from the privileged process, for the debug screen. */
    String getEnvironmentInfo();

    /**
     * Sets both min and peak refresh rate to the same value via Settings.System, the same
     * mechanism Android's own Display settings UI uses (WRITE_SECURE_SETTINGS, which the
     * shell UID holds). Returns false without throwing if this device/Android version doesn't
     * support the setting.
     */
    boolean setPeakRefreshRate(float hz);

    /** Clears the min/peak refresh rate override so Android goes back to its own default behavior. */
    boolean clearRefreshRateOverride();

    /**
     * Sets a package's Android Game Mode via the documented `cmd game` interface (the same
     * command adb/Settings use), executed here as shell instead of over adb. gameMode uses
     * GameManager.GAME_MODE_* values (STANDARD=1, PERFORMANCE=2, BATTERY=3). Requires API 31+.
     */
    boolean setGameMode(String packageName, int gameMode);

    /** Reads the package's current Game Mode the same way (`cmd game mode <pkg>`), or -1 if unknown/unsupported. */
    int getGameMode(String packageName);

    void destroy();
}
