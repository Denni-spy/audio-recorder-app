package com.example.audiorecorder_app

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun startRecording(): File? {
        val recordingsDir = File(context.filesDir, "recordings")
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        currentFile = File(recordingsDir, "recording_${System.currentTimeMillis()}.mp3")
        
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFile?.absolutePath)
            prepare()
            start()
        }
        return currentFile
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
        }
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
        }
    }

    fun stopRecording(): File? {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorder", "Error stopping recorder", e)
        }
        mediaRecorder?.release()
        mediaRecorder = null
        return currentFile
    }
}

class AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null
    private var onStopListener: (() -> Unit)? = null

    fun play(path: String, onComplete: () -> Unit) {
        stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            setOnCompletionListener {
                onStopListener?.invoke()
                onComplete()
            }
            start()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun setOnStopListener(listener: () -> Unit): Any {
        this.onStopListener = listener
        return object : Any() {} // Return a handle as requested
    }

    fun removeStopListener(handle: Any) {
        this.onStopListener = null
    }
}
