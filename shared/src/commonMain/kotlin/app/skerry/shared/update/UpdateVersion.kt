package app.skerry.shared.update

/**
 * Dotted numeric app version for update comparison. Accepts release-tag spellings ("v0.1.2") and
 * semver suffixes ("1.2.0-rc1", "1.2.0+build5" — the suffix is ignored: the `latest` GitHub
 * release is never a prerelease, so only the numeric core matters). Missing components count as
 * zero, so "1.0" == "1.0.0".
 */
class UpdateVersion private constructor(private val parts: List<Int>) : Comparable<UpdateVersion> {

    override fun compareTo(other: UpdateVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val diff = (parts.getOrNull(i) ?: 0).compareTo(other.parts.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    override fun equals(other: Any?): Boolean = other is UpdateVersion && compareTo(other) == 0

    override fun hashCode(): Int = parts.dropLastWhile { it == 0 }.hashCode()

    override fun toString(): String = parts.joinToString(".")

    companion object {
        /** Parses [raw] into a version, or null if it has no valid numeric core. */
        fun parse(raw: String): UpdateVersion? {
            val core = raw.removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            if (core.isEmpty()) return null
            val parts = core.split('.').map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
            return UpdateVersion(parts)
        }
    }
}
