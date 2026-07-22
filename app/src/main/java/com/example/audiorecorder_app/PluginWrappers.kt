package com.example.audiorecorder_app

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * License keys provided in the task.
 */
object Licenses {
    const val AUDIO_PLAYER_KEY = "ca_ccb1a883-52c3-4883-96e7-33ac0c0df555"
    const val AUDIO_RECORDER_KEY = "ca_ccb1a883-52c3-4883-96e7-33ac0c0df555"
}

/**
 * Mimics @capacitor/filesystem
 */
object FilesystemPlugin {
    fun getUri(context: Context, path: String): Uri {
        val file = File(path)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun rename(oldPath: String, newName: String): String {
        val oldFile = File(oldPath)
        val newFile = File(oldFile.parent, newName)
        if (oldFile.renameTo(newFile)) {
            return newFile.absolutePath
        }
        return oldPath
    }
}

/**
 * Mimics Capacitor File Picker Plugin
 */
object FilePickerPlugin {
    fun copyFile(context: Context, sourceUri: Uri, destinationPath: String) {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
        val outputStream = FileOutputStream(File(destinationPath))
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
    }
}
