package com.shankaravam.festival.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * G2 schema v1. Room is the single source of truth for the UI (AGENTS.md Rule #1).
 * exportSchema=false: schema snapshots start when the first migration ships (post-G5).
 */
@Database(
    entities = [
        EventEntity::class,
        DonationEntity::class,
        ExpenseEntity::class,
        CorrectionEntity::class,
        ActivityEntity::class
    ],
    version = 1,
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

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME).build()
    }
}
