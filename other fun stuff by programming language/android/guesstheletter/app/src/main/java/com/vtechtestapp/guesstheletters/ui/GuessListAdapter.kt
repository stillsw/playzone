package com.vtechtestapp.guesstheletters.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vtechtestapp.guesstheletters.R
import com.vtechtestapp.guesstheletters.data.Guess

/**
 * Recycler view adapter/holder
 *
 */
class GuessListAdapter internal constructor(context: Context) :
    RecyclerView.Adapter<GuessListAdapter.GuessViewHolder>(){

    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private var guesses = emptyList<Guess>() // Cached copy of guesses

    inner class GuessViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val guessIdView: TextView = itemView.findViewById(R.id.guess_id)
        val guessLetterView: TextView = itemView.findViewById(R.id.guess_letter)
        val guessStateView: TextView = itemView.findViewById(R.id.guess_state)
        val guessStateImage: GuessStatefulImage = itemView.findViewById(R.id.state_image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuessViewHolder {
        val itemView = inflater.inflate(R.layout.recyclerview_item, parent, false)
        return GuessViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: GuessViewHolder, position: Int) {
        val current = guesses[position]
        holder.guessIdView.text = current.guessId!!.toString()
        holder.guessLetterView.text = current.letter.toString()
        holder.guessStateView.text = current.state.name
        holder.guessStateImage.guessState = current.state
    }

    internal fun setGuesses(guesses: List<Guess>) {
        this.guesses = guesses
        notifyDataSetChanged()
    }

    override fun getItemCount() = guesses.size
}