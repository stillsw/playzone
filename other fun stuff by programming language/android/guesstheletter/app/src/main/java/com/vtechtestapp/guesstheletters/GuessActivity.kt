package com.vtechtestapp.guesstheletters

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.vtechtestapp.guesstheletters.data.GuessViewModel
import com.vtechtestapp.guesstheletters.databinding.ActivityGuessBinding
import com.vtechtestapp.guesstheletters.ui.GuessListAdapter

/**
 * The single activity for the app
 */
class GuessActivity : AppCompatActivity() {

    companion object {
        private const val SHOW_DIALOG_DELAY_MILLIS = 600L
        private const val SHARED_PREFS = "com.vtechtestapp.guesstheletters.prefs"
        private const val TOGGLE_ANSWER_PREF = "answer"
        private const val SEEN_HELP_PREF = "help"
    }

    private val viewModel: GuessViewModel by viewModels()
    private var isGameWon = false
    private lateinit var binding: ActivityGuessBinding

    private var seenGuessHelp = false //  help text after completed false guess, once only

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // references to views will be through the binding

        binding = DataBindingUtil.setContentView(this, R.layout.activity_guess)
        binding.apply {
            lifecycleOwner = this@GuessActivity
            viewmodel = viewModel
        }

        setSupportActionBar(binding.toolbar)

        val listAdapter = GuessListAdapter(this)
        binding.recyclerview.apply {
            adapter = listAdapter

            // rather than provide another layout xml for landscape, layout the buttons
            // horizontally for landscape based on metrics

            val isLandscape = with(resources.displayMetrics) { heightPixels < widthPixels}
            val layoutDirection = if (isLandscape) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            layoutManager = LinearLayoutManager(this@GuessActivity, layoutDirection, false)

            setHasFixedSize(true)
        }

        viewModel.guesses.observe(this@GuessActivity, Observer { guesses ->
            guesses?.let { listAdapter.setGuesses(it) }
        })

        // fab lets the player begin again at any time

        binding.fab.setOnClickListener { view ->

            if (isGameWon) {
                viewModel.reset()
            }
            else {
                // confirm reset only if game is not already won
                Snackbar.make(view, getString(R.string.ask_restart), Snackbar.LENGTH_LONG)
                    .setAction(R.string.confirm_restart) { viewModel.reset() }.show()
            }
        }

        // disable the buttons if the game is over
        // (could be after orientation change or restart)

        viewModel.randomPuzzle.observe(this, Observer { puzzle ->
            puzzle?.let {

                isGameWon = puzzle.currentPuzzle == puzzle.guessEntered
                setButtonsEnabled(!isGameWon)
            }
        })

        // show or hide the answer views depending on last toggle stored in shared preferences

        getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE).also { prefs ->
            val vis = if (prefs.getBoolean(TOGGLE_ANSWER_PREF, true)) View.VISIBLE else View.INVISIBLE
            binding.currentPuzzle.visibility = vis
            binding.answerLabel.visibility = vis

            seenGuessHelp = prefs.getBoolean(SEEN_HELP_PREF, false)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    /**
     * Toggle showing the answer, save to shared preferences
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_answer -> {
                (if (binding.currentPuzzle.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE)
                    .also { vis ->
                        binding.currentPuzzle.visibility = vis
                        binding.answerLabel.visibility = vis

                        getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE).also { prefs ->
                            with(prefs.edit()) {
                                putBoolean(TOGGLE_ANSWER_PREF, vis == View.VISIBLE)
                                commit()
                            }
                        }
                    }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * all three buttons call this method
     * enters the guessed letter and pops up a dialog if the game is won
     * also shows a help message the first the all the letters are
     * completed but the game isn't won
     */
    fun buttonClicked(view: View) {
        val letter = (view as Button).text
        isGameWon = viewModel.addGuess(letter[0])

        if (isGameWon) {
            // make a dialog to congratulate and reset
            showCongratsDialog()

            // disabling the buttons isn't really needed as the observer takes
            // care of it, but to be sure
            setButtonsEnabled(false)
        }
        else if (!seenGuessHelp && binding.currentGuess.text.length >= 2) { // length is 2 because the db is updating in the background

            Snackbar.make(view, getString(R.string.first_time_help), Snackbar.LENGTH_LONG)
                .setAction(R.string.got_it) {  }.show()

            seenGuessHelp = true

            getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE).also { prefs ->
                with(prefs.edit()) {
                    putBoolean(SEEN_HELP_PREF, true)
                    commit()
                }
            }
        }
    }

    /**
     * Create a short delay before popping the dialog up, so the player
     * can see the result before the dialog obscures it
     */
    private fun showCongratsDialog() {

        binding.currentGuess.postDelayed({

            AlertDialog.Builder(this).apply {
                setView(R.layout.game_over_dialog)
                setPositiveButton(R.string.ok_button) { _, _ ->
                    viewModel.reset()
                }
                setNegativeButton(R.string.cancel_button) { _, _ ->
                    // do nothing
                }
                create().show()
            }

        }, SHOW_DIALOG_DELAY_MILLIS)
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.clickButtonA.isEnabled = enabled
        binding.clickButtonB.isEnabled = enabled
        binding.clickButtonC.isEnabled = enabled
    }
}