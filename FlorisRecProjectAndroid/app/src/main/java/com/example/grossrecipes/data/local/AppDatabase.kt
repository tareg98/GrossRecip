package com.example.grossrecipes.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.grossrecipes.data.SyncStateManager
import kotlinx.coroutines.runBlocking

@Database(
    entities = [
        ListEntity::class,
        ListItemEntity::class,
        OutboxEventEntity::class,
        DividerEntity::class,
        KnownItemNameEntity::class
    ],
    // 9: DividerEntity switched from item-anchored (afterItemId) to
    // position-anchored (gapIndex) - see its class doc.
    version = 9,
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
                    // A destructive migration wipes the local lists/items,
                    // but NOT SyncStateManager's "last synced" cursor - that
                    // lives in a completely separate DataStore that Room has
                    // no idea exists. Without this, the very next sync after
                    // a schema bump asks the server "what's changed since
                    // <the old cursor>" instead of "give me everything," so
                    // any list that already existed before the bump would
                    // never come back - exactly what wiped out a real list
                    // the first time this happened with actual data in it.
                    // Resetting the cursor here makes a schema bump behave
                    // like a fresh install: the next sync naturally re-pulls
                    // the whole history and rebuilds local state from it.
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                            super.onDestructiveMigration(db)
                            runBlocking { SyncStateManager(context.applicationContext).reset() }
                        }
                    })
                    .build().also { INSTANCE = it }
            }
        }
    }
}
