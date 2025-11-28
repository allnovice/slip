package com.example.slip

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream

/**
 * Saves a given text content (like CSV) to a file in the user's Downloads folder.
 * This version is safe for all API levels your app supports (minSdk 26+).
 * Returns true on success, false on failure.
 */
fun saveTextToFile(context: Context, content: String, fileName: String): Boolean {
    return try {
        // --- THIS IS THE FIX ---
        // For modern Android versions (10 and above), use the modern MediaStore API.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                resolver.openOutputStream(it).use { outputStream ->
                    outputStream?.write(content.toByteArray())
                }
            }
        } else {
            // For older Android versions, use the classic (legacy) file path method.
            // Note: This requires WRITE_EXTERNAL_STORAGE permission on API < 29, which
            // should be declared in the Manifest for these older versions.
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(content.toByteArray())
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
