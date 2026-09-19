package com.gameboostx.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.gameboostx.app.data.db.SessionRecordEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ExportFormat(val extension: String, val mimeType: String) {
    TXT("txt", "text/plain"),
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
}

/**
 * Writes a local file only — nothing is uploaded automatically (spec §27/§37/§52). The result
 * is handed back as a share Intent so the person decides where it goes, exactly like any other
 * "Share" action on Android.
 */
class SessionExporter(private val context: Context) {

    fun export(sessions: List<SessionRecordEntity>, format: ExportFormat): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val filename = "gameboostx_sessions_${System.currentTimeMillis()}.${format.extension}"
        val file = File(dir, filename)

        val content = when (format) {
            ExportFormat.TXT -> toText(sessions)
            ExportFormat.CSV -> toCsv(sessions)
            ExportFormat.JSON -> toJson(sessions)
        }
        file.writeText(content)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun fmt(millis: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

    private fun toText(sessions: List<SessionRecordEntity>): String = buildString {
        appendLine("GameBoost X — Session History Export")
        appendLine("Generated: ${fmt(System.currentTimeMillis())}")
        appendLine()
        sessions.forEach { s ->
            appendLine(s.displayName)
            appendLine("  Started: ${fmt(s.startedAtMillis)}")
            appendLine("  Duration: ${(s.endedAtMillis - s.startedAtMillis) / 60000} min")
            appendLine("  Profile: ${s.profile}")
            appendLine("  Temperature: ${s.startTempCelsius ?: "?"}°C → ${s.endTempCelsius ?: "?"}°C (peak ${s.peakTempCelsius ?: "?"}°C)")
            appendLine("  Battery: ${s.startBatteryPercent}% → ${s.endBatteryPercent}%")
            appendLine("  Refresh rate: ${s.refreshRateHz.toInt()}Hz")
            appendLine("  Thermal status at end: ${s.thermalStatusAtEnd}")
            appendLine("  Ended: ${s.endedReason}")
            appendLine()
        }
    }

    private fun toCsv(sessions: List<SessionRecordEntity>): String = buildString {
        appendLine("displayName,packageName,profile,startedAt,endedAt,durationMin,startTempC,endTempC,peakTempC,startBattery,endBattery,refreshRateHz,thermalStatusAtEnd,endedReason")
        sessions.forEach { s ->
            val durationMin = (s.endedAtMillis - s.startedAtMillis) / 60000
            appendLine(
                listOf(
                    csvEscape(s.displayName), s.packageName, s.profile, fmt(s.startedAtMillis), fmt(s.endedAtMillis),
                    durationMin, s.startTempCelsius ?: "", s.endTempCelsius ?: "", s.peakTempCelsius ?: "",
                    s.startBatteryPercent, s.endBatteryPercent, s.refreshRateHz.toInt(), s.thermalStatusAtEnd, s.endedReason,
                ).joinToString(",")
            )
        }
    }

    private fun csvEscape(value: String): String =
        if (value.contains(",") || value.contains("\"")) "\"${value.replace("\"", "\"\"")}\"" else value

    private fun toJson(sessions: List<SessionRecordEntity>): String {
        val array = JSONArray()
        sessions.forEach { s ->
            array.put(
                JSONObject().apply {
                    put("displayName", s.displayName)
                    put("packageName", s.packageName)
                    put("profile", s.profile)
                    put("startedAtMillis", s.startedAtMillis)
                    put("endedAtMillis", s.endedAtMillis)
                    put("startTempCelsius", s.startTempCelsius ?: JSONObject.NULL)
                    put("endTempCelsius", s.endTempCelsius ?: JSONObject.NULL)
                    put("peakTempCelsius", s.peakTempCelsius ?: JSONObject.NULL)
                    put("startBatteryPercent", s.startBatteryPercent)
                    put("endBatteryPercent", s.endBatteryPercent)
                    put("refreshRateHz", s.refreshRateHz)
                    put("thermalStatusAtEnd", s.thermalStatusAtEnd)
                    put("changesApplied", s.changesApplied)
                    put("endedReason", s.endedReason)
                }
            )
        }
        return array.toString(2)
    }
}
