package app.skerry.shared.runbook

import app.skerry.shared.snippet.Snippet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunbookConverterTest {

    private fun snippet(label: String = "check disk", command: String = "df -h") = Snippet(
        id = "snip-1",
        label = label,
        command = command,
        tags = listOf("ops", "disk"),
    )

    @Test
    fun snippetToRunbook_makesSingleStepWithConfirm() {
        val runbook = RunbookConverter.snippetToRunbook(snippet())

        assertEquals("check disk", runbook.label)
        assertEquals(listOf("ops", "disk"), runbook.tags)
        assertEquals(1, runbook.steps.size)
        val step = runbook.steps.single() as RunbookStep.Command
        assertEquals("df -h", step.command)
        // Converting is a hands-on moment: the step asks for an explicit go-ahead by default.
        assertTrue(step.confirm)
    }

    @Test
    fun snippetToRunbook_keepsPlaceholders() {
        val runbook = RunbookConverter.snippetToRunbook(
            snippet(command = "mkdir /tmp/\${'$'}{{uuid}} && echo \${'$'}{{random:8}}"),
        )
        val step = runbook.steps.single() as RunbookStep.Command
        assertEquals("mkdir /tmp/\${'$'}{{uuid}} && echo \${'$'}{{random:8}}", step.command)
    }

    @Test
    fun runbookToSnippet_joinsCommandSteps() {
        val runbook = Runbook(
            id = "rb-1",
            label = "Deploy",
            tags = listOf("deploy"),
            steps = listOf(
                RunbookStep.Command(id = "s1", command = "git pull"),
                RunbookStep.Command(id = "s2", command = "systemctl restart nginx"),
                RunbookStep.Command(id = "s3", command = "systemctl status nginx"),
            ),
        )

        val (converted, skipped) = RunbookConverter.runbookToSnippet(runbook)

        assertEquals("Deploy", converted.label)
        assertEquals(listOf("deploy"), converted.tags)
        assertEquals("git pull\nsystemctl restart nginx\nsystemctl status nginx", converted.command)
        assertEquals(0, skipped)
    }

    @Test
    fun runbookToSnippet_skipsTransferSteps() {
        val runbook = Runbook(
            id = "rb-2",
            label = "Backup",
            steps = listOf(
                RunbookStep.Command(id = "s1", command = "tar czf /tmp/backup.tar.gz /data"),
                RunbookStep.Transfer(
                    id = "s2",
                    title = "fetch",
                    localPath = "/tmp/backup.tar.gz",
                    remotePath = "/home/user/backup.tar.gz",
                    direction = RunbookTransferDirection.DOWNLOAD,
                ),
                RunbookStep.Command(id = "s3", command = "rm /tmp/backup.tar.gz"),
            ),
        )

        val (converted, skipped) = RunbookConverter.runbookToSnippet(runbook)

        assertEquals("tar czf /tmp/backup.tar.gz /data\nrm /tmp/backup.tar.gz", converted.command)
        assertEquals(1, skipped)
    }

    @Test
    fun runbookToSnippet_emptySteps_blankCommand() {
        val (converted, skipped) = RunbookConverter.runbookToSnippet(Runbook(id = "rb-3", label = "Empty"))
        assertEquals("", converted.command)
        assertEquals(0, skipped)
    }
}
