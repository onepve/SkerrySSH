package app.skerry.server

import app.skerry.server.auth.SrpService
import app.skerry.server.auth.TokenService
import app.skerry.server.config.ServerConfig
import app.skerry.server.db.AccountRepository
import app.skerry.server.db.ActivityRepository
import app.skerry.server.db.AdminRepository
import app.skerry.server.db.DeviceRepository
import app.skerry.server.db.InviteCodeRepository
import app.skerry.server.db.PairingRepository
import app.skerry.server.db.RecordRepository
import app.skerry.server.db.StatsRepository
import app.skerry.server.db.TeamRecordRepository
import app.skerry.server.db.TeamRepository
import app.skerry.server.mail.EmailTemplates
import app.skerry.server.mail.SmtpMailer
import app.skerry.server.mail.loadMailConfig
import app.skerry.server.sync.ChangeNotifier
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.ZonedDateTime

/** Wired dependencies for one server instance. Created once in [module]. */
class Services(val config: ServerConfig, private val database: Database) {
    val accounts = AccountRepository(database)
    val devices = DeviceRepository(database)
    // On PostgreSQL, serialize upserts with an account-row lock; not needed on SQLite (pool=1).
    val records = RecordRepository(database, lockAccountRow = config.isPostgres)
    val pairing = PairingRepository(database)
    val teams = TeamRepository(database)
    val teamRecords = TeamRecordRepository(database, lockTeamRow = config.isPostgres)
    val stats = StatsRepository(database)
    val activity = ActivityRepository(database, retentionDays = config.activityRetentionDays, maxRows = config.activityMaxRows)
    val admin = AdminRepository(database)
    val inviteCodes = InviteCodeRepository(database)
    val srp = SrpService()
    val tokens = TokenService(config)
    val notifier = ChangeNotifier()
    // Mail subsystem
    val mailConfig = loadMailConfig(config.mailConfigPath)
    val mailer = SmtpMailer(
        enabled = config.mailEnabled,
        smtpHost = config.smtpHost,
        smtpPort = config.smtpPort,
        smtpUser = config.smtpUser,
        smtpPassword = config.smtpPassword,
        smtpFrom = config.smtpFrom,
        smtpTls = config.smtpTls,
    )

    /** Fire-and-forget: send a welcome email after registration. */
    fun sendWelcome(accountId: String) {
        if (!config.mailEnabled) return
        val lang = mailConfig.lang_fallback
        val (subject, body) = EmailTemplates.welcome(mailConfig, lang, config.publicUrl)
        val safeId = escapeHtml(accountId)
        mailer.send(accountId, subject, body.replace("{{email}}", safeId), body.replace("{{email}}", safeId))
    }

    /** Fire-and-forget: alert that a new device paired with the vault. */
    fun sendNewDeviceAlert(accountId: String, deviceName: String, platform: String?) {
        if (!config.mailEnabled) return
        val lang = mailConfig.lang_fallback
        val now = ZonedDateTime.now()
        val (subject, body) = EmailTemplates.newDevice(mailConfig, lang, escapeHtml(deviceName), platform?.let { escapeHtml(it) }, now, config.publicUrl)
        mailer.send(accountId, subject, body, body)
    }

    /** Fire-and-forget: master password changed notification. */
    fun sendPasswordChanged(accountId: String, deviceName: String) {
        if (!config.mailEnabled) return
        val lang = mailConfig.lang_fallback
        val now = ZonedDateTime.now()
        val (subject, body) = EmailTemplates.passwordChanged(mailConfig, lang, escapeHtml(deviceName), now, config.publicUrl)
        mailer.send(accountId, subject, body, body)
    }

    /** Fire-and-forget: suspicious login from new IP. */
    fun sendSuspiciousLogin(accountId: String, deviceName: String, ip: String) {
        if (!config.mailEnabled) return
        val lang = mailConfig.lang_fallback
        val now = ZonedDateTime.now()
        val (subject, body) = EmailTemplates.suspiciousLogin(mailConfig, lang, escapeHtml(deviceName), escapeHtml(ip), now, config.publicUrl)
        mailer.send(accountId, subject, body, body)
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
