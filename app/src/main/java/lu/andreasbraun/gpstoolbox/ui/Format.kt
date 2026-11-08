package lu.andreasbraun.gpstoolbox.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Fmt {
    fun num(v: Double?, digits: Int = 1): String = if (v == null || v.isNaN()) "—" else String.format(Locale.ROOT, "%.${digits}f", v)
    fun num(v: Float?, digits: Int = 1): String = num(v?.toDouble(), digits)

    fun meters(m: Double?): String = when {
        m == null -> "—"
        m < 10 -> String.format(Locale.ROOT, "%.1f m", m)
        m < 1000 -> String.format(Locale.ROOT, "%.0f m", m)
        m < 100_000 -> String.format(Locale.ROOT, "%.2f km", m / 1000)
        else -> String.format(Locale.ROOT, "%.0f km", m / 1000)
    }

    fun ago(timeMs: Long, nowMs: Long): String {
        if (timeMs <= 0) return "never"
        val s = (nowMs - timeMs) / 1000
        return when {
            s < 60 -> "just now"
            s < 3600 -> "${s / 60} min ago"
            s < 48 * 3600 -> "${s / 3600} h ago"
            else -> "${s / 86400} d ago"
        }
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }

    fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0)
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0)
    }

    fun dateTime(ms: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
}
