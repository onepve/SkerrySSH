package app.skerry.ui.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.shared.snippet.SnippetSegment
import app.skerry.shared.snippet.SnippetVariableKind
import app.skerry.shared.snippet.sanitizeSnippetValue
import app.skerry.shared.vault.CredentialSecret
import app.skerry.shared.vault.SshKeyGenerator
import app.skerry.ui.app.LocalCredentials
import app.skerry.ui.app.LocalSshKeyGenerator
import app.skerry.ui.design.FieldLabel
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.fieldFocus
import app.skerry.ui.design.fieldName
import app.skerry.ui.design.rememberFieldDraft
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.lib_snippet_vars_clipboard
import app.skerry.ui.generated.resources.lib_snippet_vars_clipboard_empty
import app.skerry.ui.generated.resources.lib_snippet_vars_vault
import app.skerry.ui.generated.resources.lib_snippet_vars_vault_missing
import app.skerry.ui.generated.resources.lib_snippet_vars_vault_not_password
import app.skerry.ui.generated.resources.vault_subtitle_certificate
import app.skerry.ui.generated.resources.vault_subtitle_password
import app.skerry.ui.generated.resources.vault_subtitle_private_key
import app.skerry.ui.identity.CredentialManagerController
import app.skerry.ui.terminal.fetchSystemClipboardText
import app.skerry.ui.theme.Skerry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import app.skerry.ui.design.FormField

/** Mask shown wherever a vault secret would otherwise be printed. */
internal const val SECRET_MASK = "••••••"

/** Vault entry type shown to the user when disambiguating same-name entries. */
internal enum class VaultEntryKind { PASSWORD, SSH_KEY, CERTIFICATE, OTHER }

/** One candidate among several same-name vault entries; the notes make them distinguishable. */
internal data class VaultRefCandidate(
    val id: String,
    val label: String,
    val kind: VaultEntryKind,
    val notes: String?,
)

/** Vault reference resolution, done once when the confirmation opens. */
internal sealed interface VaultRef {
    data class Ok(val secret: String) : VaultRef
    /** SSH-key entry whose public half is still being inspected off the UI thread. */
    data object Resolving : VaultRef
    /** More than one entry shares the name; the user must pick one in the confirmation. */
    data class Ambiguous(val candidates: List<VaultRefCandidate>) : VaultRef
    data object Missing : VaultRef
    data object NotAPassword : VaultRef
}

internal fun resolveVaultRef(name: String, credentials: CredentialManagerController?): VaultRef {
    val matches = credentials?.credentials?.filter { it.label == name }
    if (matches.isNullOrEmpty()) return VaultRef.Missing
    // Same-name entries (allowed by the vault) are ambiguous: resolving to the first would silently
    // splice the wrong type. Surface all of them and let the user pick (see VaultRef.Ambiguous).
    if (matches.size > 1) {
        return VaultRef.Ambiguous(
            matches.map { entry ->
                VaultRefCandidate(
                    id = entry.id,
                    label = entry.label,
                    kind = when (entry.secret) {
                        is CredentialSecret.Password -> VaultEntryKind.PASSWORD
                        is CredentialSecret.PrivateKey -> VaultEntryKind.SSH_KEY
                        is CredentialSecret.Certificate -> VaultEntryKind.CERTIFICATE
                        else -> VaultEntryKind.OTHER
                    },
                    notes = entry.notes,
                )
            }
        )
    }
    return when (val secret = matches.first().secret) {
        is CredentialSecret.Password -> VaultRef.Ok(secret.password)
        // An SSH-key entry injects its PUBLIC half (authorized_keys etc.) — the private key must
        // never end up in a command line. Inspecting the PEM is CPU-heavy (RSA-4096), so the caller
        // resolves this asynchronously.
        is CredentialSecret.PrivateKey -> VaultRef.Resolving
        // Upstream v1 behaviour, unchanged: certificates (and anything else that is not a password)
        // are not injectable into a snippet.
        else -> VaultRef.NotAPassword
    }
}

/**
 * The context side of `${{…}}` resolution — everything the machine can't produce on its own:
 * prompted parameters, the system clipboard and vault look-ups. Shared by the snippet confirmation
 * ([SnippetRunDialog]) and the runbook start dialog, because both have the same obligation: capture
 * once when the confirmation opens, show a masked preview, and hand the real values over only when
 * the user says go (TOCTOU rule, coding-guidelines §3).
 */
