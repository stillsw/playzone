package com.vtechtestapp.guesstheletters.data

import android.util.Log
import java.lang.StringBuilder
import kotlin.random.Random

/**
 * Top level utility function to generate a random string
 * of letters from 3 choices: ABC
 */
fun generateRandomLetters() : String {

    return StringBuilder().apply {
        for (i in 0 until 3) {
            append('A'+ Random.nextInt(0, 3))
        }
    }.toString()
}

/**
 * Following the suggested best practice for Room, the repository is created by the view model
 * and becomes the go between for database actions
 */
class GuessRepository(private val randomPuzzleDao: RandomPuzzleDao, private val guessDao: GuessDao) {

    val randomPuzzle = randomPuzzleDao.getPuzzle()
    val guesses = guessDao.getGuesses()

    suspend fun update(randomPuzzle: RandomPuzzle) {
        randomPuzzleDao.update(randomPuzzle)
    }

    /**
     * There should always be 3 at a time, for now assume can never be an empty list
     * Functionally is queue-like behaviour (FIFO)
     */
    suspend fun enqueueGuess(newGuess: Guess) {
        guessDao.insertDeleteTogether(guessDao.getGuessesOldestFirst()[0], newGuess)
    }

    suspend fun reset() {
        randomPuzzle.value?.let {
            it.currentPuzzle = generateRandomLetters()
            it.guessEntered = ""
            update(it)
            guessDao.deleteAll()
            guessDao.insert(Guess(null, ' ', GuessState.NONE),
                Guess(null, ' ', GuessState.NONE),
                Guess(null, ' ', GuessState.NONE))

        } ?: Log.e("GuessRepository", "reset: should not fail with missing current string")
    }
}