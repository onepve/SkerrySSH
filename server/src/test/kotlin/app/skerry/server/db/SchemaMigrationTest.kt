package app.skerry.server.db

import kotlin.test.Test

class SchemaMigrationTest {

    @Test
    fun `createSchema on an existing populated schema migrates cleanly`() = withTestDb { db ->
        // withTestDb already ran createSchema once on a fresh file; a server restart runs it
        // again over the existing tables. sqlite-jdbc >= 3.50 reports an empty PK_NAME, which
        // makes Exposed emit "ALTER TABLE ... ADD PRIMARY KEY" — a statement SQLite cannot
        // execute. Both passes must succeed without it.
        Db.createSchema(db)
        seedAccount(db)
        Db.createSchema(db)
    }
}
