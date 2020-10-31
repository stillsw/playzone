package com.vtechtestapp.guesstheletters

import com.vtechtestapp.guesstheletters.data.GuessState
import com.vtechtestapp.guesstheletters.data.GuessViewModel
import org.junit.Test

import org.junit.Assert.*

/**
 * Simple tests of guess and results
 */
class GuessUnitTest {
    @Test
    fun guessStateTest1() {
        val testGuess1 = "ABC"
        // guess has one A, test in all positions for the 3 results
        assertEquals(GuessState.CORRECT, GuessViewModel.evaluateGuess('A', 0, testGuess1))
        assertEquals(GuessState.PARTIAL, GuessViewModel.evaluateGuess('A', 1, testGuess1))
        assertEquals(GuessState.PARTIAL, GuessViewModel.evaluateGuess('A', 2, testGuess1))
    }
    @Test
    fun guessStateTest2() {
        val testGuess2 = "ABA"
        // A in 3rd position
        assertEquals(GuessState.CORRECT, GuessViewModel.evaluateGuess('A', 2, testGuess2))
        // C is not in the string
        assertEquals(GuessState.WRONG, GuessViewModel.evaluateGuess('C', 0, testGuess2))
        assertEquals(GuessState.WRONG, GuessViewModel.evaluateGuess('C', 1, testGuess2))
        assertEquals(GuessState.WRONG, GuessViewModel.evaluateGuess('C', 2, testGuess2))
    }
    @Test
    fun guessStateTest3() {
        val testGuess3 = "CBA"
        // guess should return all 3 positions correct
        assertEquals(GuessState.CORRECT, GuessViewModel.evaluateGuess('C', 0, testGuess3))
        assertEquals(GuessState.CORRECT, GuessViewModel.evaluateGuess('B', 1, testGuess3))
        assertEquals(GuessState.CORRECT, GuessViewModel.evaluateGuess('A', 2, testGuess3))
    }
}