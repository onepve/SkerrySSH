package app.skerry.ui.mobile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontFamily
import app.skerry.shared.runbook.RunbookStep
import app.skerry.ui.design.DesignFonts
import app.skerry.ui.design.LocalFonts
import app.skerry.ui.desktop.seededRunbooks
import app.skerry.ui.desktop.seededSnippets
import app.skerry.ui.desktop.string
import app.skerry.ui.generated.resources.Res
import app.skerry.ui.generated.resources.lib_snippets_chip_all
import app.skerry.ui.generated.resources.lib_snippets_run_title
import app.skerry.ui.generated.resources.runbook_section
import app.skerry.ui.runbook.RunbookDraft
import app.skerry.ui.snippet.SnippetDraft
import app.skerry.ui.theme.SkerryTheme
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class MobileRunSheetsTest {

    private val testFonts = DesignFonts(FontFamily.Default, FontFamily.Monospace, FontFamily.Default)

    @Test
    fun `mobile snippet run sheet renders tags and category headers`() = runComposeUiTest {
        val snippets = seededSnippets()
        snippets.save(SnippetDraft(label = "Disk Check", command = "df -h", tags = listOf("ops", "disk")))
        snippets.save(SnippetDraft(label = "Mem Check", command = "free -m", tags = listOf("ops", "memory")))

        var pickedId: String? = null
        var dismissed = false

        setContent {
            CompositionLocalProvider(LocalFonts provides testFonts) {
                SkerryTheme {
                    MobileSnippetRunSheet(
                        manager = snippets,
                        onRun = { pickedId = it.id },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        onNodeWithText(string(Res.string.lib_snippets_run_title)).assertExists()
        onNodeWithText(string(Res.string.lib_snippets_chip_all)).assertExists()
        onAllNodesWithText("#ops").onFirst().assertExists()
        onAllNodesWithText("#ops").onFirst().performClick()

        onNodeWithText("Disk Check").assertExists()
        onNodeWithText("Disk Check").performClick()
        assertTrue(pickedId != null)
    }

    @Test
    fun `mobile runbook run sheet renders tags and category headers`() = runComposeUiTest {
        val runbooks = seededRunbooks()
        runbooks.save(
            RunbookDraft(
                label = "Deploy Service",
                description = "Steps to deploy",
                tags = listOf("deploy", "prod"),
                steps = listOf(RunbookStep.Command(id = "s1", title = "Pull", command = "git pull", confirm = false)),
            )
        )

        var pickedId: String? = null
        var dismissed = false

        setContent {
            CompositionLocalProvider(LocalFonts provides testFonts) {
                SkerryTheme {
                    MobileRunbookRunSheet(
                        manager = runbooks,
                        onRun = { pickedId = it.id },
                        onDismiss = { dismissed = true },
                    )
                }
            }
        }

        onNodeWithText(string(Res.string.runbook_section)).assertExists()
        onNodeWithText(string(Res.string.lib_snippets_chip_all)).assertExists()
        onAllNodesWithText("#deploy").onFirst().assertExists()
        onAllNodesWithText("#deploy").onFirst().performClick()

        onNodeWithText("Deploy Service").assertExists()
        onNodeWithText("Deploy Service").performClick()
        assertTrue(pickedId != null)
    }
}
