package app.skerry.ui.snippet

import androidx.compose.runtime.Composable
import app.skerry.ui.design.HelpExample
import app.skerry.ui.design.HelpSection
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.help_example_clipboard
import app.skerry.ui.generated.resources.help_example_date
import app.skerry.ui.generated.resources.help_example_param
import app.skerry.ui.generated.resources.help_example_param_options
import app.skerry.ui.generated.resources.help_example_param_options_cmd
import app.skerry.ui.generated.resources.help_example_param_cmd
import app.skerry.ui.generated.resources.help_example_random
import app.skerry.ui.generated.resources.help_example_time
import app.skerry.ui.generated.resources.help_example_timestamp
import app.skerry.ui.generated.resources.help_example_uuid
import app.skerry.ui.generated.resources.help_example_vault
import app.skerry.ui.generated.resources.help_example_vault_cmd
import app.skerry.ui.generated.resources.help_example_vault_key
import app.skerry.ui.generated.resources.help_example_vault_key_cmd
import app.skerry.ui.generated.resources.help_examples_intro
import app.skerry.ui.generated.resources.help_snippet_intro
import app.skerry.ui.generated.resources.help_snippet_title
import app.skerry.ui.generated.resources.help_snippet_variables
import org.jetbrains.compose.resources.stringResource

/** Content of the snippet help dialog, shared by the desktop section and the mobile screen. */
@Composable
internal fun snippetHelpSections(): List<HelpSection> = listOf(
    HelpSection(stringResource(Res.string.help_snippet_intro)),
    HelpSection(stringResource(Res.string.help_snippet_variables)),
    HelpSection(stringResource(Res.string.help_examples_intro)),
)

@Composable
internal fun snippetHelpTitle(): String = stringResource(Res.string.help_snippet_title)

@Composable
internal fun snippetHelpExamples(): List<HelpExample> = listOf(
    HelpExample(stringResource(Res.string.help_example_date), "cp /etc/nginx/nginx.conf nginx.conf.bak-\${{date:YYYY-MM-DD}}"),
    HelpExample(stringResource(Res.string.help_example_time), "echo \"[\${{time:HH:mm:ss}}] done\" >> /tmp/deploy.log"),
    HelpExample(stringResource(Res.string.help_example_timestamp), "mkdir backup-\${{timestamp}}"),
    HelpExample(stringResource(Res.string.help_example_uuid), "echo \"id: \${{uuid}}\""),
    HelpExample(stringResource(Res.string.help_example_random), "echo \"new password: \${{random:16,special}}\""),
    HelpExample(stringResource(Res.string.help_example_clipboard), "echo \"\${{clipboard}}\" > /tmp/paste.txt"),
    HelpExample(stringResource(Res.string.help_example_vault), stringResource(Res.string.help_example_vault_cmd)),
    HelpExample(stringResource(Res.string.help_example_vault_key), stringResource(Res.string.help_example_vault_key_cmd)),
    HelpExample(stringResource(Res.string.help_example_param), stringResource(Res.string.help_example_param_cmd)),
    HelpExample(stringResource(Res.string.help_example_param_options), stringResource(Res.string.help_example_param_options_cmd)),
)