@Stable
class TemplateVariableValues internal constructor(
    /** Prompted parameters in first-appearance order. */
    val paramNames: List<String>,
    /**
     * Selectable options per parameter, from `${{name:default|opt1|opt2}}`: the part before the
     * first `|` is the default value, every following part is an option. Empty for a parameter
     * written without options — it stays a free-text field.
     */
    val paramOptions: Map<String, List<String>> = emptyMap(),
    /** Distinct `${{vault:name}}` entry names referenced. */
    val vaultRefs: List<String>,
    /** Whether anything references `${{clipboard}}` (it is only read if so). */
    val needsClipboard: Boolean,
    internal val vaultResolutions: SnapshotStateMap<String, VaultRef>,
    internal val params: SnapshotStateMap<String, String>,
) {
    /** What each parameter was seeded with — the template's default, or the previous run's value. */
    private val seeded: Map<String, String> = params.toMap()

    /** True while [name] still holds what the form put there, so its field may select on focus. */
    internal fun isSeeded(name: String): Boolean = params[name] == seeded[name]

    /** Clipboard contents; `null` while still being read. */
    internal var clipboard: String? by mutableStateOf(null)

    /** Names currently being resolved (inspect in flight) — guards against re-entry in snapshotFlow. */
    internal val inFlightResolutions: SnapshotStateList<String> = mutableStateListOf()

    /** Current parameter values, to remember for this template's next run. */
    fun paramValues(): Map<String, String> = params.toMap()

    /** Whether every reference resolved: a missing vault entry has no value to send. */
    val canRun: Boolean
        get() = vaultResolutions.values.all { it is VaultRef.Ok } && (!needsClipboard || clipboard != null)

    /**
     * User's pick for an ambiguous vault reference ([VaultRef.Ambiguous]); the entry is resolved
     * (off the UI thread for SSH keys) right after the pick.
     */
    internal val selectedVaultCandidate: SnapshotStateMap<String, VaultRefCandidate> = mutableStateMapOf()

    /** Resolves [candidate] for [name]'s vault reference after the user picks it. */
    internal fun selectVaultCandidate(name: String, candidate: VaultRefCandidate) {
        selectedVaultCandidate[name] = candidate
        vaultResolutions[name] = VaultRef.Resolving
    }
    /**
     * Value for [variable]. [masked] replaces vault secrets with [SECRET_MASK] — the preview path;
     * the unmasked path is only ever called to build the line actually sent.
     */
    fun value(variable: SnippetSegment.Variable, masked: Boolean): String = when (variable.kind) {
        SnippetVariableKind.CLIPBOARD -> clipboard.orEmpty()
        SnippetVariableKind.VAULT -> when (val ref = vaultResolutions[variable.format.orEmpty()]) {
            is VaultRef.Ok -> if (masked) SECRET_MASK else ref.secret
            else -> ""
        }
        SnippetVariableKind.PARAM -> params[variable.name].orEmpty()
        else -> "" // machine kinds are resolved from the run's own draw
    }
}

/**
 * Collects the context values for [variables], keyed on [request] so a new confirmation never
 * inherits the previous one's fields. Parameters are prefilled from [initialParams] (the previous
 * run) and otherwise from the placeholder's inline default (`${{name:default}}`).
 */
