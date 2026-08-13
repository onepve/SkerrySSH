package app.skerry.shared.snippet

import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

private val secureRandom = SecureRandom()

actual fun captureSnippetRunEnvironment(): SnippetRunEnvironment {
    // One clock read: ${{timestamp}} and ${{time}} from the same run must agree on the second.
    val instant = Instant.now()
    val now = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
    return SnippetRunEnvironment(
        moment = SnippetMoment(
            year = now.year,
            month = now.monthValue,
            day = now.dayOfMonth,
            hour = now.hour,
            minute = now.minute,
            second = now.second,
            epochSeconds = instant.epochSecond,
        ),
        newUuid = { UUID.randomUUID().toString() },
        randomChars = { length, alphabet ->
            require(alphabet.isNotEmpty()) { "random alphabet must not be empty" }
            buildString(length) { repeat(length) { append(alphabet[secureRandom.nextInt(alphabet.length)]) } }
        },
    )
}
