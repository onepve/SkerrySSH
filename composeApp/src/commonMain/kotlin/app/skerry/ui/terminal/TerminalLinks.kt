package app.skerry.ui.terminal

import app.skerry.shared.terminal.TermCell

/**
 * Whether an OSC 8 hyperlink is safe to open on Ctrl+click. The URI comes from an untrusted ssh
 * server, so the gate is strict: rejects any control bytes (C0/DEL), and allows only web schemes with
 * authority (`http(s)://`, `ftp://`) or `mailto:` — file:, javascript:, data:, and degenerate forms
 * like `http:` (no `://`) are rejected. Pure function, kept out of the Composable for unit testing.
 */
internal fun isSafeLinkUri(uri: String): Boolean {
    if (uri.any { it.code < 0x20 || it.code == 0x7F }) return false
    return uri.startsWith("https://", ignoreCase = true) ||
        uri.startsWith("http://", ignoreCase = true) ||
        uri.startsWith("ftp://", ignoreCase = true) ||
        uri.startsWith("mailto:", ignoreCase = true)
}

/** A URL found in plain row text. [start]/[endExclusive] are indices — into the source string for
 *  [detectPlainTextLinks], into grid columns for [rowLinkSpans]. */
internal data class TextLinkSpan(val start: Int, val endExclusive: Int, val uri: String)

// Explicit web schemes only (no bare `www.`), so a match already carries an authority. The body runs
// to the next whitespace; trailing sentence punctuation is trimmed afterwards.
private val PLAIN_LINK_REGEX = Regex("(?i)(?:https?|ftp)://[^\\s]+")

// Trailing chars that are usually sentence punctuation, not part of the URL. ')' is handled
// separately so balanced parens inside a URL (e.g. Wikipedia's `Foo_(bar)`) survive.
private const val LINK_TRAILING_PUNCT = ".,;:!?]}>\"'"

/** Right-trim sentence punctuation from a matched URL, keeping a ')' that closes a '(' in the URL. */
private fun trimUrlTail(url: String): String {
    val opens = url.count { it == '(' }
    var closes = url.count { it == ')' }
    var end = url.length
    while (end > 0) {
        val ch = url[end - 1]
        when {
            // A ')' is part of the URL only while there's still an unmatched '(' to its left.
            ch == ')' -> if (closes > opens) { closes--; end-- } else break
            ch in LINK_TRAILING_PUNCT -> end--
            else -> break
        }
    }
    return if (end == url.length) url else url.substring(0, end)
}

/**
 * Detect bare http(s)/ftp URLs in a line of plain text (URLs the server printed without OSC 8
 * markup — MOTD banners, `curl -I` output). Returns spans as string indices; each URI is validated
 * through [isSafeLinkUri], so degenerate/dangerous forms never surface as clickable.
 */
internal fun detectPlainTextLinks(text: String): List<TextLinkSpan> {
    var out: MutableList<TextLinkSpan>? = null
    for (m in PLAIN_LINK_REGEX.findAll(text)) {
        val uri = trimUrlTail(m.value)
        if (uri.isEmpty() || !isSafeLinkUri(uri)) continue
        (out ?: ArrayList<TextLinkSpan>().also { out = it })
            .add(TextLinkSpan(m.range.first, m.range.first + uri.length, uri))
    }
    return out ?: emptyList()
}

/** Cheap allocation-free scan for a `://` scheme separator anywhere in the row (across cell borders). */
private fun rowHasSchemeMarker(row: List<TermCell>): Boolean {
    var p2 = ' '
    var p1 = ' '
    for (cell in row) {
        for (ch in cell.text) {
            if (p2 == ':' && p1 == '/' && ch == '/') return true
            p2 = p1
            p1 = ch
        }
    }
    return false
}

/**
 * Same detection as [detectPlainTextLinks], but over a grid row, returning spans in **column**
 * coordinates. A wide glyph occupies two columns (its [CellWidth.Continuation] cell has empty text),
 * so string index and column diverge — the char→column map keeps clicks landing on the URL.
 */
internal fun rowLinkSpans(row: List<TermCell>): List<TextLinkSpan> {
    // Runs per visible row on every Canvas draw (scroll, cursor blink, PTY output) — so bail out with
    // zero allocation on the overwhelmingly common URL-free row before touching StringBuilder/regex.
    if (!rowHasSchemeMarker(row)) return emptyList()
    val sb = StringBuilder(row.size)
    val colOf = ArrayList<Int>(row.size)
    for (c in row.indices) {
        for (ch in row[c].text) { sb.append(ch); colOf.add(c) }
    }
    if (colOf.isEmpty()) return emptyList()
    val found = detectPlainTextLinks(sb.toString())
    if (found.isEmpty()) return emptyList()
    return found.map { s -> TextLinkSpan(colOf[s.start], colOf[s.endExclusive - 1] + 1, s.uri) }
}

/** The plain-text URL under column [col] in [row], or `null`. Used for Ctrl+click hit-testing. */
internal fun linkAt(row: List<TermCell>, col: Int): String? =
    rowLinkSpans(row).firstOrNull { col >= it.start && col < it.endExclusive }?.uri
