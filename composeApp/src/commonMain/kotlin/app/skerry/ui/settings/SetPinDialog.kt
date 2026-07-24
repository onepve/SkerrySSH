package app.skerry.ui.settings

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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.ModalScrim
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt
import app.skerry.ui.design.consumeClicks
import app.skerry.ui.theme.Skerry
import app.skerry.ui.vault.sha256
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.shell_softlock_set_title
import app.skerry.ui.generated.resources.shell_softlock_change_title
import app.skerry.ui.generated.resources.shell_softlock_set_desc
import app.skerry.ui.generated.resources.shell_softlock_pin_label
import app.skerry.ui.generated.resources.shell_softlock_confirm_label
import app.skerry.ui.generated.resources.shell_softlock_too_short
import app.skerry.ui.generated.resources.shell_softlock_too_long
import app.skerry.ui.generated.resources.shell_softlock_mismatch
import app.skerry.ui.generated.resources.shell_softlock_confirm
import app.skerry.ui.generated.resources.shell_cancel
import org.jetbrains.compose.resources.stringResource

/**
 * Dialog for setting (or changing) the desktop quick-unlock PIN.
 * [currentHash] — existing hash (empty if first-time setup).
 * [onSet] — called with the new SHA-256 hash on confirm.
 * [onDismiss] — close without changes (backdrop click or cancel).
 */
@Composable
fun SetPinDialog(
    currentHash: String,
    onSet: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isNewSetup = currentHash.isEmpty()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val tooShort = pin.isNotEmpty() && pin.length < 4
    val tooLong = pin.length > 8
    val mismatch = confirm.isNotEmpty() && pin != confirm
    val canSubmit = pin.length in 4..8 && pin == confirm

    fun submit() {
        if (!canSubmit) return
        val hash = sha256("skerry-soft-lock" + pin)
        onSet(hash)
    }

    ModalScrim(onDismiss = onDismiss) {
        Column(
            Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .consumeClicks()
                .padding(26.dp),
        ) {
            Txt(
                if (isNewSetup) stringResource(Res.string.shell_softlock_set_title) else stringResource(Res.string.shell_softlock_change_title),
                color = Skerry.colors.text,
                size = 16.sp,
                weight = FontWeight.SemiBold,
            )
            Box(Modifier.height(8.dp))
            Txt(
                stringResource(Res.string.shell_softlock_set_desc),
                color = Skerry.colors.dim,
                size = 11.5.sp,
            )
            Box(Modifier.height(16.dp))

            SetPinField(
                value = pin,
                placeholder = stringResource(Res.string.shell_softlock_pin_label),
                imeAction = ImeAction.Next,
                onValueChange = { pin = it; error = null },
            )

            if (tooShort) Txt(stringResource(Res.string.shell_softlock_too_short), color = Skerry.colors.amber, size = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
            if (tooLong) Txt(stringResource(Res.string.shell_softlock_too_long), color = Skerry.colors.amber, size = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

            Box(Modifier.height(12.dp))

            SetPinField(
                value = confirm,
                placeholder = stringResource(Res.string.shell_softlock_confirm_label),
                imeAction = ImeAction.Done,
                onSubmit = { submit() },
                onValueChange = { confirm = it; error = null },
            )

            if (mismatch) Txt(stringResource(Res.string.shell_softlock_mismatch), color = Skerry.colors.amber, size = 11.5.sp, modifier = Modifier.padding(top = 4.dp))
            if (error != null) Txt(error!!, color = Skerry.colors.storm, size = 11.5.sp, modifier = Modifier.padding(top = 4.dp))

            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                ) {
                    Txt(stringResource(Res.string.shell_cancel), color = Skerry.colors.dim, size = 12.5.sp)
                }
                PrimaryButton(
                    stringResource(Res.string.shell_softlock_confirm),
                    onClick = { submit() },
                    enabled = canSubmit,
                    bg = if (canSubmit) Skerry.colors.cyan else Skerry.colors.cyan10,
                    fg = if (canSubmit) Skerry.colors.ink else Skerry.colors.faint,
                )
            }
        }
    }
}

@Composable
private fun SetPinField(
    value: String,
    placeholder: String,
    imeAction: ImeAction,
    onSubmit: () -> Unit = {},
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        textStyle = TextStyle(color = Skerry.colors.text, fontSize = 14.sp, fontFamily = LocalFonts.current.ui),
        cursorBrush = SolidColor(Skerry.colors.cyan),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = KeyboardType.Password),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }, onGo = { onSubmit() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Skerry.colors.surface2)
                    .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Sym("lock", size = 18.sp, color = Skerry.colors.faint)
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Txt(placeholder, color = Skerry.colors.faint, size = 14.sp)
                    innerTextField()
                }
            }
        },
    )
}
