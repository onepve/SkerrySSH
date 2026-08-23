package app.skerry.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.vault.BackupImportMode
import app.skerry.ui.design.FieldLabel
import app.skerry.ui.design.GhostButton
import app.skerry.ui.design.ModalScrim
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.ToggleRow
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.settings_backup_export
import app.skerry.ui.generated.resources.settings_backup_export_action
import app.skerry.ui.generated.resources.settings_backup_export_encrypted
import app.skerry.ui.generated.resources.settings_backup_export_encrypted_sub
import app.skerry.ui.generated.resources.settings_backup_export_plain
import app.skerry.ui.generated.resources.settings_backup_export_plain_sub
import app.skerry.ui.generated.resources.settings_backup_export_plain_warning
import app.skerry.ui.generated.resources.settings_backup_import
import app.skerry.ui.generated.resources.settings_backup_import_action
import app.skerry.ui.generated.resources.settings_backup_import_confirm
import app.skerry.ui.generated.resources.settings_backup_import_records
import app.skerry.ui.generated.resources.settings_backup_merge
import app.skerry.ui.generated.resources.settings_backup_merge_sub
import app.skerry.ui.generated.resources.settings_backup_pw_hint
import app.skerry.ui.generated.resources.settings_backup_pw_label
import app.skerry.ui.generated.resources.settings_backup_replace
import app.skerry.ui.generated.resources.settings_backup_replace_sub
import app.skerry.ui.generated.resources.settings_backup_encrypted_note
import app.skerry.ui.generated.resources.settings_cancel
import app.skerry.ui.sync.SyncField
import app.skerry.ui.theme.Skerry
import org.jetbrains.compose.resources.stringResource

/**
 * Export dialog: confirm the master password, choose the export form, then export. The default is
 * sealed with a key derived from the master password (vault records are stored encrypted, so the
 * export stays encrypted too). The alternative is a fully-decrypted plaintext export — every record
 * read out decrypted into human-readable JSON, meant for migrating terminals or eyeballing the
 * data. Both forms require the master password first (the plaintext form holds private keys); the
 * password only derives the backup key (and verifies against the vault before the records are
 * read); it never leaves the machine.
 */
@Composable
fun BackupExportDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onExport: (password: CharArray, encrypt: Boolean) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var encrypt by remember { mutableStateOf(true) }

    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
        ) {
            Txt(stringResource(Res.string.settings_backup_export), color = Skerry.colors.text, size = 16.sp, weight = FontWeight.SemiBold)
            Box(Modifier.height(14.dp))
            Txt(stringResource(Res.string.settings_backup_pw_hint), color = Skerry.colors.faint, size = 11.5.sp, lineHeight = 16.sp)
            Box(Modifier.height(10.dp))
            FieldLabel(stringResource(Res.string.settings_backup_pw_label), top = 0.dp)
            SyncField(
                placeholder = "••••••••",
                value = password,
                icon = "lock",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                secret = true,
                onChange = { password = it },
            )
            Box(Modifier.height(10.dp))
            BackupExportModeOption(
                label = stringResource(Res.string.settings_backup_export_encrypted),
                sub = stringResource(Res.string.settings_backup_export_encrypted_sub),
                selected = encrypt,
                onClick = { encrypt = true },
            )
            Box(Modifier.height(8.dp))
            BackupExportModeOption(
                label = stringResource(Res.string.settings_backup_export_plain),
                sub = stringResource(Res.string.settings_backup_export_plain_sub),
                selected = !encrypt,
                onClick = { encrypt = false },
            )
            if (!encrypt) {
                Txt(stringResource(Res.string.settings_backup_export_plain_warning), color = Skerry.colors.sunset, size = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 10.dp))
            }
            if (error != null) {
                Txt(error, color = Skerry.colors.sunset, size = 11.5.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Txt(stringResource(Res.string.settings_cancel), color = Skerry.colors.dim, size = 12.5.sp)
                }
                PrimaryButton(
                    stringResource(Res.string.settings_backup_export_action),
                    onClick = { onExport(password.toCharArray(), encrypt) },
                    enabled = password.isNotEmpty() && !busy,
                    bg = if (password.isNotEmpty() && !busy) Skerry.colors.cyan else Skerry.colors.cyan10,
                    fg = if (password.isNotEmpty() && !busy) Skerry.colors.ink else Skerry.colors.faint,
                )
            }
        }
    }
}

