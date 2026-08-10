import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { FilePicker } from '@capawesome/capacitor-file-picker';
import { AudioPlayer } from '@capawesome-team/capacitor-audio-player';
import { AudioRecorder } from '@capawesome-team/capacitor-audio-recorder';

/** Verzeichnis, in dem die Aufnahmen dauerhaft abgelegt werden. */
const STORAGE_DIRECTORY = Directory.Data;
const RECORDINGS_PATH = 'recordings';
/** Zwischenname, unter dem eine frische Aufnahme kopiert und danach umbenannt wird. */
const TEMP_BASENAME = 'temp-recording';
const PROGRESS_INTERVAL_MS = 250;

const RecorderState = {
  Inactive: 'INACTIVE',
  Recording: 'RECORDING',
  Paused: 'PAUSED',
};

const state = {
  /** @type {{name: string, path: string, uri: string, mtime: number}[]} */
  recordings: [],
  recorderState: RecorderState.Inactive,
  hasPermission: false,
  /** Dateiname der aktuell abgespielten Aufnahme, sonst null. */
  playingName: null,
  playbackPosition: 0,
  playbackDuration: 0,
  /** Handle des 'stop'-Listeners des Audio Player Plugins (muss manuell entfernt werden). */
  playbackStopHandle: null,
  playbackTimer: null,
  recordTimer: null,
  recordElapsedMs: 0,
  recordStartedAt: 0,
};

const elements = {
  permissionHint: document.getElementById('permission-hint'),
  permissionButton: document.getElementById('permission-button'),
  recorderPanel: document.getElementById('recorder-panel'),
  recorderStatus: document.getElementById('recorder-status'),
  recorderTimer: document.getElementById('recorder-timer'),
  pauseButton: document.getElementById('pause-button'),
  stopButton: document.getElementById('stop-button'),
  recordButton: document.getElementById('record-button'),
  recordingList: document.getElementById('recording-list'),
  emptyHint: document.getElementById('empty-hint'),
  toast: document.getElementById('toast'),
};

let toastTimer = null;

function showToast(message) {
  elements.toast.textContent = message;
  elements.toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    elements.toast.hidden = true;
  }, 3000);
}

