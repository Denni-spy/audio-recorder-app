package com.example.audiorecorder_app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.audiorecorder_app.ui.theme.AudioRecorderAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioRecorderAppTheme {
                AudioRecorderScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioRecorderScreen(viewModel: AudioViewModel = viewModel()) {
    val context = LocalContext.current
    val recordings by viewModel.recordings
    val recordingState by viewModel.recordingState
    val playingFile by viewModel.playingFile
    val playbackProgress by viewModel.playbackProgress

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Audio Recorder") })
        },
        floatingActionButton = {
            if (hasPermission) {
                FloatingActionButton(
                    onClick = {
                        when (recordingState) {
                            RecordingState.IDLE -> viewModel.startRecording()
                            RecordingState.RECORDING -> viewModel.pauseRecording()
                            RecordingState.PAUSED -> viewModel.resumeRecording()
                        }
                    },
                    containerColor = if (recordingState != RecordingState.IDLE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = when (recordingState) {
                            RecordingState.IDLE -> Icons.Default.Mic
                            RecordingState.RECORDING -> Icons.Default.Pause
                            RecordingState.PAUSED -> Icons.Default.Mic
                        },
                        contentDescription = "Record"
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                    Text("Grant Recording Permission")
                }
            }

            if (recordingState != RecordingState.IDLE) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (recordingState == RecordingState.RECORDING) "Recording..." else "Paused",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Button(onClick = { viewModel.stopRecording() }) {
                            Text("Stop")
                        }
                    }
                }
            }

            Text(
                text = "My Recordings",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(recordings) { recording ->
                    RecordingItem(
                        recording = recording,
                        isPlaying = playingFile == recording.file,
                        progress = if (playingFile == recording.file) playbackProgress else 0f,
                        onPlay = { viewModel.playRecording(recording) },
                        onStop = { viewModel.stopPlayback() },
                        onDelete = { viewModel.deleteRecording(recording) },
                        onShare = { viewModel.shareRecording(recording) }
                    )
                }
            }
        }
    }
}
