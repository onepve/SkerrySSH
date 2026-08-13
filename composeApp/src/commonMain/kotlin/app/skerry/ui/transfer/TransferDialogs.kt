package app.skerry.ui.transfer

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.transfer.TransferMode
import app.skerry.shared.transfer.TransferPlan
import app.skerry.ui.design.FieldLabel
import app.skerry.ui.design.GhostButton
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.ModalScrim
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.settings_cancel
import app.skerry.ui.generated.resources.transfer_dialog_confirm
import app.skerry.ui.generated.resources.transfer_dialog_merge
import app.skerry.ui.generated.resources.transfer_dialog_merge_sub
import app.skerry.ui.generated.resources.transfer_dialog_replace
import app.skerry.ui.generated.resources.transfer_dialog_replace_sub
import app.skerry.ui.generated.resources.transfer_dialog_summary
import app.skerry.ui.theme.Skerry
import org.jetbrains.compose.resources.stringResource

/** One-shot notice after an import: the outcome, or the reason nothing happened (parse failure). */
@Composable
fun TransferInfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
        ) {
            Txt(title, color = Skerry.colors.text, size = 15.sp, weight = FontWeight.SemiBold)
            Box(Modifier.height(12.dp))
            Txt(message, color = Skerry.colors.dim, size = 12.5.sp)
            Box(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PrimaryButton(stringResource(Res.string.transfer_dialog_confirm), onClick = onDismiss)
            }
        }
    }
}

/**
 * Confirmation for a whole-library import: shows what the file would do (additions/updates/local
 * leftovers) and lets the user pick between [TransferMode.MERGE] (sync changes in, keep the rest)
 * and [TransferMode.REPLACE] (the file is the truth — local leftovers are deleted). Shared by the
 * snippet and runbook libraries.
 */
@Composable
fun TransferImportDialog(
    title: String,
    fileName: String,
    plan: TransferPlan,
    mode: TransferMode,
    onModeChange: (TransferMode) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mono = LocalFonts.current.mono
    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
        ) {
            Txt(title, color = Skerry.colors.text, size = 16.sp, weight = FontWeight.SemiBold)
            Box(Modifier.height(12.dp))
            Txt(fileName, color = Skerry.colors.dim, size = 11.5.sp, font = mono, maxLines = 1)
            Box(Modifier.height(10.dp))
            Txt(
                stringResource(Res.string.transfer_dialog_summary, plan.additions, plan.updates, plan.localOnly),
                color = Skerry.colors.text,
                size = 12.5.sp,
            )
            Box(Modifier.height(14.dp))

            TransferModeOption(
                label = stringResource(Res.string.transfer_dialog_merge),
                sub = stringResource(Res.string.transfer_dialog_merge_sub),
                selected = mode == TransferMode.MERGE,
                onClick = { onModeChange(TransferMode.MERGE) },
            )
            Box(Modifier.height(8.dp))
            TransferModeOption(
                label = stringResource(Res.string.transfer_dialog_replace),
                sub = stringResource(Res.string.transfer_dialog_replace_sub, plan.localOnly),
                selected = mode == TransferMode.REPLACE,
                onClick = { onModeChange(TransferMode.REPLACE) },
            )

            Box(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GhostButton(stringResource(Res.string.settings_cancel), onClick = onDismiss)
                PrimaryButton(stringResource(Res.string.transfer_dialog_confirm), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun TransferModeOption(label: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val mono = LocalFonts.current.mono
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
        Txt(
            if (selected) "◉" else "○",
            color = if (selected) Skerry.colors.cyan else Skerry.colors.faint,
            size = 13.sp,
            font = mono,
        )
        Column {
            Txt(label, color = Skerry.colors.text, size = 12.5.sp, weight = FontWeight.Medium)
            Txt(sub, color = Skerry.colors.dim, size = 11.sp)
        }
    }
}
