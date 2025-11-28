package com.example.slip

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * A helper function to save a JSON string to the user's public "Downloads" folder.
 *
 * @param context The application context, needed to access the ContentResolver.
 * @param jsonString The complete JSON string to be saved.
 * @param fileName The name of the file to be created (e.g., "sleep_data.json").
 * @return `true` if the file was saved successfully, `false` otherwise.
 */
fun saveJsonToFile(context: Context, jsonString: String, fileName: String): Boolean {
    // For modern Android versions (10/Q and above), we use the MediaStore API.
    // This is the modern, recommended way that doesn't require legacy storage permissions.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
            // This places the file in the "Downloads" directory.
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        // Insert a new file entry into the MediaStore.
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)

        // If the entry was created successfully, open an output stream and write the data.
        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray())
                }
                return true // Success
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }
    } else {
        // For older Android versions (below 10), we write directly to the public Downloads directory.
        // This requires the WRITE_EXTERNAL_STORAGE permission in the manifest (with maxSdkVersion="28").
        @Suppress("DEPRECATION") // This method is deprecated but required for older APIs.
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        try {
            file.writeText(jsonString)
            return true // Success
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
    return false // Default to failure
}
