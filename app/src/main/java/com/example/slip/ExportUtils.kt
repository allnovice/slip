package com.example.slip

import android.content.ContentValues
import android.content.Context
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File

/**
 * Saves a given text content (like JSON or CSV) to a file in the user's Downloads folder.
 * Returns true on success, false on failure.
 */
@RequiresApi(Build.VERSION_CODES.Q)
fun saveTextToFile(context: Context, content: String, fileName: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv") // Set mime type for CSV
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it).use { outputStream ->
                outputStream?.write(content.toByteArray())
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

/**
 * Copies the app's Room database file to the user's Downloads folder.
 * Returns true on success, false on failure.
 */
@RequiresApi(Build.VERSION_CODES.Q)
fun backupDatabase(context: Context, dbName: String): Boolean {
    return try {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists()) {
            return false // Database file doesn't exist
        }

        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${dbName}_backup_${System.currentTimeMillis()}.db")
            // Use a generic mime type for database files
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it).use { outputStream ->
                dbFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream!!)
                }
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}
