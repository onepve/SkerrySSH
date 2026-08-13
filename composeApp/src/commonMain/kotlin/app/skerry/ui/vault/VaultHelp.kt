package app.skerry.ui.vault

import androidx.compose.runtime.Composable
import app.skerry.ui.design.HelpSection
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.help_vault_categories
import app.skerry.ui.generated.resources.help_vault_intro
import app.skerry.ui.generated.resources.help_vault_title
import app.skerry.ui.generated.resources.help_vault_variable
import org.jetbrains.compose.resources.stringResource

/** Content of the vault help dialog, shared by the desktop section and the mobile screen. */
@Composable
internal fun vaultHelpSections(): List<HelpSection> = listOf(
    HelpSection(stringResource(Res.string.help_vault_intro)),
    HelpSection(stringResource(Res.string.help_vault_categories)),
    HelpSection(stringResource(Res.string.help_vault_variable)),
)

@Composable
internal fun vaultHelpTitle(): String = stringResource(Res.string.help_vault_title)
