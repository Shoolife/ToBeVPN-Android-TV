package com.tobevpn.tv.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tobevpn.tv.data.local.AppDatabase
import com.tobevpn.tv.data.local.DatabasePassphrase
import com.tobevpn.tv.data.local.dao.ServerDao
import com.tobevpn.tv.data.local.dao.SessionDao
import com.tobevpn.tv.data.local.dao.TrafficLogDao
import com.tobevpn.tv.data.local.dao.UsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        val passphrase = DatabasePassphrase.getPassphrase(context)
        // If a pre-encryption DB file exists, SQLCipher throws
        // "file is not a database" on open. Drop incompatible leftovers so
        // upgrades from older builds do not get stuck in a crash loop.
        ensureCipherCompatible(context, "tobevpn_tv.db", passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "tobevpn_tv.db",
        )
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(MIGRATION_7_8)
            .fallbackToDestructiveMigration(dropAllTables = true)
            // Usage is updated while the tunnel is active. Keep committed
            // writes in the main DB instead of growing a separate WAL file.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE session ADD COLUMN planDisplayName TEXT")
        }
    }

    private fun ensureCipherCompatible(
        context: Context,
        dbName: String,
        passphrase: ByteArray,
    ) {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) return
        try {
            net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                passphrase,
                null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                null,
                null,
            ).close()
        } catch (_: android.database.SQLException) {
            dbFile.delete()
            java.io.File("${dbFile.absolutePath}-shm").delete()
            java.io.File("${dbFile.absolutePath}-wal").delete()
        } catch (_: Throwable) {
            // Do not delete user data for transient JNI or file-lock errors.
        }
    }

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideUsageDao(db: AppDatabase): UsageDao = db.usageDao()

    @Provides
    fun provideServerDao(db: AppDatabase): ServerDao = db.serverDao()

    @Provides
    fun provideTrafficLogDao(db: AppDatabase): TrafficLogDao = db.trafficLogDao()
}
