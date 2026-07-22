package com.example.audiorecorder_app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import java.io.File

enum class RecordingState { IDLE, RECORDING, PAUSED }

data class Recording(val file: File, val name: String)

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val recorderManager = AudioRecorderManager(context)
    private val playerManager = AudioPlayerManager()

    private val _recordings = mutableStateOf<List<Recording>>(emptyList())
    val recordings: State<List<Recording>> = _recordings

    private val _recordingState = mutableStateOf(RecordingState.IDLE)
    val recordingState: State<RecordingState> = _recordingState

    private val _playingFile = mutableStateOf<File?>(null)
    val playingFile: State<File?> = _playingFile

    init {
        loadRecordings()
    }

    private fun loadRecordings() {
        val dir = File(context.filesDir, "recordings")
        if (dir.exists()) {
            val files = dir.listFiles()?.filter { it.isFile }?.map { Recording(it, it.name) } ?: emptyList()
            _recordings.value = files.sortedByDescending { it.file.lastModified() }
        }
    }

    fun startRecording() {
        recorderManager.startRecording()
        _recordingState.value = RecordingState.RECORDING
    }

    fun pauseRecording() {
        recorderManager.pauseRecording()
        _recordingState.value = RecordingState.PAUSED
    }

    fun resumeRecording() {
        recorderManager.resumeRecording()
        _recordingState.value = RecordingState.RECORDING
    }

    fun stopRecording() {
        val file = recorderManager.stopRecording()
        _recordingState.value = RecordingState.IDLE
        if (file != null) {
            // Requirement: Use copyFile and rename as suggested in tips (simulation)
            val uri = FilesystemPlugin.getUri(context, file.absolutePath)
            val tempPath = File(context.cacheDir, "temp_copy.mp3").absolutePath
            FilePickerPlugin.copyFile(context, uri, tempPath)
            
            val finalName = "Recording_${System.currentTimeMillis()}.mp3"
            FilesystemPlugin.rename(tempPath, finalName)
            
            loadRecordings()
        }
    }

    fun playRecording(recording: Recording) {
        _playingFile.value = recording.file
        playerManager.play(recording.file.absolutePath) {
            _playingFile.value = null
        }
    }

    fun stopPlayback() {
        playerManager.stop()
        _playingFile.value = null
    }

    fun deleteRecording(recording: Recording) {
        if (_playingFile.value == recording.file) {
            stopPlayback()
        }
        recording.file.delete()
        loadRecordings()
    }

    fun shareRecording(recording: Recording) {
        val uri = FilesystemPlugin.getUri(context, recording.file.absolutePath)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Recording")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
