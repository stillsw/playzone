package com.vtechtestapp.guesstheletters.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Very simple database, one table, one row to store the last random sequence
 * Overkill since could use shared prefs, but good to demonstrate knowledge of room
 */

@Database(entities = [RandomPuzzle::class, Guess::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class GuessDatabase : RoomDatabase() {

    abstract fun randomPuzzleDao(): RandomPuzzleDao
    abstract fun guessDao(): GuessDao

    companion object {
        // Singleton prevents multiple instances of database opening at the same time.

        @Volatile
        private var INSTANCE: GuessDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): GuessDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        GuessDatabase::class.java,
                        "guess_database")
                    .addCallback(GuessDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                return instance
            }
        }
    }

    private class GuessDatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch {
                    populateDatabase(database.randomPuzzleDao(), database.guessDao())
                }
            }
        }

        suspend fun populateDatabase(randomPuzzleDao: RandomPuzzleDao, guessDao: GuessDao) {
            // only called on create, so delete everything, which should be redundant
            randomPuzzleDao.deleteAll()
            guessDao.deleteAll()

            // add a single row for test sequence
            randomPuzzleDao.insert(RandomPuzzle(null, generateRandomLetters(), ""))

            // add 3 rows for the guesses
            guessDao.insert(Guess(null, ' ', GuessState.NONE),
                Guess(null, ' ', GuessState.NONE),
                Guess(null, ' ', GuessState.NONE))
        }
    }
}