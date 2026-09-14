package com.shankaravam.festival.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room is the single source of truth for the UI (AGENTS.md Rule #1).
 * exportSchema=false: schema snapshots start when the first migration ships (post-G5).
 *
 * v2: donations.honorific (pandal mic title — శ్రీ/శ్రీమతి/కుమారి, default శ్రీ).
 * v3: expenses.receiptUrl (Phase-3 gateway path, NULL until first upload).
 */
@Database(
    entities = [
        EventEntity::class,
        DonationEntity::class,
        ExpenseEntity::class,
        CorrectionEntity::class,
        ActivityEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun donationDao(): DonationDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun activityDao(): ActivityDao

    /**
     * Phase 1 local-scrub cascade (Step 2). Single SQLite transaction via
     * withTransaction — NOT @Transaction (which only works on @Dao methods).
     * Caller must gate on prefs.isCloudEvent(eventId)==false first.
     */
    suspend fun deleteEventCascade(eventId: String) {
        withTransaction {
            donationDao().deleteForEvent(eventId)
            expenseDao().deleteForEvent(eventId)
            correctionDao().deleteForEvent(eventId)
            activityDao().deleteForEvent(eventId)
            eventDao().deleteById(eventId)
        }
    }

    companion object {
        const val NAME = "shankaravam.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE donations ADD COLUMN honorific TEXT NOT NULL DEFAULT 'శ్రీ'"
                )
            }
        }

        /** Phase-3 receipts: nullable gateway path (no backfill — NULL until upload). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptUrl TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
