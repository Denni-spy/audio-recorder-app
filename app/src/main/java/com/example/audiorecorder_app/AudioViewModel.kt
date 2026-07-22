package com.example.audiorecorder_app

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

    private val _playbackProgress = mutableStateOf(0f)
    val playbackProgress: State<Float> = _playbackProgress

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
        val tempFile = recorderManager.stopRecording()
        _recordingState.value = RecordingState.IDLE
        
        if (tempFile != null && tempFile.exists()) {
            val recordingsDir = File(context.filesDir, "recordings")
            if (!recordingsDir.exists()) recordingsDir.mkdirs()

            val finalFile = File(recordingsDir, "temp_rec.mp3")
            
            // 1. Get URI for temp file (Filesystem Plugin tip)
            val uri = FilesystemPlugin.getUri(context, tempFile.absolutePath)
            
            // 2. Copy file to recordings dir (File Picker Plugin tip)
            FilePickerPlugin.copyFile(context, uri, finalFile.absolutePath)
            
            // 3. Rename to final timestamped name (Filesystem Plugin tip)
            val finalName = "Recording_${System.currentTimeMillis()}.mp3"
            FilesystemPlugin.rename(finalFile.absolutePath, finalName)
            
            loadRecordings()
        }
    }

    fun playRecording(recording: Recording) {
        _playingFile.value = recording.file
        
        var handle: Any? = null
        handle = playerManager.setOnStopListener {
            _playingFile.value = null
            _playbackProgress.value = 0f
            handle?.let { playerManager.removeStopListener(it) }
        }
        
        playerManager.play(recording.file.absolutePath) {
            _playingFile.value = null
            _playbackProgress.value = 0f
        }

        // Ticker to query current position and duration (Tip requirement)
        viewModelScope.launch {
            while (_playingFile.value == recording.file) {
                val pos = playerManager.getCurrentPosition()
                val dur = playerManager.getDuration()
                if (dur > 0) {
                    _playbackProgress.value = pos.toFloat() / dur
                }
                delay(100)
            }
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
