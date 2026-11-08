package com.asoc.ping.db

import com.asoc.ping.data.db.Migrations
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays MIGRATION_1_2 against the exported v1 schema and compares the result with a database
 * created straight from the exported v2 schema (columns, types, nullability, defaults, indices).
 */
class MigrationTest {
    private val dir = File("schemas/com.asoc.ping.data.db.AppDatabase")

    private fun schema(v: Int) = Json.parseToJsonElement(File(dir, "$v.json").readText()).jsonObject["database"]!!.jsonObject

    private fun create(conn: Connection, v: Int) {
        for (e in schema(v)["entities"]!!.jsonArray) {
            val o = e.jsonObject
            val t = o["tableName"]!!.jsonPrimitive.content
            conn.createStatement().execute(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t))
            o["indices"]?.jsonArray?.forEach { conn.createStatement().execute(it.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", t)) }
        }
    }

    private fun describe(conn: Connection): Map<String, List<String>> {
        val out = sortedMapOf<String, List<String>>()
        val tables = mutableListOf<String>()
        conn.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table')").use { while (it.next()) tables += it.getString(1) }
        for (t in tables) {
            val cols = mutableListOf<String>()
            conn.createStatement().executeQuery("PRAGMA table_info(`$t`)").use { while (it.next()) cols += "${it.getString("name")}:${it.getString("type")}:${it.getInt("notnull")}:${it.getString("dflt_value")}:${it.getInt("pk")}" }
            val idx = mutableListOf<String>()
            conn.createStatement().executeQuery("PRAGMA index_list(`$t`)").use { while (it.next()) if (it.getString("origin") == "c") idx += it.getString("name") + ":" + it.getInt("unique") }
            out[t] = cols.sorted() + idx.sorted().map { "idx:$it" }
        }
        return out
    }

    @Test fun `migration 1 to 2 yields exactly the v2 schema and keeps legacy rows in the default project`() {
        val migrated = DriverManager.getConnection("jdbc:sqlite::memory:")
        val fresh = DriverManager.getConnection("jdbc:sqlite::memory:")
        create(migrated, 1)
        migrated.createStatement().execute(
            "INSERT INTO incidents(id,title,summary,severity,status,isManual,createdAt,updatedAt) VALUES('inc_x','t','s','LOW','OPEN',0,1,1)")
        migrated.createStatement().execute(
            "INSERT INTO signals(sourceKey,type,title,body,createdAt,sourceUpdatedAt,ingestedAt,replyState) VALUES('github:o/r#1','GITHUB_ISSUE','t','b',1,1,1,'NONE')")
        Migrations.MIGRATION_1_2_SQL.forEach { migrated.createStatement().execute(it) }
        create(fresh, 2)

        assertEquals(describe(fresh), describe(migrated))
        migrated.createStatement().executeQuery("SELECT projectId FROM incidents").use { assertTrue(it.next()); assertEquals("default", it.getString(1)) }
        migrated.createStatement().executeQuery("SELECT projectId, sentReplyText FROM signals").use { assertTrue(it.next()); assertEquals("default", it.getString(1)); assertEquals(null, it.getString(2)) }
    }
}
