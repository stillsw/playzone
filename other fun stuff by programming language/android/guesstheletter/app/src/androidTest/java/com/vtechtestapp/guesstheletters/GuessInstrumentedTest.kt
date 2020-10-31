package com.vtechtestapp.guesstheletters

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vtechtestapp.guesstheletters.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import java.io.IOException

/**
 * Instrumented test
 * Complicated by need for coroutine scope, followed instructions from here:
 * https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-test/
 * added to build to get over problem:
    packagingOptions { pickFirst  'META-INF/ *'
 */

@ObsoleteCoroutinesApi
@RunWith(AndroidJUnit4::class)
class GuessInstrumentedTest {
    private lateinit var context: Context
    private lateinit var guessDatabase: GuessDatabase
    private lateinit var guessDao: GuessDao

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @ExperimentalCoroutinesApi
    @Before
    fun initTesting() {
        Dispatchers.setMain(mainThreadSurrogate)

        context = ApplicationProvider.getApplicationContext<Context>()
        guessDatabase = Room.inMemoryDatabaseBuilder(context,
            GuessDatabase::class.java).build()
        guessDao = guessDatabase.guessDao()
    }

    @ExperimentalCoroutinesApi
    @After
    @Throws(IOException::class)
    fun tearDown() {
        guessDatabase.close()
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    @Test
    fun useAppContext() {
        // Context of the app under test - from the example
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.vtechtestapp.guesstheletters", appContext.packageName)
    }

    @Test
    @Throws(IOException::class)
    fun testEmpty() {
        // tests can examine the table

        val list = guessDao.getGuessesOldestFirst()
        assertEquals(0, list.size)
    }

    @Test
    @Throws(IOException::class)
    fun testPopulate() {

        runBlocking {
            launch(Dispatchers.Main) {
                guessDao.insert(Guess(null, ' ', GuessState.NONE))
                guessDao.insert(Guess(null, ' ', GuessState.NONE))
                guessDao.insert(Guess(null, ' ', GuessState.NONE))

                val list = guessDao.getGuessesOldestFirst()
                assertEquals("test 3 guesses inserted", 3, list.size)

                // guess id is auto-generated and the list is in order of it,
                // therefore the first one should have id = 1 and the last = 3
                val firstGuess = list[0]
                assertEquals("test first id is 1", 1L, firstGuess.guessId)

                val lastGuess = list[2]
                assertEquals("test last id is 3", 3L, lastGuess.guessId)
            }
        }
    }

    @Test
    @Throws(IOException::class)
    fun testAddGuess() {

        runBlocking {
            launch(Dispatchers.Main) {
                guessDao.insert(Guess(null, ' ', GuessState.NONE))
                guessDao.insert(Guess(null, ' ', GuessState.NONE))
                guessDao.insert(Guess(null, ' ', GuessState.NONE))

                val list = guessDao.getGuessesOldestFirst()
                assertEquals("test 3 guesses inserted", 3, list.size)

                val firstGuess = list[0]
                assertEquals("test first id is 1", 1L, firstGuess.guessId)

                // add a correct test guess by calling the method for that
                // should remove the oldest one and add a new one
                guessDao.insertDeleteTogether(firstGuess, Guess(null, 'A', GuessState.CORRECT))

                // getting the list a 2nd time
                val list2 = guessDao.getGuessesOldestFirst()

                // now first one should be index 2 and last one is the new one just added
                assertEquals("test first guess changed", 2L, list2[0].guessId)
                assertEquals("test last guess is the one marked as correct", GuessState.CORRECT, list2[2].state)

            }
        }
    }
}