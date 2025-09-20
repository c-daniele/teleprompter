const script = document.getElementById('script');
const btnPlay = document.getElementById('btnPlay');
const btnPause = document.getElementById('btnPause');
const speedRange = document.getElementById('speedRange');
const btnRecord = document.getElementById('btnRecord');
const btnRestart = document.getElementById('btnRestart');
const scriptInput = document.getElementById('scriptInput');
// const resolutionSelect = document.getElementById('resolutionSelect');
const cameraSelect = document.getElementById('cameraSelect');
const micSelect = document.getElementById('microphoneSelect');
const btnToggleTextarea = document.getElementById('btnToggleTextarea');
const video = document.getElementById('video');

let scrollSpeed = 2;
let isScrolling = false;
let scrollInterval;
let mediaRecorder;
let recordedChunks = [];

async function getMicrophones() {
    const devices = await navigator.mediaDevices.enumerateDevices();
    const audioDevices = devices.filter(device => device.kind === 'audioinput');

    audioDevices.forEach(device => {
      const option = document.createElement('option');
      option.value = device.deviceId;
      option.text = device.label || `Microfono ${micSelect.length + 1}`;
      micSelect.appendChild(option);
    });
  }

btnToggleTextarea.addEventListener('click', () => {
    scriptInput.toggleAttribute('hidden');
    video.toggleAttribute('hidden');
});

btnPlay.addEventListener('click', () => {
    isScrolling = true;
    startScrolling();
});

btnPause.addEventListener('click', () => {
    isScrolling = false;
    clearInterval(scrollInterval);
});

speedRange.addEventListener('input', (e) => {
    scrollSpeed = e.target.value;
});

btnRecord.addEventListener('click', () => {
    if (mediaRecorder && mediaRecorder.state === "recording") {
        mediaRecorder.stop();
        btnRecord.textContent = "Record";
    } else {
        startRecording();
        btnRecord.textContent = "Stop";
    }
});

btnRestart.addEventListener('click', () => {
    resetScriptPosition();
});

scriptInput.addEventListener('input', (e) => {
    script.textContent = e.target.value;
});

// resolutionSelect.addEventListener('change', () => {
//     resetStream();
// });

cameraSelect.addEventListener('change', () => {
    resetStream();
});

function startScrolling() {
    clearInterval(scrollInterval);
    scrollInterval = setInterval(() => {
        if (isScrolling) {
            script.style.top = `${parseInt(script.style.top) - scrollSpeed}px`;
        }
    }, 200);
}

function resetScriptPosition() {
    script.style.top = '100%';
}

// Initialize script position
resetScriptPosition();

function getResolutionConstraints() {
    //const resolution = resolutionSelect.value.split('x');
    return {
        // width: { ideal: parseInt(resolution[1]) },
        // height: { ideal: parseInt(resolution[0]) }
        width: 1920,
        height: 1088
    };
}

function getCameraConstraints() {
    return {
        facingMode: cameraSelect.value
    };
}

function resetStream() {
    if (mediaRecorder && mediaRecorder.state === "recording") {
        mediaRecorder.stop();
        btnRecord.textContent = "Record";
    }

    const selectedDeviceId = micSelect.value;
    navigator.mediaDevices.getUserMedia({
        video: {
            ...getResolutionConstraints(),
            ...getCameraConstraints()
        },
        audio: { deviceId: selectedDeviceId }
    })
    .then(stream => {
        video.srcObject = stream;
        video.muted = true;  // Mute the video element to prevent audio playback
        setupRecorder(stream);
    })
    .catch(err => {
        console.error('Error accessing the camera and microphone: ', err);
    });
}

// Access the camera and microphone with initial resolution and camera
resetStream();

function setupRecorder(stream) {
    const options = { mimeType: 'video/mp4; codecs="avc1.42E01E, mp4a.40.2"' };

    if (!MediaRecorder.isTypeSupported(options.mimeType)) {
        console.error('H.264 non supportato, si utilizzerà il formato predefinito del browser');
    }

    mediaRecorder = new MediaRecorder(stream, options);
    mediaRecorder.ondataavailable = handleDataAvailable;
    mediaRecorder.onstop = handleStop;
}

function handleDataAvailable(event) {
    if (event.data.size > 0) {
        recordedChunks.push(event.data);
    }
}

function handleStop() {
    const blob = new Blob(recordedChunks, {
        type: 'video/mp4'
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.style.display = 'none';
    a.href = url;
    a.download = 'recording.mp4';
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    recordedChunks = [];
}

function startRecording() {
    recordedChunks = [];
    mediaRecorder.start();
}

 // Chiedi il permesso di accesso ai dispositivi e popola il menu
 navigator.mediaDevices.getUserMedia({ audio: true, video: true })
    .then(getMicrophones)
    .catch(error => console.error('Errore nell\'accesso ai dispositivi:', error));
