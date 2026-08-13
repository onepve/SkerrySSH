package app.skerry.ui.convert

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
import app.skerry.ui.design.FieldLabel
import app.skerry.ui.design.ModalScrim
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.settings_cancel
import app.skerry.ui.sync.SyncField
import app.skerry.ui.theme.Skerry
import org.jetbrains.compose.resources.stringResource

/**
 * Shared dialog for converting a snippet into a runbook (or vice versa): a name field with a live
 * duplicate check, and an optional info line (e.g. how many transfer steps were skipped).
 * Confirmation is disabled until the name is non-blank and doesn't collide with an existing entry.
 */
@Composable
fun ConvertDialog(
    title: String,
    initialName: String,
    nameLabel: String,
    confirmLabel: String,
    nameConflict: (String) -> Boolean,
    conflictMessage: String,
    onConfirm: (name: String) -> Unit,
    onDismiss: () -> Unit,
    info: String? = null,
) {
    var name by remember { mutableStateOf(initialName) }
    val conflict = name.isNotBlank() && nameConflict(name.trim())
    val canSubmit = name.isNotBlank() && !conflict

    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
        ) {
            Txt(title, color = Skerry.colors.text, size = 16.sp, weight = FontWeight.SemiBold)
            Box(Modifier.height(16.dp))
            FieldLabel(nameLabel, top = 0.dp)
            SyncField(
                placeholder = initialName,
                value = name,
                icon = "edit",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
                // The old label is pre-filled: select it on focus so typing replaces it.
                selectAllOnFocus = true,
                onChange = { name = it },
            )
            if (conflict) {
                Txt(conflictMessage, color = Skerry.colors.storm, size = 11.5.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (info != null) {
                Txt(info, color = Skerry.colors.faint, size = 11.5.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 12.dp))
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
                    confirmLabel,
                    onClick = { onConfirm(name.trim()) },
                    enabled = canSubmit,
                    bg = if (canSubmit) Skerry.colors.cyan else Skerry.colors.cyan10,
                    fg = if (canSubmit) Skerry.colors.ink else Skerry.colors.faint,
                )
            }
        }
    }
}
