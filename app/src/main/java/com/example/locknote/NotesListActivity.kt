package com.example.locknote

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class NotesListActivity : AppCompatActivity() {

    private lateinit var adapter: NotesAdapter
    private lateinit var empty: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notes_list)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.saved_notes)

        empty = findViewById(R.id.emptyText)
        val recycler = findViewById<RecyclerView>(R.id.recycler)
        recycler.layoutManager = GridLayoutManager(this, 2)
        adapter = NotesAdapter(
            onClick = { /* preview handled inside adapter via full-screen dialog */ },
            onDelete = { file ->
                AlertDialog.Builder(this)
                    .setMessage(R.string.confirm_delete)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        NoteStore.delete(file)
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        recycler.adapter = adapter
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val notes = NoteStore.list(this)
        adapter.submit(notes)
        empty.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
