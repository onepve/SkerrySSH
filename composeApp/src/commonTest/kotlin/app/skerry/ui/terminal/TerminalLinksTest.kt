package app.skerry.ui.terminal

import app.skerry.shared.terminal.TermCell
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalLinksTest {

    @Test
    fun `accepts web schemes with authority`() {
        assertTrue(isSafeLinkUri("https://skerry.app/docs"))
        assertTrue(isSafeLinkUri("http://example.com"))
        assertTrue(isSafeLinkUri("ftp://files.example.com/x"))
        assertTrue(isSafeLinkUri("mailto:dev@skerry.app"))
    }

    @Test
    fun `scheme match is case-insensitive`() {
        assertTrue(isSafeLinkUri("HTTPS://Skerry.App"))
        assertTrue(isSafeLinkUri("MailTo:dev@skerry.app"))
    }

    @Test
    fun `rejects dangerous and non-web schemes`() {
        assertFalse(isSafeLinkUri("file:///etc/passwd"))
        assertFalse(isSafeLinkUri("javascript:alert(1)"))
        assertFalse(isSafeLinkUri("data:text/html,<script>"))
        assertFalse(isSafeLinkUri("ssh://root@host"))
    }

    @Test
    fun `rejects degenerate http without authority`() {
        assertFalse(isSafeLinkUri("http:"))
        assertFalse(isSafeLinkUri("https:evil"))
    }

    @Test
    fun `rejects uris carrying control characters`() {
        // A server could embed \n/\r to corrupt platform URI dispatch.
        val nl = 10.toChar()
        val cr = 13.toChar()
        assertFalse(isSafeLinkUri("https://ok.test${nl}https://evil.test"))
        assertFalse(isSafeLinkUri("https://ok.test${cr}x"))
    }

    // --- Plain-text URL detection (no OSC 8): the char-index level ---

    @Test
    fun `detects a bare https url in surrounding text`() {
        val text = "see https://skerry.app/docs for more"
        val links = detectPlainTextLinks(text)
        assertEquals(1, links.size)
        val link = links.single()
        assertEquals("https://skerry.app/docs", link.uri)
        assertEquals("https://skerry.app/docs", text.substring(link.start, link.endExclusive))
    }

    @Test
    fun `detects multiple urls on one line`() {
        val text = "http://a.test and ftp://b.test/x"
        val uris = detectPlainTextLinks(text).map { it.uri }
        assertEquals(listOf("http://a.test", "ftp://b.test/x"), uris)
    }

    @Test
    fun `ignores text without a url`() {
        assertTrue(detectPlainTextLinks("just a normal line, nothing here").isEmpty())
        // A scheme with no authority must not match (mirrors isSafeLinkUri).
        assertTrue(detectPlainTextLinks("run https:evil now").isEmpty())
        // www. without an explicit scheme is intentionally not linkified.
        assertTrue(detectPlainTextLinks("visit www.skerry.app today").isEmpty())
    }

    @Test
    fun `trims trailing sentence punctuation but keeps balanced parens`() {
        assertEquals("https://a.test", detectPlainTextLinks("go to https://a.test.").single().uri)
        assertEquals("https://a.test/p", detectPlainTextLinks("(see https://a.test/p)").single().uri)
        // A ')' that closes a '(' inside the URL is kept.
        assertEquals(
            "https://en.w.org/wiki/Foo_(bar)",
            detectPlainTextLinks("https://en.w.org/wiki/Foo_(bar)").single().uri,
        )
    }

    @Test
    fun `does not linkify dangerous schemes`() {
        assertTrue(detectPlainTextLinks("file:///etc/passwd").isEmpty())
        assertTrue(detectPlainTextLinks("javascript:alert(1)").isEmpty())
    }

    // --- Plain-text URL detection: mapping onto grid columns and click hit-testing ---

    private fun row(text: String): List<TermCell> = text.map { TermCell(it) }

    @Test
    fun `maps a url onto its grid columns`() {
        val cells = row("x https://a.test y")
        val span = rowLinkSpans(cells).single()
        assertEquals(2, span.start)                 // 'h' column
        assertEquals(2 + "https://a.test".length, span.endExclusive)
        assertEquals("https://a.test", span.uri)
    }

    @Test
    fun `linkAt returns the uri only under the url columns`() {
        val cells = row("go https://a.test")
        assertNull(linkAt(cells, 0))                // 'g'
        assertEquals("https://a.test", linkAt(cells, 3))  // 'h'
        assertEquals("https://a.test", linkAt(cells, cells.lastIndex))
    }

    @Test
    fun `detection is independent of the OSC 8 hyperlink field`() {
        // A cell may already carry an OSC 8 URI; rowLinkSpans still reports the bare text URL. Which one
        // wins (and the no-double-underline skip) is decided by the renderer/click layer, not here.
        val cells = "https://a.test".map { TermCell(it.toString(), hyperlink = "https://osc8.test") }
        assertEquals("https://a.test", rowLinkSpans(cells).single().uri)
    }

    @Test
    fun `plain rows without a scheme allocate nothing and yield no spans`() {
        assertTrue(rowLinkSpans(row("total 42  drwxr-xr-x  root:root  10:30")).isEmpty())
    }

    @Test
    fun `wide cells shift columns so mapping still lands on the url`() {
        // A leading wide glyph (CJK) occupies two columns: [Wide, Continuation].
        val cells = buildList {
            add(TermCell("世", width = app.skerry.shared.terminal.CellWidth.Wide))
            add(TermCell("", width = app.skerry.shared.terminal.CellWidth.Continuation))
            add(TermCell(' '))
            "https://a.test".forEach { add(TermCell(it)) }
        }
        val span = rowLinkSpans(cells).single()
        assertEquals(3, span.start)                 // url starts after wide glyph (cols 0,1) + space (col 2)
        assertEquals("https://a.test", linkAt(cells, 3))
    }
}
