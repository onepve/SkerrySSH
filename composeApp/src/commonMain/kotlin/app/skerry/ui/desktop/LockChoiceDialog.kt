package app.skerry.ui.desktop

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.ModalScrim
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.Txt
import app.skerry.ui.theme.Skerry
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.shell_lock_choice_title
import app.skerry.ui.generated.resources.shell_lock_choice_soft_title
import app.skerry.ui.generated.resources.shell_lock_choice_soft_desc
import app.skerry.ui.generated.resources.shell_lock_choice_hard_title
import app.skerry.ui.generated.resources.shell_lock_choice_hard_desc
import app.skerry.ui.generated.resources.shell_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Lock choice dialog: shown when clicking the lock button and PIN is enabled.
 * Offers two options: "Fast lock" (PIN soft lock, keys stay in memory) and
 * "Completely lock" (hard lock, keys wiped, requires master password).
 */
@Composable
fun LockChoiceDialog(
    onSoftLock: () -> Unit,
    onHardLock: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
        ) {
            Txt(stringResource(Res.string.shell_lock_choice_title), color = Skerry.colors.text, size = 16.sp, weight = FontWeight.SemiBold)
            Box(Modifier.height(18.dp))

            // Option 1: Soft lock
            LockChoiceRow(
                title = stringResource(Res.string.shell_lock_choice_soft_title),
                subtitle = stringResource(Res.string.shell_lock_choice_soft_desc),
                onClick = {
                    onSoftLock()
                    onDismiss()
                },
            )

            Box(Modifier.height(12.dp))

            // Option 2: Hard lock
            LockChoiceRow(
                title = stringResource(Res.string.shell_lock_choice_hard_title),
                subtitle = stringResource(Res.string.shell_lock_choice_hard_desc),
                onClick = {
                    onHardLock()
                    onDismiss()
                },
            )

            Box(Modifier.height(18.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Txt(stringResource(Res.string.shell_cancel), color = Skerry.colors.dim, size = 12.5.sp)
            }
        }
    }
}

@Composable
private fun LockChoiceRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, Skerry.colors.cyan20, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Skerry.colors.cyan14))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Txt(title, color = Skerry.colors.text, size = 13.sp, weight = FontWeight.Medium)
            Txt(subtitle, color = Skerry.colors.dim, size = 11.5.sp)
        }
    }
}
