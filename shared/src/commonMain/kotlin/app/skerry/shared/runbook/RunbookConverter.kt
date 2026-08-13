package app.skerry.shared.runbook

import app.skerry.shared.snippet.Snippet

/**
 * Conversions between snippets and runbooks. Pure, stateless mapping — the callers own the UI
 * (name-conflict check) and persist the result through their managers.
 *
 * Snippets and runbooks share the same `${{…}}` variable syntax and both draw machine values once
 * per run, so a converted command keeps its placeholders as-is: a `${{uuid}}` that named a file in
 * step 2 of a runbook still names the same file when the merged script runs as a snippet.
 */
object RunbookConverter {

    /**
     * Converts [snippet] into a single-step runbook. The step gets [RunbookStep.confirm] = true
     * (the default) on purpose: a converted snippet is usually a hands-on command, and an explicit
     * go-ahead per step is the safer default.
     */
    fun snippetToRunbook(snippet: Snippet): Runbook = Runbook(
        id = "",
        label = snippet.label,
        steps = listOf(
            RunbookStep.Command(
                id = "",
                command = snippet.command,
                // confirm defaults to true; named here so the intent survives refactors.
                confirm = true,
            )
        ),
        tags = snippet.tags,
    )

    /**
     * Converts [runbook] into a snippet by joining every [RunbookStep.Command] step with newlines —
     * a snippet's command is a script, so the whole procedure fits in one. Transfer steps can't be
     * expressed as commands, so they are skipped; the returned count lets the UI say so. Returns
     * the snippet and the number of skipped transfer steps.
     */
    fun runbookToSnippet(runbook: Runbook): Pair<Snippet, Int> {
        val commands = runbook.steps.filterIsInstance<RunbookStep.Command>().map { it.command }
        val skipped = runbook.steps.size - commands.size
        val snippet = Snippet(
            id = "",
            label = runbook.label,
            command = commands.joinToString("\n"),
            tags = runbook.tags,
        )
        return snippet to skipped
    }
}