function formatClock(milliseconds) {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const minutes = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

function buildFileName(extension) {
  const now = new Date();
  const pad = (value) => String(value).padStart(2, '0');
  const date = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
  const time = `${pad(now.getHours())}-${pad(now.getMinutes())}-${pad(now.getSeconds())}`;
  return `Aufnahme_${date}_${time}.${extension}`;
}

/** Liest die Dateiendung aus der vom Recorder gelieferten URI (Fallback: m4a). */
function extractExtension(uri) {
  const withoutQuery = uri.split('?')[0];
  const match = withoutQuery.match(/\.([a-zA-Z0-9]{1,5})$/);
  return match ? match[1].toLowerCase() : 'm4a';
}

/** Stellt sicher, dass Pfade als URI vorliegen - die Plugins erwarten URIs. */
function toUri(value) {
  return /^[a-z][a-z0-9+.-]*:/i.test(value) ? value : `file://${value}`;
}

async function ensureRecordingsDirectory() {
  try {
    await Filesystem.mkdir({
      directory: STORAGE_DIRECTORY,
      path: RECORDINGS_PATH,
      recursive: true,
    });
  } catch (error) {
    // Das Verzeichnis existiert bereits - das ist kein Fehlerfall.
    if (!String(error?.message ?? '').toLowerCase().includes('exist')) {
      throw error;
    }
  }
}

// ---------------------------------------------------------------------------
// Berechtigungen
// ---------------------------------------------------------------------------

async function refreshPermission() {
  const { recordAudio } = await AudioRecorder.checkPermissions();
  state.hasPermission = recordAudio === 'granted';
  render();
}

async function requestPermission() {
  const { recordAudio } = await AudioRecorder.requestPermissions();
  state.hasPermission = recordAudio === 'granted';
  if (!state.hasPermission) {
    showToast('Ohne Mikrofon-Berechtigung sind keine Aufnahmen möglich.');
  }
  render();
}

// ---------------------------------------------------------------------------
// Aufnahmen laden (Persistenz)
// ---------------------------------------------------------------------------

async function loadRecordings() {
  await ensureRecordingsDirectory();
  const { files } = await Filesystem.readdir({
    directory: STORAGE_DIRECTORY,
    path: RECORDINGS_PATH,
  });

  state.recordings = files
    .filter((file) => file.type === 'file' && !file.name.startsWith(TEMP_BASENAME))
    .map((file) => ({
      name: file.name,
      path: `${RECORDINGS_PATH}/${file.name}`,
      uri: toUri(file.uri),
      mtime: file.mtime,
    }))
    .sort((a, b) => b.mtime - a.mtime);

  render();
}

// ---------------------------------------------------------------------------
// Aufnahme
// ---------------------------------------------------------------------------

function startRecordTimer() {
  state.recordStartedAt = Date.now();
  clearInterval(state.recordTimer);
  state.recordTimer = setInterval(() => {
    elements.recorderTimer.textContent = formatClock(currentRecordElapsed());
  }, 500);
}

function currentRecordElapsed() {
  if (state.recorderState === RecorderState.Recording) {
    return state.recordElapsedMs + (Date.now() - state.recordStartedAt);
  }
  return state.recordElapsedMs;
}

function stopRecordTimer() {
  clearInterval(state.recordTimer);
  state.recordTimer = null;
}

async function startRecording() {
  if (!state.hasPermission) {
    await requestPermission();
    if (!state.hasPermission) {
      return;
    }
  }

  await stopPlayback();
  await AudioRecorder.startRecording();

  state.recorderState = RecorderState.Recording;
  state.recordElapsedMs = 0;
  startRecordTimer();
  render();
}

async function pauseRecording() {
  await AudioRecorder.pauseRecording();
  state.recordElapsedMs += Date.now() - state.recordStartedAt;
  state.recorderState = RecorderState.Paused;
  stopRecordTimer();
  render();
}

async function resumeRecording() {
  await AudioRecorder.resumeRecording();
  state.recorderState = RecorderState.Recording;
  startRecordTimer();
  render();
}

/**
 * Beendet die Aufnahme und legt die Datei dauerhaft ab.
 *
 * Der Recorder schreibt zunächst in den Cache. Von dort wird die Datei gemäß
 * Aufgabenstellung mit copyFile(...) des File Picker Plugins kopiert - die
 * Ziel-URI stammt aus getUri(...) des Filesystem Plugins - und anschließend
 * mit rename(...) auf ihren endgültigen Namen gesetzt.
 */
async function stopRecording() {
  const { uri } = await AudioRecorder.stopRecording();
  state.recorderState = RecorderState.Inactive;
  stopRecordTimer();
  state.recordElapsedMs = 0;
  render();

  if (!uri) {
    showToast('Die Aufnahme konnte nicht gespeichert werden.');
    return;
  }

  await ensureRecordingsDirectory();

  const extension = extractExtension(uri);
  const tempPath = `${RECORDINGS_PATH}/${TEMP_BASENAME}.${extension}`;
  const { uri: tempUri } = await Filesystem.getUri({
    directory: STORAGE_DIRECTORY,
    path: tempPath,
  });

  await FilePicker.copyFile({ from: toUri(uri), to: toUri(tempUri) });

  await Filesystem.rename({
    from: tempPath,
    to: `${RECORDINGS_PATH}/${buildFileName(extension)}`,
    directory: STORAGE_DIRECTORY,
    toDirectory: STORAGE_DIRECTORY,
  });

  await loadRecordings();
}

// ---------------------------------------------------------------------------
// Wiedergabe
// ---------------------------------------------------------------------------

function stopPlaybackTimer() {
  clearInterval(state.playbackTimer);
  state.playbackTimer = null;
}

async function removeStopListener() {
  // Das Audio Player Plugin besitzt keine removeAllListeners()-Methode,
  // der Listener muss deshalb über sein Handle entfernt werden.
  const handle = state.playbackStopHandle;
  state.playbackStopHandle = null;
  if (handle) {
    await handle.remove();
  }
}

function resetPlaybackState() {
  state.playingName = null;
  state.playbackPosition = 0;
  state.playbackDuration = 0;
  stopPlaybackTimer();
  render();
}

async function playRecording(recording) {
  if (state.playingName) {
    await stopPlayback();
  }

  state.playbackStopHandle = await AudioPlayer.addListener('stop', () => {
    void removeStopListener().finally(resetPlaybackState);
  });

  await AudioPlayer.play({ uri: recording.uri });

  state.playingName = recording.name;
  state.playbackPosition = 0;
  state.playbackDuration = 0;
  render();

  stopPlaybackTimer();
  state.playbackTimer = setInterval(async () => {
    if (!state.playingName) {
      return;
    }
    try {
      const [{ position }, { duration }] = await Promise.all([
        AudioPlayer.getCurrentPosition(),
        AudioPlayer.getDuration(),
      ]);
      state.playbackPosition = position;
      state.playbackDuration = duration;
      renderProgress();
    } catch {
      // Nach dem Ende der Wiedergabe können die Abfragen fehlschlagen.
    }
  }, PROGRESS_INTERVAL_MS);
}

async function stopPlayback() {
  if (!state.playingName) {
    return;
  }
  stopPlaybackTimer();
  try {
    await AudioPlayer.stop();
  } catch {
    // Wiedergabe wurde bereits beendet.
  }
  await removeStopListener();
  resetPlaybackState();
}

// ---------------------------------------------------------------------------
// Teilen / Löschen
// ---------------------------------------------------------------------------

async function shareRecording(recording) {
  await Share.share({
    title: recording.name,
    files: [recording.uri],
  });
}

async function deleteRecording(recording) {
  if (state.playingName === recording.name) {
    await stopPlayback();
  }
  await Filesystem.deleteFile({
    directory: STORAGE_DIRECTORY,
    path: recording.path,
  });
  await loadRecordings();
  showToast(`"${recording.name}" wurde gelöscht.`);
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

function renderProgress() {
  if (!state.playingName) {
    return;
  }
  const bar = elements.recordingList.querySelector('.progress-bar');
  const current = elements.recordingList.querySelector('.progress-current');
  const total = elements.recordingList.querySelector('.progress-total');
  if (!bar || !current || !total) {
    return;
  }
  const ratio = state.playbackDuration > 0 ? state.playbackPosition / state.playbackDuration : 0;
  bar.style.width = `${Math.min(100, Math.max(0, ratio * 100))}%`;
  current.textContent = formatClock(state.playbackPosition);
  total.textContent = formatClock(state.playbackDuration);
}

function createIconButton(label, symbol, onClick, isDanger = false) {
  const button = document.createElement('button');
  button.className = isDanger ? 'icon-button is-danger' : 'icon-button';
  button.type = 'button';
  button.textContent = symbol;
  button.setAttribute('aria-label', label);
  button.addEventListener('click', () => void guard(onClick)());
  return button;
}

function createRecordingItem(recording) {
  const isPlaying = state.playingName === recording.name;

  const item = document.createElement('li');
  item.className = 'recording';

  const row = document.createElement('div');
  row.className = 'recording-row';

  const name = document.createElement('span');
  name.className = 'recording-name';
  name.textContent = recording.name;
  row.appendChild(name);

  row.appendChild(
    isPlaying
      ? createIconButton('Wiedergabe stoppen', '■', () => stopPlayback())
      : createIconButton('Aufnahme abspielen', '▶', () => playRecording(recording)),
  );
  row.appendChild(createIconButton('Aufnahme teilen', '⇪', () => shareRecording(recording)));
  row.appendChild(createIconButton('Aufnahme löschen', '🗑', () => deleteRecording(recording), true));

  item.appendChild(row);

  if (isPlaying) {
    const progress = document.createElement('div');
    progress.className = 'progress';
    progress.innerHTML = `
      <div class="progress-track"><div class="progress-bar"></div></div>
      <div class="progress-time">
        <span class="progress-current">00:00</span>
        <span class="progress-total">00:00</span>
      </div>
    `;
    item.appendChild(progress);
  }

  return item;
}

function render() {
  const isRecording = state.recorderState !== RecorderState.Inactive;

  elements.permissionHint.hidden = state.hasPermission;
  // Der Button bleibt bedienbar: fehlt die Berechtigung, wird sie beim Klick angefragt.
  elements.recordButton.hidden = isRecording;

  elements.recorderPanel.hidden = !isRecording;
  elements.recorderStatus.textContent =
    state.recorderState === RecorderState.Paused ? 'Pausiert' : 'Aufnahme läuft';
  elements.recorderStatus.classList.toggle(
    'is-paused',
    state.recorderState === RecorderState.Paused,
  );
  elements.pauseButton.textContent =
    state.recorderState === RecorderState.Paused ? 'Fortsetzen' : 'Pause';
  elements.recorderTimer.textContent = formatClock(currentRecordElapsed());

  elements.emptyHint.hidden = state.recordings.length > 0;
  elements.recordingList.replaceChildren(...state.recordings.map(createRecordingItem));
  renderProgress();
}

// ---------------------------------------------------------------------------
// Verdrahtung
// ---------------------------------------------------------------------------

/** Kapselt asynchrone Handler, damit Plugin-Fehler sichtbar werden. */
function guard(action) {
  return async (...args) => {
    try {
      await action(...args);
    } catch (error) {
      console.error(error);
      showToast(error?.message ?? 'Es ist ein Fehler aufgetreten.');
    }
  };
}

elements.permissionButton.addEventListener('click', guard(requestPermission));
elements.recordButton.addEventListener('click', guard(startRecording));
elements.stopButton.addEventListener('click', guard(stopRecording));
elements.pauseButton.addEventListener('click',
  guard(() =>
    state.recorderState === RecorderState.Paused ? resumeRecording() : pauseRecording(),
  ),
);

document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && !state.hasPermission) {
    void guard(refreshPermission)();
  }
});

void guard(async () => {
  await refreshPermission();
  await loadRecordings();
})();
