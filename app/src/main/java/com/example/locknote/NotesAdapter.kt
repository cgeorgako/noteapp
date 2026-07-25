package com.example.locknote

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotesAdapter(
    private val onClick: (File) -> Unit,
    private val onDelete: (File) -> Unit
) : RecyclerView.Adapter<NotesAdapter.VH>() {

    private val items = ArrayList<File>()
    private val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun submit(files: List<File>) {
        items.clear()
        items.addAll(files)
        notifyDataSetChanged()
    }

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val thumb: ImageView = view.findViewById(R.id.thumb)
        val date: TextView = view.findViewById(R.id.date)
        val delete: ImageButton = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_note, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        // Downsample the thumbnail so the grid stays light on memory.
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        holder.thumb.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, opts))
        holder.date.text = fmt.format(Date(file.lastModified()))
        holder.itemView.setOnClickListener { onClick(file) }
        holder.delete.setOnClickListener { onDelete(file) }
    }

    override fun getItemCount(): Int = items.size
}
