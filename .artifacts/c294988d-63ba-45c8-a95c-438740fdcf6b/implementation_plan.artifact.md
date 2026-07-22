# Implementation Plan - Audio Recorder App

This plan outlines the development of a native Android Audio Recorder app using Jetpack Compose, fulfilling all the requirements including recording, playback, sharing, and persistence.

## User Review Required

> [!IMPORTANT]
> The provided task mentions **Capacitor plugins** and **license keys**. Since the current project is a **native Android (Jetpack Compose)** project and not a Capacitor/Hybrid project, I will implement the functionality using **native Android APIs** (`MediaRecorder`, `MediaPlayer`, `FileProvider`). To adhere to the "Tips" provided, I will create wrapper classes that mimic the requested plugin methods (`copyFile`, `rename`, `getCurrentPosition`, etc.).

## Proposed Changes

### 1. Configuration & Setup

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/AndroidManifest.xml)
- Add `RECORD_AUDIO` and `READ_EXTERNAL_STORAGE` permissions.
- Configure `FileProvider` for sharing audio files.

#### [NEW] [file_paths.xml](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/res/xml/file_paths.xml)
- Define paths for `FileProvider` to share files from internal storage.

### 2. Core Logic (Wrappers & Managers)

#### [NEW] [AudioEngine.kt](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/java/com/example/audiorecorder_app/AudioEngine.kt)
- `AudioRecorderManager`: Handles `MediaRecorder` logic (Start, Pause, Resume, Stop).
- `AudioPlayerManager`: Handles `MediaPlayer` logic (Play, Stop, Progress).
- Implements the `getCurrentPosition()` and `getDuration()` methods as requested.

#### [NEW] [PluginWrappers.kt](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/java/com/example/audiorecorder_app/PluginWrappers.kt)
- `FilesystemPlugin`: Mimics `@capacitor/filesystem` with `rename()` and `getUri()` methods.
- `FilePickerPlugin`: Mimics `Capacitor File Picker Plugin` with `copyFile()` method.
- Stores the provided **license keys** as constants for reference.

### 3. State Management

#### [NEW] [AudioViewModel.kt](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/java/com/example/audiorecorder_app/AudioViewModel.kt)
- Manages the list of recordings.
- Tracks recording state (Idle, Recording, Paused).
- Tracks playback state.
- Handles file operations (Delete, Share, Save).

### 4. UI Layer (Jetpack Compose)

#### [MODIFY] [MainActivity.kt](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/java/com/example/audiorecorder_app/MainActivity.kt)
- Implement the main UI with a `Scaffold`.
- `RecordingList`: A list of all recorded files.
- `ControlPanel`: Buttons for recording (Start/Pause/Resume/Stop).

#### [NEW] [RecordingItem.kt](file:///C:/Users/denni/AndroidStudioProjects/AudioRecorderApp/app/src/main/java/com/example/audiorecorder_app/RecordingItem.kt)
- Individual list item with Filename, Play/Stop, Share, and Delete buttons.

## Verification Plan

### Automated Tests
- Unit tests for `AudioViewModel` to ensure list updates and file naming logic.
- (Manual verification is primary for Audio/UI).

### Manual Verification
1. **Permissions**: App asks for Microphone permission on startup or when recording starts.
2. **Recording**: Press "Record", verify it records. "Pause" and "Resume" and check the resulting file.
3. **Persistence**: Stop recording, verify it appears in the list. Restart app, verify it's still there.
4. **Playback**: Play a recording, verify sound. Stop playback.
5. **Sharing**: Click "Share", verify the Android Share Sheet appears with the file.
6. **Deletion**: Click "Delete", verify it disappears from the list and storage.
7. **Stop Playback**: Verify playback can be stopped manually.
