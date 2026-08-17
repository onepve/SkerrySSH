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
import app.skerry.shared.snippet.paramChoices
import app.skerry.shared.snippet.paramDefault
import app.skerry.shared.snippet.sanitizeSnippetValue
import app.skerry.shared.vault.CredentialSecret
import app.skerry.shared.vault.SshKeyGenerator
import app.skerry.ui.app.LocalCredentials
import app.skerry.ui.app.LocalSshKeyGenerator
import app.skerry.ui.design.DropdownField
import app.skerry.ui.design.FieldLabel
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.design.fieldFocus
import app.skerry.ui.design.fieldName
import app.skerry.ui.design.rememberFieldDraft
import app.skerry.ui.design.spaceLabel
import app.skerry.ui.design.untrustedLabel
import app.skerry.ui.design.Txt
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.lib_snippet_vars_clipboard
import app.skerry.ui.generated.resources.lib_snippet_vars_clipboard_empty
import app.skerry.ui.generated.resources.lib_snippet_vars_vault
import app.skerry.ui.generated.resources.lib_snippet_vars_vault_missing
import app.skerry.ui.generated.resources.lib_snippet_vars_vault_not_password
import app.skerry.ui.generated.resources.lib_snippet_vars_vault_unnamed
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
import androidx.compose.ui.text.style.TextDirection

/** Mask shown wherever a vault secret would otherwise be printed. */
internal const val SECRET_MASK = "••••••"

/**
 * [text] with every resolved vault secret span replaced by [SECRET_MASK]. Display only: what is
 * sent or classified stays the real text. Shared by the confirmations that quote a resolved line —
 * the production guard's dialog and the connect-time snippet gate.
 *
 * A string that was CUT mid-secret — the classifier caps a candidate at 512 characters, and the
 * cut can land inside a resolved value — ends with a prefix of the secret, which the exact replace
 * cannot see. Any trailing prefix of a secret is masked as well: that can hide a legitimate
 * character that merely coincides with the secret's start, but the other direction printed the
 * secret's head in the guard's aside in clear. The mirror image — a string that BEGINS mid-secret,
 * reachable only through a soft-wrapped screen row that happens to carry a resolved value — is
 * accepted residual risk: a leading-suffix rule cannot tell a secret's tail from ordinary text.
 *
 * Longest secret first: with one resolved value a substring of another, replacing the shorter
 * first would mask only the embedded span and print the longer secret's flanks in clear.
 */
internal fun maskSecrets(text: String, secrets: List<String>): String {
    val hidden = secrets.filter { it.isNotBlank() }.sortedByDescending { it.length }
    if (hidden.isEmpty()) return text
    var masked = hidden.fold(text) { acc, secret -> acc.replace(secret, SECRET_MASK) }
    for (secret in hidden) {
        val longest = minOf(secret.length - 1, masked.length)
        for (cut in longest downTo 1) {
            if (masked.endsWith(secret.substring(0, cut))) {
                masked = masked.dropLast(cut) + SECRET_MASK
                break
            }
        }
    }
    return masked
}

/**
 * A `${'$'}{{vault:name}}` entry name as it may be drawn. The name comes from the template, which may
 * have arrived from a team member: a bidi override in it would name one credential while another is
 * looked up, in the row whose whole job is to say which secret goes into the command. A name that
 * filters away to nothing gets a stand-in, or the row would name no entry at all.
 */
