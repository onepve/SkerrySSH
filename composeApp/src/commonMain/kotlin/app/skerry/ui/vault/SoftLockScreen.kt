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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.design.BrandMark
import app.skerry.ui.design.BrandPlate
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
 */
@Composable
fun SoftLockScreen(
    storedHash: String,
    onUnlock: () -> Unit,
    onHardLock: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }
    var shake by remember { mutableStateOf(false) }

    val MAX_ATTEMPTS = 3

    fun tryUnlock() {
        if (pin.length !in 4..8) return
        val inputHash = sha256("skerry-soft-lock" + pin)
        if (inputHash == storedHash) {
            onUnlock()
        } else {
            attempts++
            pin = ""
            shake = true
            if (attempts >= MAX_ATTEMPTS) {
                onHardLock()
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.radialGradient(listOf(Skerry.colors.surfaceDeep, Skerry.colors.bg)))
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandPlate(size = 88.dp, corner = 20.dp)
        Box(Modifier.height(22.dp))
        Txt(stringResource(Res.string.shell_softlock_title), color = Skerry.colors.text, size = 22.sp, weight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
        Box(Modifier.height(6.dp))
        Txt(stringResource(Res.string.shell_softlock_subtitle), color = Skerry.colors.dim, size = 13.sp)
        Box(Modifier.height(32.dp))

        Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SoftLockPinField(pin, { pin = it; shake = false }, onSubmit = { tryUnlock() })

            if (attempts > 0 && attempts < MAX_ATTEMPTS) {
                Txt(stringResource(Res.string.shell_softlock_wrong_attempts, MAX_ATTEMPTS - attempts), color = Skerry.colors.amber, size = 12.sp)
            }
            if (attempts >= MAX_ATTEMPTS) {
                Txt(stringResource(Res.string.shell_softlock_too_many), color = Skerry.colors.storm, size = 12.sp)
            }

            PrimaryButton(stringResource(Res.string.shell_softlock_unlock), onClick = { tryUnlock() }, modifier = Modifier.fillMaxWidth())
        }

        Box(Modifier.height(28.dp))

        Txt(
            stringResource(Res.string.shell_softlock_full_lock),
            color = Skerry.colors.dim,
            size = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onHardLock)
                .padding(vertical = 4.dp),
        )

        Box(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sym("shield_lock", size = 14.sp, color = Skerry.colors.faint)
            Txt(stringResource(Res.string.shell_softlock_footer), color = Skerry.colors.faint, size = 11.sp)
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
    // platform-independent implementation using Kotlin stdlib
    val bytes = input.encodeToByteArray()
    // A minimal SHA-256 in pure Kotlin would be very verbose; using the platform's available hashing.
    // For JVM/desktop, this is available via java.security.MessageDigest.
    // For the compose multiplatform context, we use expect/actual or a common implementation.
    // Here we drop down to a JVM-available call via expect/actual pattern.
    return sha256Internal(bytes)
}

internal expect fun sha256Internal(bytes: ByteArray): String
