package app.skerry.ui.runbook

import androidx.compose.runtime.Composable
import app.skerry.ui.design.HelpExample
import app.skerry.ui.design.HelpSection
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.help_runbook_examples_intro
import app.skerry.ui.generated.resources.help_runbook_example_disk
import app.skerry.ui.generated.resources.help_runbook_example_install
import app.skerry.ui.generated.resources.help_runbook_example_load
import app.skerry.ui.generated.resources.help_runbook_flow
import app.skerry.ui.generated.resources.help_runbook_intro
import app.skerry.ui.generated.resources.help_runbook_sample_disk
import app.skerry.ui.generated.resources.help_runbook_sample_install
import app.skerry.ui.generated.resources.help_runbook_sample_load
import app.skerry.ui.generated.resources.help_runbook_step_check_installed
import app.skerry.ui.generated.resources.help_runbook_step_install
import app.skerry.ui.generated.resources.help_runbook_step_largest_dirs
import app.skerry.ui.generated.resources.help_runbook_step_memory
import app.skerry.ui.generated.resources.help_runbook_step_top_processes
import app.skerry.ui.generated.resources.help_runbook_step_uptime
import app.skerry.ui.generated.resources.help_runbook_step_usage
import app.skerry.ui.generated.resources.help_runbook_step_verify
import app.skerry.ui.generated.resources.help_runbook_title
import app.skerry.ui.generated.resources.help_snippet_variables
import app.skerry.shared.runbook.RunbookStep
import org.jetbrains.compose.resources.stringResource

/** Content of the runbook help dialog, shared by the desktop section and the mobile screen. */
@Composable
internal fun runbookHelpSections(): List<HelpSection> = listOf(
    HelpSection(stringResource(Res.string.help_runbook_intro)),
    HelpSection(stringResource(Res.string.help_runbook_flow)),
    HelpSection(stringResource(Res.string.help_snippet_variables)),
    HelpSection(stringResource(Res.string.help_runbook_examples_intro)),
)

@Composable
internal fun runbookHelpTitle(): String = stringResource(Res.string.help_runbook_title)

/** Stable keys tying each help example to the template it creates. */
private const val KEY_LOAD = "load"
private const val KEY_INSTALL = "install"
private const val KEY_DISK = "disk"

/**
 * Three independent entries, one per example runbook — tapping an entry creates only its own
 * template (see [runbookExampleTemplatesByKey]).
 */
@Composable
internal fun runbookHelpExamples(): List<HelpExample> {
    val templates = runbookExampleTemplates()
    return listOf(
        HelpExample(
            stringResource(Res.string.help_runbook_example_load),
            (templates[0].steps.firstOrNull() as? RunbookStep.Command)?.command ?: "",
            key = KEY_LOAD,
        ),
        HelpExample(
            stringResource(Res.string.help_runbook_example_install),
            (templates[1].steps.firstOrNull() as? RunbookStep.Command)?.command ?: "",
            key = KEY_INSTALL,
        ),
        HelpExample(
            stringResource(Res.string.help_runbook_example_disk),
            (templates[2].steps.firstOrNull() as? RunbookStep.Command)?.command ?: "",
            key = KEY_DISK,
        ),
    )
}

/**
 * Example templates keyed by [HelpExample.key], for an [HelpDialog.onExampleAction] handler: fetch
 * this in a @Composable context and look up `byKey[example.key]` inside the click lambda.
 */
@Composable
internal fun runbookExampleTemplatesByKey(): Map<String, RunbookDraft> {
    val templates = runbookExampleTemplates()
    return mapOf(
        KEY_LOAD to templates[0],
        KEY_INSTALL to templates[1],
        KEY_DISK to templates[2],
    )
}

/**
 * The three runbooks created by the help dialog's example entries. Step titles are localized (the
 * commands themselves are shell commands and stay as-is); each step is shown with its title in the
 * editor, so the sample follows the app language.
 */
@Composable
internal fun runbookExampleTemplates(): List<RunbookDraft> = listOf(
    RunbookDraft(
        label = stringResource(Res.string.help_runbook_sample_load),
        description = "system",
        steps = listOf(
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_uptime), command = "uptime"),
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_memory), command = "free -h"),
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_top_processes), command = "top -bn1 | head -15"),
        ),
    ),
    RunbookDraft(
        label = stringResource(Res.string.help_runbook_sample_install),
        description = "package",
        steps = listOf(
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_check_installed), command = "which htop || echo \"htop is not installed\""),
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_install), command = "sudo apt install -y htop"),
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_verify), command = "htop --version"),
        ),
    ),
    RunbookDraft(
        label = stringResource(Res.string.help_runbook_sample_disk),
        description = "system",
        steps = listOf(
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_usage), command = "df -h"),
            RunbookStep.Command(id = "", title = stringResource(Res.string.help_runbook_step_largest_dirs), command = "du -sh /* 2>/dev/null | sort -rh | head -10"),
        ),
    ),
)
