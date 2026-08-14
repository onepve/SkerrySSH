package app.skerry.server.routes

import app.skerry.server.RateLimits
import app.skerry.server.Services
import app.skerry.server.model.ErrorResponse
import app.skerry.server.model.InviteRedeemRequest
import app.skerry.server.model.PublicInviteDto
import app.skerry.server.model.PublicInvitesResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** Mirrors [app.skerry.server.db.InviteCodes.code] (varchar 64). */
private const val MAX_INVITE_CODE = 64

/**
 * Public invite endpoints (fork's gated registration): the front page lists the public codes via
 * [GET /invites/public], and a visitor redeems one for an account id via [POST /invites/redeem].
 * Both are unauthenticated; the redeem is rate-limited like `/auth/register`.
 *
 * The account id is a free-form string — deliberately NOT assumed to be an email, matching the
 * server's own account id (a `varchar(320)` with no format validation).
 */
fun Route.inviteRoutes(services: Services) {
    get("/invites/public") {
        val invites = services.invites.listPublic().map { PublicInviteDto(it.code, it.remainingUses) }
        call.respond(PublicInvitesResponse(invites))
    }

    rateLimit(RateLimits.INVITE_REDEEM) {
        post("/invites/redeem") {
            val req = call.receive<InviteRedeemRequest>()
            // Validate lengths before touching the DB: an oversized code/accountId would fail the
            // insert into a varchar column with a 500 on PostgreSQL.
            if (tooLong(req.accountId) || req.code.length > MAX_INVITE_CODE) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("identifier too long"))
                return@post
            }
            val now = System.currentTimeMillis()
            if (services.invites.accountExists(req.accountId)) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("account already registered"))
                return@post
            }
            if (services.invites.isPreregistered(req.accountId, now)) {
                call.respond(HttpStatusCode.Conflict, ErrorResponse("account already pre-registered"))
                return@post
            }
            val expiresAt = now + services.config.preregTtlSeconds * 1000
            val ok = services.invites.preregister(req.accountId, req.code, expiresAt, now)
            if (!ok) {
                call.respond(HttpStatusCode.Gone, ErrorResponse("invite code invalid or exhausted"))
                return@post
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
