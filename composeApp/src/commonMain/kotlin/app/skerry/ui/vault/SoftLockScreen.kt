package app.skerry.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.PrimaryButton
import app.skerry.ui.design.Sym
import app.skerry.ui.design.Txt
import app.skerry.ui.theme.Skerry
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.shell_softlock_title
import app.skerry.ui.generated.resources.shell_softlock_subtitle
import app.skerry.ui.generated.resources.shell_softlock_pin_placeholder
import app.skerry.ui.generated.resources.shell_softlock_unlock
import app.skerry.ui.generated.resources.shell_softlock_full_lock
import app.skerry.ui.generated.resources.shell_softlock_wrong_attempts
import app.skerry.ui.generated.resources.shell_softlock_too_many
import app.skerry.ui.generated.resources.shell_softlock_footer
import org.jetbrains.compose.resources.stringResource

/**
 * Soft-lock screen: shown over the app when [DesktopDesignState.softLocked] is true.
 * The vault stays unlocked (keys remain in memory); user enters the PIN to regain access,
 * or clicks "Completely lock" to perform a full hard lock (wipe keys, require master password).
 *
 * PIN validation: SHA-256 hash of `"skerry-soft-lock" + input` compared against [storedHash].
 * After 3 failed attempts, auto-escalates to a full hard lock.
 *
 * Visual: a centered modal-style card panel, matching the ChangeMasterPasswordDialog design.
 */
@Composable
fun SoftLockScreen(
    storedHash: String,
    onUnlock: () -> Unit,
    onHardLock: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }

    val MAX_ATTEMPTS = 3

    fun tryUnlock() {
        if (pin.length !in 4..8) return
        val inputHash = sha256("skerry-soft-lock" + pin)
        if (inputHash == storedHash) {
            onUnlock()
        } else {
            attempts++
            pin = ""
            if (attempts >= MAX_ATTEMPTS) {
                onHardLock()
            }
        }
    }

    val error: String? = when {
        attempts >= MAX_ATTEMPTS -> stringResource(Res.string.shell_softlock_too_many)
        attempts > 0 -> stringResource(Res.string.shell_softlock_wrong_attempts, MAX_ATTEMPTS - attempts)
        else -> null
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Skerry.colors.bg.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(
                stringResource(Res.string.shell_softlock_title),
                color = Skerry.colors.text,
                size = 16.sp,
                weight = FontWeight.SemiBold,
            )
            Box(Modifier.height(8.dp))
            Txt(
                stringResource(Res.string.shell_softlock_subtitle),
                color = Skerry.colors.dim,
                size = 11.5.sp,
            )
            Box(Modifier.height(16.dp))

            SoftLockPinField(pin, { pin = it }, onSubmit = { tryUnlock() })

            if (error != null) {
                Txt(
                    error,
                    color = if (attempts >= MAX_ATTEMPTS) Skerry.colors.storm else Skerry.colors.amber,
                    size = 11.5.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Box(Modifier.height(16.dp))

            PrimaryButton(
                stringResource(Res.string.shell_softlock_unlock),
                onClick = { tryUnlock() },
                modifier = Modifier.fillMaxWidth(),
            )

            Box(Modifier.height(14.dp))

            Txt(
                stringResource(Res.string.shell_softlock_full_lock),
                color = Skerry.colors.dim,
                size = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onHardLock)
                    .padding(vertical = 4.dp),
            )

            Box(Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Sym("shield_lock", size = 14.sp, color = Skerry.colors.faint)
                Txt(stringResource(Res.string.shell_softlock_footer), color = Skerry.colors.faint, size = 11.sp)
            }
        }
    }
}

@Composable
private fun SoftLockPinField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        textStyle = TextStyle(color = Skerry.colors.text, fontSize = 14.sp, fontFamily = LocalFonts.current.ui),
        cursorBrush = SolidColor(Skerry.colors.cyan),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, keyboardType = KeyboardType.Password),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }, onGo = { onSubmit() }),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                    if (value.isEmpty()) Txt(stringResource(Res.string.shell_softlock_pin_placeholder), color = Skerry.colors.faint, size = 14.sp)
                    innerTextField()
                }
            }
        },
    )
}

/** SHA-256 hash of [input], returned as a 64-char lowercase hex string. */
internal fun sha256(input: String): String {
    val bytes = input.encodeToByteArray()
    return sha256Internal(bytes)
}

internal expect fun sha256Internal(bytes: ByteArray): String