@Composable
private fun BackupExportModeOption(label: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(Skerry.colors.bg)
            .border(1.dp, if (selected) Skerry.colors.cyan else Skerry.colors.line, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Txt(if (selected) "◉" else "○", color = if (selected) Skerry.colors.cyan else Skerry.colors.faint, size = 13.sp)
        Column {
            Txt(label, color = Skerry.colors.text, size = 12.5.sp, weight = FontWeight.Medium)
            Txt(sub, color = Skerry.colors.dim, size = 11.sp)
        }
    }
}

/**
 * Import dialog: shows what the file holds, asks for the master password only when the file is
 * sealed, and offers merge (LWW, safe) or replace (destructive — the button asks for a second
 * click to confirm).
 */
@Composable
fun BackupImportDialog(
    records: Int?,
    encrypted: Boolean,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onImport: (password: CharArray?, mode: BackupImportMode) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var replaceArmed by remember { mutableStateOf(false) }

    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
        ) {
            Txt(stringResource(Res.string.settings_backup_import), color = Skerry.colors.text, size = 16.sp, weight = FontWeight.SemiBold)
            Box(Modifier.height(14.dp))
            if (records != null) {
                Txt(stringResource(Res.string.settings_backup_import_records, records), color = Skerry.colors.text, size = 12.sp)
            } else {
                Txt(stringResource(Res.string.settings_backup_encrypted_note), color = Skerry.colors.faint, size = 11.5.sp, lineHeight = 16.sp)
            }
            if (encrypted) {
                Box(Modifier.height(10.dp))
                FieldLabel(stringResource(Res.string.settings_backup_pw_label), top = 0.dp)
                SyncField(
                    placeholder = "••••••••",
                    value = password,
                    icon = "lock",
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    secret = true,
                    onChange = { password = it },
                )
            }
            Box(Modifier.height(14.dp))
            ToggleRow(
                label = stringResource(Res.string.settings_backup_merge),
                subtitle = stringResource(Res.string.settings_backup_merge_sub),
                on = !replaceArmed,
                onToggle = { replaceArmed = false },
            )
            Box(Modifier.height(6.dp))
            ToggleRow(
                label = stringResource(Res.string.settings_backup_replace),
                subtitle = stringResource(Res.string.settings_backup_replace_sub),
                on = replaceArmed,
                onToggle = { replaceArmed = true },
            )
            if (error != null) {
                Txt(error, color = Skerry.colors.sunset, size = 11.5.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clip(RoundedCornerShape(7.dp)).clickable(onClick = onDismiss).padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Txt(stringResource(Res.string.settings_cancel), color = Skerry.colors.dim, size = 12.5.sp)
                }
                if (replaceArmed) {
                    GhostButton(
                        stringResource(Res.string.settings_backup_import_confirm),
                        onClick = { onImport(if (encrypted) password.toCharArray() else null, BackupImportMode.REPLACE) },
                        fg = Skerry.colors.sunset,
                        border = Skerry.colors.sunset.copy(alpha = 0.3f),
                        enabled = (!encrypted || password.isNotEmpty()) && !busy,
                    )
                } else {
                    PrimaryButton(
                        stringResource(Res.string.settings_backup_import_action),
                        onClick = { onImport(if (encrypted) password.toCharArray() else null, BackupImportMode.MERGE) },
                        enabled = (!encrypted || password.isNotEmpty()) && !busy,
                        bg = if ((!encrypted || password.isNotEmpty()) && !busy) Skerry.colors.cyan else Skerry.colors.cyan10,
                        fg = if ((!encrypted || password.isNotEmpty()) && !busy) Skerry.colors.ink else Skerry.colors.faint,
                    )
                }
            }
        }
    }
}
