package app.skerry.shared.transfer

import app.skerry.shared.runbook.Runbook
import app.skerry.shared.runbook.RunbookStep
import app.skerry.shared.runbook.RunbookTransferDirection
import app.skerry.shared.runbook.RunbookTransferFile
import app.skerry.shared.runbook.RunbookTransferResult
import app.skerry.shared.runbook.parseRunbookTransfer
import app.skerry.shared.snippet.Snippet
import app.skerry.shared.snippet.SnippetTransferFile
import app.skerry.shared.snippet.SnippetTransferResult
import app.skerry.shared.snippet.parseSnippetTransfer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SnippetTransferTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun snippet_file_round_trips_through_pretty_json() {
        val file = SnippetTransferFile(
            snippets = listOf(
                Snippet(id = "a", label = "磁盘", command = "df -h", tags = listOf("disk")),
                Snippet(id = "b", label = "更新", command = "apt update", shortcut = "Ctrl+Shift+U"),
            ),
        )
        val parsed = assertIs<SnippetTransferResult.Ok>(parseSnippetTransfer(json.encodeToString(file)))
        assertEquals(file.snippets, parsed.file.snippets)
        assertEquals("skerry-snippets", parsed.file.format)
    }

    @Test
    fun snippet_file_with_wrong_format_is_corrupted() {
        assertIs<SnippetTransferResult.Corrupted>(
            parseSnippetTransfer("""{"format":"some-other-app","snippets":[]}"""),
        )
    }

    @Test
    fun snippet_file_with_garbage_is_corrupted() {
        assertIs<SnippetTransferResult.Corrupted>(parseSnippetTransfer("not json at all"))
    }

    @Test
    fun runbook_file_round_trips_steps_with_kind_discriminator() {
        val file = RunbookTransferFile(
            runbooks = listOf(
                Runbook(
                    id = "r1",
                    label = "部署",
                    steps = listOf(
                        RunbookStep.Command(id = "s1", title = "更新", command = "apt update"),
                        RunbookStep.Transfer(id = "s2", title = "上传", localPath = "a", remotePath = "b", direction = RunbookTransferDirection.UPLOAD),
                    ),
                ),
            ),
        )
        val text = json.encodeToString(file)
        assertTrue(text.contains("\"kind\": \"command\""), "no command discriminator in: $text")
        assertTrue(text.contains("\"kind\": \"transfer\""), "no transfer discriminator in: $text")
        val parsed = assertIs<RunbookTransferResult.Ok>(parseRunbookTransfer(text))
        assertEquals(file.runbooks, parsed.file.runbooks)
    }

    @Test
    fun merge_plan_counts_additions_updates_and_keeps_locals() {
        val incoming = listOf(snippet("a"), snippet("b"), snippet("c"))
        val local = listOf(snippet("a"), snippet("x"))
        val plan = planTransfer(incoming, local, TransferMode.MERGE) { it.id }

        assertEquals(2, plan.additions) // b, c
        assertEquals(1, plan.updates) // a
        assertEquals(1, plan.localOnly) // x kept
        assertEquals(4, plan.resultingTotal)
        assertTrue(!plan.isNoOp)
    }

    @Test
    fun replace_plan_counts_local_removals() {
        val incoming = listOf(snippet("a"), snippet("b"))
        val local = listOf(snippet("a"), snippet("x"), snippet("y"))
        val plan = planTransfer(incoming, local, TransferMode.REPLACE) { it.id }

        assertEquals(1, plan.additions) // b
        assertEquals(1, plan.updates) // a
        assertEquals(2, plan.localOnly) // x, y removed
        assertEquals(2, plan.resultingTotal)
    }

    @Test
    fun identical_lists_count_as_updates_not_a_no_op() {
        val list = listOf(snippet("a"), snippet("b"))
        val plan = planTransfer(list, list, TransferMode.MERGE) { it.id }
        assertEquals(2, plan.updates)
        assertTrue(!plan.isNoOp)
    }

    @Test
    fun empty_merge_is_a_no_op_but_empty_replace_wipes_everything() {
        val local = listOf(snippet("a"), snippet("b"))
        assertTrue(planTransfer(emptyList(), local, TransferMode.MERGE) { it.id }.isNoOp)
        val wipe = planTransfer(emptyList(), local, TransferMode.REPLACE) { it.id }
        assertTrue(!wipe.isNoOp)
        assertEquals(2, wipe.localOnly)
        assertEquals(0, wipe.resultingTotal)
    }

    private fun snippet(id: String) = Snippet(id = id, label = "s$id", command = "echo $id")
}
