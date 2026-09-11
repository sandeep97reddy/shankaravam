package com.shankaravam.festival.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room is the single source of truth for the UI (AGENTS.md Rule #1).
 * exportSchema=false: schema snapshots start when the first migration ships (post-G5).
 *
 * v2: donations.honorific (pandal mic title — శ్రీ/శ్రీమతి/కుమారి, default శ్రీ).
 */
@Database(
    entities = [
        EventEntity::class,
        DonationEntity::class,
        ExpenseEntity::class,
        CorrectionEntity::class,
        ActivityEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun donationDao(): DonationDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun activityDao(): ActivityDao

    companion object {
        const val NAME = "shankaravam.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE donations ADD COLUMN honorific TEXT NOT NULL DEFAULT 'శ్రీ'"
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
