package com.nyamimo.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nyamimo.app.R

class GenreChipAdapter(
    private var genres: List<String>,
    private val onGenreClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<GenreChipAdapter.GenreViewHolder>() {

    fun updateData(newGenres: List<String>) {
        this.genres = newGenres
        notifyDataSetChanged()
    }

    inner class GenreViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuery: TextView = view.findViewById(R.id.tvHistoryQuery)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_history_chip, parent, false)
        return GenreViewHolder(view)
    }

    override fun onBindViewHolder(holder: GenreViewHolder, position: Int) {
        val genre = genres[position]
        holder.tvQuery.text = genre
        holder.itemView.setOnClickListener {
            onGenreClick?.invoke(genre)
        }
    }

    override fun getItemCount(): Int = genres.size
}
