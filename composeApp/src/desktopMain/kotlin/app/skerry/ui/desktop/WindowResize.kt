package app.skerry.ui.desktop

import java.awt.Rectangle

/**
 * Which window border a resize drag grabs. [dx]/[dy] mark the moving side per axis:
 * -1 — the left/top side moves, 1 — the right/bottom side, 0 — the axis is untouched.
 */
enum class ResizeEdge(val dx: Int, val dy: Int) {
    Left(-1, 0), Right(1, 0), Top(0, -1), Bottom(0, 1),
    TopLeft(-1, -1), TopRight(1, -1), BottomLeft(-1, 1), BottomRight(1, 1),
}

/**
 * New window bounds after dragging [edge] by ([deltaX], [deltaY]) px from [start].
 * When the left/top side moves, the opposite side stays fixed (x/y compensate the width/height
 * change) — including when the size clamps at [minWidth]/[minHeight].
 */
fun resizedWindowBounds(
    start: Rectangle,
    edge: ResizeEdge,
    deltaX: Int,
    deltaY: Int,
    minWidth: Int,
    minHeight: Int,
): Rectangle {
    var width = start.width
    var x = start.x
    if (edge.dx != 0) {
        width = (start.width + edge.dx * deltaX).coerceAtLeast(minWidth)
        if (edge.dx < 0) x = start.x + start.width - width
    }
    var height = start.height
    var y = start.y
    if (edge.dy != 0) {
        height = (start.height + edge.dy * deltaY).coerceAtLeast(minHeight)
        if (edge.dy < 0) y = start.y + start.height - height
    }
    return Rectangle(x, y, width, height)
}