@Composable
fun rememberTemplateVariableValues(
    request: Any,
    variables: List<SnippetSegment.Variable>,
    initialParams: Map<String, String> = emptyMap(),
): TemplateVariableValues {
    val credentials = LocalCredentials.current
    val generator = LocalSshKeyGenerator.current
    val clipboard = LocalClipboard.current
    val values = remember(request) {
        val paramNames = variables.filter { it.kind == SnippetVariableKind.PARAM }.map { it.name }.distinct()
        val vaultRefs = variables.filter { it.kind == SnippetVariableKind.VAULT }.map { it.format.orEmpty() }.distinct()
        TemplateVariableValues(
            paramNames = paramNames,
            paramOptions = paramNames.associateWith { name ->
                variables.firstOrNull { it.kind == SnippetVariableKind.PARAM && it.name == name }
                    ?.format?.split('|')?.drop(1)?.filter { it.isNotBlank() } ?: emptyList()
            },
            vaultRefs = vaultRefs,
            needsClipboard = variables.any { it.kind == SnippetVariableKind.CLIPBOARD },
            vaultResolutions = mutableStateMapOf<String, VaultRef>().apply {
                vaultRefs.forEach { put(it, resolveVaultRef(it, credentials)) }
            },
            params = mutableStateMapOf<String, String>().apply {
                paramNames.forEach { name ->
                    val default = variables.firstOrNull { it.kind == SnippetVariableKind.PARAM && it.name == name }
                        ?.format?.substringBefore('|')
                    put(name, initialParams[name] ?: default ?: "")
                }
            },
        )
    }
    // SSH-key vault refs resolve to their public half by inspecting the PEM; that is CPU-heavy
    // (RSA-4096 key derivation), so it runs off the UI thread and flips Resolving → Ok/error.
    // Also resolves the user's pick for ambiguous (same-name) refs. snapshotFlow keeps this
    // alive across state changes inside `values` (the remember instance itself never changes).
    LaunchedEffect(request, values) {
        snapshotFlow { values.vaultResolutions.toMap() }
            .collect { resolutions ->
                resolutions.forEach { (name, ref) ->
                    if (ref is VaultRef.Resolving && !values.inFlightResolutions.contains(name)) {
                        values.inFlightResolutions.add(name)
                        val pick = values.selectedVaultCandidate[name]
                        val entry = when {
                            pick != null -> credentials?.credentials?.firstOrNull { it.id == pick.id }
                            else -> credentials?.credentials?.firstOrNull { it.label == name }
                        }
                        val resolved: VaultRef = when (val secret = entry?.secret) {
                            is CredentialSecret.Password -> VaultRef.Ok(secret.password)
                            is CredentialSecret.PrivateKey -> {
                                val pub = withContext(Dispatchers.Default) {
                                    generator?.inspect(secret.privateKeyPem, secret.passphrase)?.publicKeyOpenSsh
                                }
                                if (pub != null) VaultRef.Ok(pub) else VaultRef.NotAPassword
                            }
                            // Upstream v1 behaviour: only passwords and SSH-key public halves are injectable.
                            else -> VaultRef.NotAPassword
                        }
                        values.vaultResolutions[name] = resolved
                    }
                }
            }
    }
    if (values.needsClipboard) {
        LaunchedEffect(request) { values.clipboard = fetchSystemClipboardText(clipboard).orEmpty() }
    }
    return values
}

/**
 * The input block of a confirmation: one field per prompted parameter, then read-only rows for the
 * clipboard and vault references so the user sees what will be spliced in (and what failed to
 * resolve) before anything is sent.
 */
