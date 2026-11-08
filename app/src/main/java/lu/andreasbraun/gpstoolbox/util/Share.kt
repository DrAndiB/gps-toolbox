package lu.andreasbraun.gpstoolbox.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import lu.andreasbraun.gpstoolbox.telemetry.Telemetry
import java.io.File
import java.util.Locale

object Share {
    fun file(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Telemetry.event("share_file", "ext" to file.extension)
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Writes [content] to an export file and shares it. */
    fun text(context: Context, fileName: String, content: String, mime: String) {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val f = File(dir, fileName)
        f.writeText(content)
        Telemetry.event("export", "name" to fileName)
        file(context, f, mime)
    }

    fun position(context: Context, lat: Double, lon: Double, accuracy: Float?, label: String?) {
        val coords = String.format(Locale.ROOT, "%.6f, %.6f", lat, lon)
        val acc = accuracy?.let { String.format(Locale.ROOT, " (±%.1f m)", it) } ?: ""
        val osm = String.format(Locale.ROOT, "https://www.openstreetmap.org/?mlat=%.6f&mlon=%.6f#map=18/%.6f/%.6f", lat, lon, lat, lon)
        val text = listOfNotNull(label, coords + acc, osm).joinToString("\n")
        Telemetry.event("share_position", "waypoint" to (label != null))
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, "Share position").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(context: Context, label: String, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Telemetry.event("copy")
        // Android 13+ shows its own confirmation for clipboard writes.
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) toast(context, "Copied")
    }

    fun toast(context: Context, text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
}
