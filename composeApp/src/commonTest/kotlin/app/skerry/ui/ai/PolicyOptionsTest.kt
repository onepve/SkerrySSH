package app.skerry.ui.ai

import app.skerry.shared.ai.AiPolicy
import kotlin.test.Test
import kotlin.test.assertEquals

class PolicyOptionsTest {

    @Test
    fun `every AiPolicy has exactly one picker option`() {
        // shortLabel() resolves via POLICY_OPTIONS.first { it.policy == policy }: a policy missing
        // from the list compiles fine but crashes the pickers at render time with
        // NoSuchElementException, so exhaustiveness is enforced here instead.
        AiPolicy.entries.forEach { policy ->
            assertEquals(1, POLICY_OPTIONS.count { it.policy == policy }, "expected one option for $policy")
        }
    }
}
