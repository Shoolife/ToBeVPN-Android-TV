package com.tobevpn.tv.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tobevpn.tv.di.DatabaseModule
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Before
    fun removePreviousTestDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration10To11PreservesServerAndAddsTransportDefaults() {
        helper.createDatabase(TEST_DATABASE, 10).use { database ->
            insertVersion10Server(database)
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            11,
            true,
            DatabaseModule.MIGRATION_10_11,
        ).use { database ->
            database.query(
                "SELECT id, name, host, alpn, headerType, serviceName, extra, sortOrder FROM servers",
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("server-id", cursor.getString(cursor.getColumnIndexOrThrow("id")))
                assertEquals("Saved server", cursor.getString(cursor.getColumnIndexOrThrow("name")))
                listOf("host", "alpn", "headerType", "serviceName", "extra").forEach { column ->
                    assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow(column)))
                }
                assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("sortOrder")))
            }
        }
    }

    @Test
    fun migration11To12CreatesPendingPromocodeActivationTable() {
        helper.createDatabase(TEST_DATABASE, 11).close()

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            12,
            true,
            DatabaseModule.MIGRATION_11_12,
        ).use { database ->
            database.execSQL(
                "INSERT INTO pending_promocode_activations " +
                    "(telegramId, code, requestId, createdAt) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(123L, "WELCOME", "request-id", 456L),
            )
            database.query(
                "SELECT telegramId, code, requestId, createdAt " +
                    "FROM pending_promocode_activations",
            ).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals(123L, cursor.getLong(cursor.getColumnIndexOrThrow("telegramId")))
                assertEquals("WELCOME", cursor.getString(cursor.getColumnIndexOrThrow("code")))
                assertEquals("request-id", cursor.getString(cursor.getColumnIndexOrThrow("requestId")))
                assertEquals(456L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
            }
        }
    }

    @Test
    fun fullMigration10To12PreservesServerAndCreatesPromocodeQueue() {
        helper.createDatabase(TEST_DATABASE, 10).use(::insertVersion10Server)

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            12,
            true,
            DatabaseModule.MIGRATION_10_11,
            DatabaseModule.MIGRATION_11_12,
        ).use { database ->
            database.query("SELECT COUNT(*) FROM servers").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            database.query(
                "SELECT COUNT(*) FROM sqlite_master " +
                    "WHERE type='table' AND name='pending_promocode_activations'",
            ).use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    private fun insertVersion10Server(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO servers (id, name, address, port, uuid, flow, security, sni, " +
                "fingerprint, publicKey, shortId, network, path, mode, spx, country, " +
                "isOnline, sortOrder) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(
                "server-id", "Saved server", "node.example", 443,
                "550e8400-e29b-41d4-a716-446655440000", "", "tls", "front.example",
                "chrome", "", "", "ws", "/vpn", "", "", "NL", 1, 7,
            ),
        )
    }

    private companion object {
        const val TEST_DATABASE = "tv-app-database-migration-test"
    }
}