@Composable
fun TemplateVariableFields(values: TemplateVariableValues, autoFocus: Boolean = true) {
    val mono = LocalFonts.current.mono
    val firstFieldFocus = remember { FocusRequester() }
    values.paramNames.forEachIndexed { index, name ->
        key(name) {
            // The caption is the variable's own name, not chrome: `${{token}}` and `${{TOKEN}}` are
            // two different keys, and uppercasing the caption would draw and announce them alike.
            FormField(name, uppercase = false) {
                val options = values.paramOptions[name].orEmpty()
                if (options.isEmpty()) {
                    ParamField(
                        value = values.params[name].orEmpty(),
                        onChange = { values.params[name] = sanitizeSnippetValue(it) },
                        modifier = if (index == 0) Modifier.focusRequester(firstFieldFocus) else Modifier,
                        // The default from the template (or the previous run) is a suggestion: select it,
                        // so the autofocused first field takes a replacement rather than a prefix.
                        selectAllOnFocus = values.isSeeded(name),
                    )
                } else {
                    ParamOptionList(
                        options = options,
                        selected = values.params[name].orEmpty(),
                        onSelect = { values.params[name] = sanitizeSnippetValue(it) },
                    )
                }
            }
        }
    }
    if (values.paramNames.isNotEmpty() && autoFocus) {
        LaunchedEffect(Unit) { firstFieldFocus.requestFocus() }
    }
    if (values.needsClipboard) {
        FieldLabel(stringResource(Res.string.lib_snippet_vars_clipboard))
        val shown = values.clipboard?.let { sanitizeSnippetValue(it) }
        Txt(
            when {
                shown == null -> "…"
                shown.isEmpty() -> stringResource(Res.string.lib_snippet_vars_clipboard_empty)
                else -> shown
            },
            color = Skerry.colors.dim, size = 11.5.sp, font = mono, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
    if (values.vaultRefs.isNotEmpty()) {
        FieldLabel(stringResource(Res.string.lib_snippet_vars_vault))
        values.vaultRefs.forEach { name ->
            key(name) {
                when (val ref = values.vaultResolutions[name]) {
                    is VaultRef.Ok -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Txt(name, color = Skerry.colors.text, size = 11.5.sp, font = mono)
                        Txt(SECRET_MASK, color = Skerry.colors.faint, size = 11.5.sp, font = mono)
                    }
                    VaultRef.Resolving ->
                        Txt("…", color = Skerry.colors.dim, size = 11.5.sp)
                    is VaultRef.Ambiguous -> VaultCandidateList(
                        name = name,
                        candidates = ref.candidates,
                        values = values,
                    )
                    VaultRef.NotAPassword ->
                        Txt(stringResource(Res.string.lib_snippet_vars_vault_not_password, name), color = Skerry.colors.sunset, size = 11.5.sp)
                    else ->
                        Txt(stringResource(Res.string.lib_snippet_vars_vault_missing, name), color = Skerry.colors.sunset, size = 11.5.sp)
                }
            }
        }
    }
}

@Composable
private fun VaultCandidateList(name: String, candidates: List<VaultRefCandidate>, values: TemplateVariableValues) {
    val mono = LocalFonts.current.mono
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        candidates.forEach { candidate ->
            val isSelected = values.selectedVaultCandidate[name]?.id == candidate.id
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Skerry.colors.bg)
                    .border(1.dp, if (isSelected) Skerry.colors.cyan else Skerry.colors.line, RoundedCornerShape(7.dp))
                    .clickable { values.selectVaultCandidate(name, candidate) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Txt(vaultEntryKindLabel(candidate.kind), color = Skerry.colors.cyan, size = 10.5.sp, font = mono)
                Column(Modifier.weight(1f)) {
                    Txt(candidate.label, color = Skerry.colors.text, size = 12.sp, font = mono)
                    if (!candidate.notes.isNullOrBlank()) {
                        Txt(candidate.notes, color = Skerry.colors.dim, size = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun vaultEntryKindLabel(kind: VaultEntryKind): String = when (kind) {
    VaultEntryKind.PASSWORD -> stringResource(Res.string.vault_subtitle_password)
    VaultEntryKind.SSH_KEY -> stringResource(Res.string.vault_subtitle_private_key)
    VaultEntryKind.CERTIFICATE -> stringResource(Res.string.vault_subtitle_certificate)
    VaultEntryKind.OTHER -> ""
}

@Composable
private fun ParamOptionList(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val mono = LocalFonts.current.mono
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(Skerry.colors.bg)
                    .border(1.dp, if (isSelected) Skerry.colors.cyan else Skerry.colors.line, RoundedCornerShape(7.dp))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 9.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(option, color = if (isSelected) Skerry.colors.text else Skerry.colors.dim, size = 12.5.sp, font = mono)
            }
        }
    }
}

@Composable
private fun ParamField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, selectAllOnFocus: Boolean = false) {
    val mono = LocalFonts.current.mono
    val textColor = Skerry.colors.text
    val style = remember(mono, textColor) { TextStyle(color = textColor, fontSize = 12.5.sp, fontFamily = mono) }
    val draft = rememberFieldDraft(value, selectAllOnFocus)
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(7.dp)).background(Skerry.colors.bg)
            .border(1.dp, Skerry.colors.line, RoundedCornerShape(7.dp)).padding(horizontal = 9.dp, vertical = 7.dp),
    ) {
        BasicTextField(
            draft.textFieldValue(value), { draft.accept(it, value, onChange) }, singleLine = true, textStyle = style,
            cursorBrush = SolidColor(Skerry.colors.cyan),
            modifier = modifier.fillMaxWidth().fieldFocus(draft).fieldName(),
        )
    }
}
