package com.example.grossrecipes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ListEntity::class,
        ListItemEntity::class,
        OutboxEventEntity::class,
        DividerEntity::class,
        KnownItemNameEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(StringListConverter::class, EventConverter::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun listDao(): ListDao
    abstract fun listItemDao(): ListItemDao
    abstract fun outboxEventDao(): OutboxEventDao
    abstract fun dividerDao(): DividerDao
    abstract fun knownItemNameDao(): KnownItemNameDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "grossrecipes.db"
                )
                    // No real user data to preserve yet during development -
                    // wipes and recreates the local db on schema changes.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
