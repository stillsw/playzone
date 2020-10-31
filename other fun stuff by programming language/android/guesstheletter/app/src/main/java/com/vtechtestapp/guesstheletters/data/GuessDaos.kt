package com.vtechtestapp.guesstheletters.data

import androidx.lifecycle.LiveData
import androidx.room.*

import com.vtechtestapp.guesstheletters.R

/**
 * File holds all dao related interfaces/classes
 * BaseDao - interface for all Daos
 * GuessState - type for guesses
 * RandomPuzzle - DB table entity class
 * RandomPuzzleDao - Abstract Dao class to implement the table
 * Guess - DB table entity class
 * GuessDao - Abstract Dao with list getter
 * Converters - type conversion for DB data
 */

interface BaseDao<T> {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vararg obj: T)

    @Delete
    suspend fun delete(vararg obj: T)

    @Update
    suspend fun update(vararg obj: T)
}

// using styled attributes for drawable by state
enum class GuessState(val styledAttributeName: IntArray) {
    NONE(intArrayOf(R.attr.none)),
    WRONG(intArrayOf(R.attr.wrong)),
    PARTIAL(intArrayOf(R.attr.partial)),
    CORRECT(intArrayOf(R.attr.correct))
}

@Entity(tableName = "random_puzzle")
data class RandomPuzzle(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "seq_id") var seqId: Long?,
    @ColumnInfo(name = "current_puzzle") var currentPuzzle : String,
    @ColumnInfo(name = "guess_entered") var guessEntered : String
)

@Entity(tableName = "guesses")
data class Guess(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "guess_id") var guessId: Long?,
    @ColumnInfo(name = "letter") var letter : Char,
    @ColumnInfo(name = "guess_state") var state: GuessState
)

@Dao
abstract class RandomPuzzleDao : BaseDao<RandomPuzzle> {

    @Query("SELECT * FROM random_puzzle")
    abstract fun getPuzzle(): LiveData<RandomPuzzle>

    // called from the db initialisation, unlikely ever to be called since there shouldn't be any
    // data at that point
    @Query("DELETE FROM random_puzzle")
    abstract suspend fun deleteAll()
}

@Dao
abstract class GuessDao : BaseDao<Guess> {

    @Query("SELECT * FROM guesses ORDER BY guess_id DESC")
    abstract fun getGuesses(): LiveData<List<Guess>>

    @Query("SELECT * FROM guesses ORDER BY guess_id ASC")
    abstract fun getGuessesOldestFirst(): List<Guess>

    @Transaction
    open suspend fun insertDeleteTogether(deleteGuess: Guess, insertGuess: Guess) {
        delete(deleteGuess)
        insert(insertGuess)
    }

    @Query("DELETE FROM guesses")
    abstract suspend fun deleteAll()
}

class Converters {
    @TypeConverter
    fun guessStateToString(stateType: GuessState?): String? {
        return stateType?.toString()
    }

    @TypeConverter
    fun stringToGuessState(str: String?): GuessState? {
        return if (str != null) GuessState.valueOf(str) else null
    }
}