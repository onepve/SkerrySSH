package app.skerry.ui.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.help_copied_all
import app.skerry.ui.generated.resources.help_copy_all_hint
import app.skerry.ui.terminal.plainTextClipEntry
import app.skerry.ui.theme.Skerry
import app.skerry.ui.nav.PlatformBackHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** One plain-text paragraph of a help dialog. */
data class HelpSection(val text: String)

/** One copyable example: [label] explains it, [command] is what runs. [key] lets an
 * [onExampleAction] handler tell examples apart (runbook help uses it to create the matching
 * template); snippet help leaves it null. */
data class HelpExample(val label: String, val command: String, val key: String? = null)

/**
 * Modal help dialog for feature screens (snippets, runbooks…): a scrollable card with a few
 * explanation paragraphs and a list of example commands. Copy writes to the system clipboard and
 * flips the text to a check briefly. Esc or a click on the scrim closes it — the dialog
 * is read-only, so nothing can be lost. When [onExampleAction] is set, clicking an example calls
 * it instead of copying (used by runbooks to create the example runbook).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HelpDialog(
    title: String,
    sections: List<HelpSection>,
    examples: List<HelpExample>,
    onDismiss: () -> Unit,
    onExampleAction: ((HelpExample) -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copiedIndex by remember { mutableIntStateOf(-1) }
    var allCopied by remember { mutableStateOf(false) }
    // Double-clicking anywhere in the dialog copies everything the user sees: the explanation
    // sections (variable reference for snippets) and every example command, in reading order.
    val copyAll: () -> Unit = {
        val all = buildList {
            sections.forEach { add(it.text) }
            examples.forEach { add(it.command) }
        }.joinToString("\n")
        scope.launch { clipboard.setClipEntry(plainTextClipEntry(all)) }
        allCopied = true
        scope.launch {
            delay(1500)
            allCopied = false
        }
    }
    // Android physical back closes the dialog (LIFO) instead of falling through to the screen's
    // navigation handler and popping the whole screen behind it.
    PlatformBackHandler { onDismiss() }
    ModalScrim(onDismiss = onDismiss, dismissOnScrimClick = true) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(0.72f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Skerry.colors.surfaceDeep)
                .border(1.dp, Skerry.colors.cyan14, RoundedCornerShape(12.dp))
                .consumeClicks(),
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(26.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Txt(
                        title, color = Skerry.colors.text, size = 16.sp,
                        weight = FontWeight.SemiBold, letterSpacing = (-0.2).sp,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Txt(
                            if (allCopied) stringResource(Res.string.help_copied_all) else stringResource(Res.string.help_copy_all_hint),
                            color = if (allCopied) Skerry.colors.moss else Skerry.colors.faint,
                            size = 10.5.sp,
                        )
                        if (allCopied) {
                            Sym("check", size = 12.sp, color = Skerry.colors.moss)
                        }
                    }
                }
                sections.forEachIndexed { index, section ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .combinedClickable(
                                // Single click must do nothing — only a double-click copies everything.
                                onClick = {},
                                onDoubleClick = { copyAll() },
                            ),
                    ) {
                        Txt(
                            section.text, color = Skerry.colors.dim, size = 12.5.sp, lineHeight = 18.sp,
                        )
                    }
                }
                if (examples.isNotEmpty()) {
                    HLine()
                    examples.forEachIndexed { index, example ->
                        val copied = copiedIndex == index
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clickable(
                                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (onExampleAction != null) {
                                        onExampleAction(example)
                                    } else {
                                        scope.launch { clipboard.setClipEntry(plainTextClipEntry(example.command)) }
                                        copiedIndex = index
                                        scope.launch {
                                            delay(1500)
                                            if (copiedIndex == index) copiedIndex = -1
                                        }
                                    }
                                },
                        ) {
                            Txt(example.label, color = Skerry.colors.faint, size = 11.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Txt(
                                    example.command, color = if (copied) Skerry.colors.moss else Skerry.colors.text, size = 12.sp,
                                    font = LocalFonts.current.mono, lineHeight = 16.sp,
                                )
                                if (copied) {
                                    Sym(
                                        "check", size = 14.sp, color = Skerry.colors.moss,
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