@Composable
internal fun vaultEntryLabel(name: String): String =
    spaceLabel(name, fallback = stringResource(Res.string.lib_snippet_vars_vault_unnamed))

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
        // Certificates (and anything else that is not a password) are not injectable into a snippet.
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
    /** Options per parameter (`${{name:default|opt1|opt2}}`); absent for free-text parameters. */
    val paramChoices: Map<String, List<String>>,
    /** Distinct `${{vault:name}}` entry names referenced. */
    val vaultRefs: List<String>,
    /** Whether anything references `${{clipboard}}` (it is only read if so). */
    val needsClipboard: Boolean,
    internal val vaultResolutions: SnapshotStateMap<String, VaultRef>,
    internal val params: SnapshotStateMap<String, String>,
) {
    internal constructor(
        paramNames: List<String>,
        paramChoices: Map<String, List<String>>,
        vaultRefs: List<String>,
        needsClipboard: Boolean,
        vaultResolutions: Map<String, VaultRef>,
        params: SnapshotStateMap<String, String>,
    ) : this(
        paramNames,
        paramChoices,
        vaultRefs,
        needsClipboard,
        mutableStateMapOf<String, VaultRef>().apply { putAll(vaultResolutions) },
        params,
    )
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

    /**
     * The resolved vault secrets of this run — the spans a later confirmation (the production
     * guard's) must mask rather than print, exactly as this dialog's own preview masked them.
     */
    fun vaultSecrets(): List<String> =
        vaultResolutions.values.filterIsInstance<VaultRef.Ok>().map { it.secret }

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
        val params = variables.filter { it.kind == SnippetVariableKind.PARAM }
        val paramNames = params.map { it.name }.distinct()
        val vaultRefs = variables.filter { it.kind == SnippetVariableKind.VAULT }.map { it.format.orEmpty() }.distinct()
        val firstByName = paramNames.associateWith { name -> params.first { it.name == name } }
        TemplateVariableValues(
            paramNames = paramNames,
            paramChoices = firstByName.mapValues { (_, v) -> v.paramChoices() }.filterValues { it.isNotEmpty() },
            vaultRefs = vaultRefs,
            needsClipboard = variables.any { it.kind == SnippetVariableKind.CLIPBOARD },
            vaultResolutions = mutableStateMapOf<String, VaultRef>().apply {
                vaultRefs.forEach { put(it, resolveVaultRef(it, credentials)) }
            },
            params = mutableStateMapOf<String, String>().apply {
                paramNames.forEach { name -> put(name, paramSeed(firstByName.getValue(name), initialParams[name])) }
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
                            // Only passwords and SSH-key public halves are injectable.
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
    val firstTextParam = values.paramNames.firstOrNull { it !in values.paramChoices }
    values.paramNames.forEach { name ->
        key(name) {
            // The caption is the variable's own name, not chrome: `${{token}}` and `${{TOKEN}}` are
            // two different keys, and uppercasing the caption would draw and announce them alike.
            // The caption is also the field's accessible name. The parser bounds what a name may
            // contain but not how long it is: unfiltered, a shared template could push the preview
            // and the Run button out of the dialog with one very long parameter.
            FormField(untrustedLabel(name), uppercase = false) {
                val choices = values.paramChoices[name]
                if (choices != null) {
                    DropdownField(
                        value = values.params[name].orEmpty(),
                        options = choices,
                        // Options come pre-sanitized from paramChoices(), so the picked string, the
                        // selected highlight and the sent value are already one string. The label
                        // filter adds the cap and space-folding every untrusted label carries — a
                        // menu row may draw shortened, but the confirmed line shows the true value.
                        label = { untrustedLabel(it) },
                        onPick = { values.params[name] = it },
                    )
                } else {
                    ParamField(
                        value = values.params[name].orEmpty(),
                        onChange = { values.params[name] = sanitizeSnippetValue(it) },
                        modifier = if (name == firstTextParam) Modifier.focusRequester(firstFieldFocus) else Modifier,
                        // The default from the template (or the previous run) is a suggestion: select it,
                        // so the autofocused first field takes a replacement rather than a prefix.
                        selectAllOnFocus = values.isSeeded(name),
                    )
                }
            }
        }
    }
    if (firstTextParam != null && autoFocus) {
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
                val shown = vaultEntryLabel(name)
                when (val ref = values.vaultResolutions[name]) {
                    is VaultRef.Ok -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Txt(shown, color = Skerry.colors.text, size = 11.5.sp, font = mono)
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
                        Txt(stringResource(Res.string.lib_snippet_vars_vault_not_password, shown), color = Skerry.colors.sunset, size = 11.5.sp)
                    else ->
                        Txt(stringResource(Res.string.lib_snippet_vars_vault_missing, shown), color = Skerry.colors.sunset, size = 11.5.sp)
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

/**
 * What a parameter's field starts out holding. A free-text parameter keeps the previous run's
 * value, else the inline default. A choice parameter must start on one of its options: the
 * previous run's value only if it is still offered, else the default (always the first option
 * when present), else the first option — a stale remembered value must not resurrect a choice
 * the template no longer contains.
 */
internal fun paramSeed(variable: SnippetSegment.Variable, previous: String?): String {
    val choices = variable.paramChoices()
    // The default is the template's own text, and a template can be shared: sanitized here the way
    // the option list already is, so the field draws the string that will be spliced rather than one
    // the run then quietly rewrites.
    val default = variable.paramDefault()?.let { sanitizeSnippetValue(it) }?.ifEmpty { null }
    if (choices.isEmpty()) return previous ?: default ?: ""
    return previous?.takeIf { it in choices } ?: default ?: choices.first()
}

@Composable
private fun ParamField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, selectAllOnFocus: Boolean = false) {
    val mono = LocalFonts.current.mono
    val textColor = Skerry.colors.text
    // The value is spliced into a shell line, and its seed is the template's own text: pinned, so
    // the field reads in the order the line will.
    val style = remember(mono, textColor) {
        TextStyle(color = textColor, fontSize = 12.5.sp, fontFamily = mono, textDirection = TextDirection.Ltr)
    }
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
