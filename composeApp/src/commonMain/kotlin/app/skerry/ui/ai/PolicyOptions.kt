package app.skerry.ui.ai

import androidx.compose.runtime.Composable
import app.skerry.shared.ai.AiPolicy
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.conn_policy_balanced_desc
import app.skerry.ui.generated.resources.conn_policy_balanced_short
import app.skerry.ui.generated.resources.conn_policy_balanced_title
import app.skerry.ui.generated.resources.conn_policy_off_desc
import app.skerry.ui.generated.resources.conn_policy_off_short
import app.skerry.ui.generated.resources.conn_policy_off_title
import app.skerry.ui.generated.resources.conn_policy_permissive_desc
import app.skerry.ui.generated.resources.conn_policy_permissive_short
import app.skerry.ui.generated.resources.conn_policy_permissive_title
import app.skerry.ui.generated.resources.conn_policy_strict_desc
import app.skerry.ui.generated.resources.conn_policy_strict_short
import app.skerry.ui.generated.resources.conn_policy_strict_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A per-host AI policy option for pickers (connection form, settings). [title]/[desc]/[shortLabel]
 * are localized string resources, resolved with `stringResource` at the call site, so pickers follow
 * the selected UI language ([app.skerry.ui.i18n.UiLanguage]).
 */
data class PolicyOption(
    val policy: AiPolicy,
    val icon: String,
    val title: StringResource,
    val desc: StringResource,
    val shortLabel: StringResource,
)

/**
 * Whether an AI provider endpoint uses plain http:// (excluding localhost/127.0.0.1) — the key and
 * prompt (with secrets, under Permissive) would travel in plaintext.
 */
fun isInsecureAiEndpoint(url: String): Boolean {
    val u = url.trim()
    if (!u.startsWith("http://")) return false
    val host = u.removePrefix("http://")
    return !host.startsWith("localhost") && !host.startsWith("127.0.0.1")
}

val POLICY_OPTIONS = listOf(
    PolicyOption(AiPolicy.Strict, "shield_lock", Res.string.conn_policy_strict_title, Res.string.conn_policy_strict_desc, Res.string.conn_policy_strict_short),
    PolicyOption(AiPolicy.Balanced, "tune", Res.string.conn_policy_balanced_title, Res.string.conn_policy_balanced_desc, Res.string.conn_policy_balanced_short),
    PolicyOption(AiPolicy.Permissive, "science", Res.string.conn_policy_permissive_title, Res.string.conn_policy_permissive_desc, Res.string.conn_policy_permissive_short),
    PolicyOption(AiPolicy.Off, "block", Res.string.conn_policy_off_title, Res.string.conn_policy_off_desc, Res.string.conn_policy_off_short),
)

/** Localized short label for [AiPolicy] (mobile policy pills). */
@Composable
fun AiPolicy.shortLabel(): String {
    val policy = this
    return stringResource(POLICY_OPTIONS.first { it.policy == policy }.shortLabel)
}
