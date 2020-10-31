package com.vtechtestapp.guesstheletters.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * View model to keep current data during device orientation changes
 * Caches the repository data for observers
 */
class GuessViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GuessRepository
    val randomPuzzle: LiveData<RandomPuzzle>
    val guesses: LiveData<List<Guess>>

    init {
        GuessDatabase.getDatabase(application, viewModelScope).also {db ->
            repository = GuessRepository(db.randomPuzzleDao(), db.guessDao())
            randomPuzzle = repository.randomPuzzle
            guesses = repository.guesses
        }
    }

    companion object {
        /**
         * Pass the current puzzle to make unit test easier
         */
        fun evaluateGuess(letter: Char, index: Int, currentPuzzle: String): GuessState {
            return when {
                currentPuzzle[index] == letter -> GuessState.CORRECT
                currentPuzzle.contains(letter, true) -> GuessState.PARTIAL
                else -> GuessState.WRONG
            }
        }
    }

    /**
     * There's only ever one random set of letters, and never deleted
     * null check is forced by LiveData class
     */
    fun reset() = viewModelScope.launch(Dispatchers.IO) {
        repository.reset()
    }

    /**
     * Evaluate the guess against the random letters
     *      add it to the database with the correct type
     *      remove the oldest guess from the database to maintain just 3
     *      each time it's a 4th guess, clear the guess string and start again
     *      return true if the guess is completely correct (all 3 chars correct)
     * return false if puzzle is missing
     */
    fun addGuess(letter: Char): Boolean {

        val puzzle = randomPuzzle.value

        return if (puzzle != null) {

            // make the return value from the current string plus new letter because the
            // coroutine doesn't run immediately
            val retValue = puzzle.currentPuzzle == puzzle.guessEntered + letter

            viewModelScope.launch(Dispatchers.IO) {

                // remove the oldest one and add a new one (same transaction)
                // add the latest guess
                repository.enqueueGuess(
                    Guess(null, letter,
                        evaluateGuess(letter, puzzle.guessEntered.length % 3, puzzle.currentPuzzle)))

                // update the current guess
                if (puzzle.guessEntered.length == 3) {
                    puzzle.guessEntered = letter.toString()
                }
                else {
                    puzzle.guessEntered += letter
                }

                repository.update(puzzle)
            }

            // true if it's a complete match
            retValue
        }
        else {
            Log.d("GuessViewModel", "addGuess: no random letters should not be possible, returning false will cause no action")
            false
        }
    }

}