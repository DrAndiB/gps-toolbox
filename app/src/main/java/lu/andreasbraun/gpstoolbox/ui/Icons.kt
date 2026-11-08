package lu.andreasbraun.gpstoolbox.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Line icons drawn to match the design (24 × 24, 1.8 stroke). Icon tint recolours them. */
object Ic {
    private fun circle(cx: Float, cy: Float, r: Float): String =
        "M${cx - r},${cy} a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

    private fun icon(name: String, strokes: List<String>, fills: List<String> = emptyList()): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            for (d in strokes) addPath(
                pathData = addPathNodes(d),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            for (d in fills) addPath(pathData = addPathNodes(d), fill = SolidColor(Color.Black))
        }.build()

    val now = icon("now", listOf(circle(12f, 12f, 8f)), listOf(circle(12f, 12f, 2.5f)))
    val sky = icon("sky", listOf(circle(12f, 12f, 9f), circle(12f, 12f, 4.5f), "M12 3v18M3 12h18"))
    val accuracy = icon("accuracy", listOf("M3 17l5-6 4 3 5-7 4 4"))
    val tools = icon("tools", listOf("M4 4h6.5v6.5H4z", "M13.5 4H20v6.5h-6.5z", "M4 13.5h6.5V20H4z", "M13.5 13.5H20V20h-6.5z"))
    val share = icon("share", listOf("M4 12v7a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-7", "M12 3v12", "M7 8l5-5 5 5"))
    val settings = icon(
        "settings",
        listOf(circle(12f, 12f, 3f), "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"),
    )
    val compass = icon("compass", listOf(circle(12f, 12f, 9f), "M15.5 8.5l-2 5-5 2 2-5z"))
    val level = icon("level", listOf("M6 9h12a3 3 0 0 1 0 6H6a3 3 0 0 1 0-6z", circle(12f, 12f, 1.8f)))
    val pin = icon("pin", listOf("M12 21s-6-5.5-6-11a6 6 0 0 1 12 0c0 5.5-6 11-6 11z", circle(12f, 10f, 2f)))
    val track = icon("track", listOf("M4 18c3-6 5 2 8-4s5-2 8-8", circle(4f, 18f, 1.5f)))
    val log = icon("log", listOf("M5 7h14M5 12h10M5 17h12"))
    val sensors = icon("sensors", listOf("M4 12h3l2-6 4 12 2-6h5"))
    val copy = icon("copy", listOf("M9 9h10v10H9z", "M5 15V5h10"))
    val back = icon("back", listOf("M15 5l-7 7 7 7"))
    val plus = icon("plus", listOf("M12 5v14M5 12h14"))
    val trash = icon("trash", listOf("M4 7h16", "M9 7V4h6v3", "M6 7l1 13h10l1-13"))
    val export = icon("export", listOf("M12 4v11", "M7 10l5 5 5-5", "M5 20h14"))
    val edit = icon("edit", listOf("M4 20h4l11-11-4-4L4 16z"))
    val map = icon("map", listOf("M3 6l6-3 6 3 6-3v15l-6 3-6-3-6 3z", "M9 3v15M15 6v15"))
    val swap = icon("swap", listOf("M7 7h11l-3-3", "M17 17H6l3 3"))
    val arrow = icon("arrow", emptyList(), listOf("M12 2l7 19-7-4-7 4z"))
}
