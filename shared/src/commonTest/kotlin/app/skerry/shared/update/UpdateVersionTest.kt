package app.skerry.shared.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateVersionTest {

    @Test
    fun `parses plain dotted version`() {
        val v = UpdateVersion.parse("0.1.1")
        assertEquals(UpdateVersion.parse("0.1.1"), v)
    }

    @Test
    fun `strips leading v from release tags`() {
        assertEquals(UpdateVersion.parse("0.1.2"), UpdateVersion.parse("v0.1.2"))
        assertEquals(UpdateVersion.parse("1.0"), UpdateVersion.parse("V1.0"))
    }

    @Test
    fun `ignores prerelease and build suffixes`() {
        assertEquals(UpdateVersion.parse("1.2.0"), UpdateVersion.parse("1.2.0-rc1"))
        assertEquals(UpdateVersion.parse("1.2.0"), UpdateVersion.parse("1.2.0+build5"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(UpdateVersion.parse(""))
        assertNull(UpdateVersion.parse("abc"))
        assertNull(UpdateVersion.parse("1.two.3"))
        assertNull(UpdateVersion.parse("-1.0"))
        assertNull(UpdateVersion.parse("v"))
    }

    @Test
    fun `orders numerically not lexically`() {
        assertTrue(UpdateVersion.parse("0.1.2")!! > UpdateVersion.parse("0.1.1")!!)
        assertTrue(UpdateVersion.parse("0.10.0")!! > UpdateVersion.parse("0.9.9")!!)
        assertTrue(UpdateVersion.parse("1.0.0")!! > UpdateVersion.parse("0.99.99")!!)
        assertTrue(UpdateVersion.parse("0.1.1")!! < UpdateVersion.parse("v0.2")!!)
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(UpdateVersion.parse("1.0.0"), UpdateVersion.parse("1.0"))
        assertEquals(0, UpdateVersion.parse("1.0")!!.compareTo(UpdateVersion.parse("1.0.0")!!))
        assertTrue(UpdateVersion.parse("1.0.1")!! > UpdateVersion.parse("1.0")!!)
    }
}
