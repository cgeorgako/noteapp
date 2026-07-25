package com.example.locknote

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persists notes as PNG files in the app's private storage. */
object NoteStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "notes").apply { if (!exists()) mkdirs() }

    /** Save a bitmap; returns the created file. */
    fun save(context: Context, bitmap: Bitmap): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir(context), "note_$stamp.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    /** All saved notes, newest first. */
    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension.equals("png", true) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    fun delete(file: File): Boolean = file.delete()
}
