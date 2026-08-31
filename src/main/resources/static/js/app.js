const serverStatus = document.querySelector("#server-status");
const statusCopy = serverStatus.querySelector(".status-copy");
const recorderPanel = document.querySelector(".recorder-panel");
const recordButton = document.querySelector("#record-button");
const recordingTimer = document.querySelector("#recording-timer");
const recorderStatus = document.querySelector(".recorder-footer p");
const panelStateCopy = document.querySelector(".panel-state-copy");
const recordButtonLabel = recordButton.querySelector(".visually-hidden"); //For TITAN to detect the stop control
const transcriptText = document.querySelector(".transcript-output p");
const characterCount = document.querySelector(".character-count");
const MAX_RECORDING_MS = 5 * 60 * 1000;
const recordingSupported = Boolean(
    navigator.mediaDevices?.getUserMedia && "MediaRecorder" in window
);

let timerInterval;
let recordingStartedAt;
let microphoneStream;
let mediaRecorder;
let audioChunks = [];

function setRecordingVisualState(isRecording) {
    recorderPanel.classList.toggle("is-recording", isRecording);
    recordButtonLabel.textContent = isRecording ? "Stop recording" : "Start recording";
    recordButton.setAttribute("aria-pressed", String(isRecording));
    panelStateCopy.textContent = isRecording ? "Recording" : "Ready";
}

function releaseMicrophone() {
    microphoneStream?.getTracks().forEach(track => track.stop());
    microphoneStream = undefined;
}

function finishRecordingSession(footerMessage, panelMessage) {
    stopRecordingTimer();
    setRecordingVisualState(false);
    releaseMicrophone();
    mediaRecorder = undefined;
    audioChunks = [];
    recordButton.disabled = !recordingSupported;
    recorderStatus.textContent = footerMessage;
    panelStateCopy.textContent = panelMessage;
}

if (recordingSupported) { // checks browser-support mic
    recordButton.disabled = false;
    recorderStatus.textContent = "Ready";
    setRecordingVisualState(false);
} else {
    setRecordingVisualState(false);
    recorderStatus.textContent = "Recording unsupported";
    panelStateCopy.textContent = "Unsupported";
}

function updateRecordingTimer() {
    const elapsedMs = Math.min(
        performance.now() - recordingStartedAt,
        MAX_RECORDING_MS
    );

    const minutes = Math.floor(elapsedMs / 60000);
    const seconds = Math.floor((elapsedMs % 60000) / 1000);
    const hundredths = Math.floor((elapsedMs % 1000) / 10);

    recordingTimer.textContent =
        `${String(minutes).padStart(2, "0")}:` +
        `${String(seconds).padStart(2, "0")}.` +
        `${String(hundredths).padStart(2, "0")}`;

    if (
        elapsedMs >= MAX_RECORDING_MS &&
        mediaRecorder?.state === "recording"
    ) {
        recordButton.disabled = true;
        mediaRecorder.stop();
    }
}

function startRecordingTimer() {
    recordingStartedAt = performance.now();
    recordingTimer.textContent = "00:00.00";
    timerInterval = setInterval(updateRecordingTimer, 50);
}

function stopRecordingTimer() {
    clearInterval(timerInterval);
    timerInterval = undefined;
}

async function uploadRecording(audioBlob) {
    const formData = new FormData();

    /*
     * "audio" must match @RequestParam("audio") in the Java controller.
     * The Blob becomes a file-like multipart request part.
     */
    formData.append("audio", audioBlob, "recording.webm");

    const response = await fetch("/api/v1/transcriptions", {
        method: "POST",
        body: formData
    });

    /*
     * Do not manually set the Content-Type header.
     * The browser adds multipart/form-data and its required boundary.
     */
    if (!response.ok) {
        throw new Error(`Upload failed with HTTP ${response.status}`);
    }

    const transcription = await response.json();

    if (typeof transcription.text !== "string") {
        throw new Error("The server returned an invalid transcription");
    }

    return transcription.text;
}

recordButton.addEventListener("click", async () => {
    if (mediaRecorder?.state === "recording") {
        recordButton.disabled = true;
        mediaRecorder.stop();
        return;
    }

    recordButton.disabled = true;
    recorderStatus.textContent = "Requesting microphone";
    panelStateCopy.textContent = "Connecting";

    try {
        microphoneStream =
            await navigator.mediaDevices.getUserMedia({ audio: true }); //		the live sound coming from the microphone


        mediaRecorder = new MediaRecorder(microphoneStream);
        const currentRecorder = mediaRecorder;
        let sessionFinished = false;
        audioChunks = [];

        currentRecorder.addEventListener("dataavailable", event => {  //		records the microphoneStream

            if (event.data.size > 0) {
                audioChunks.push(event.data); //				stores pieces of the recorded audio

            }
        });
        currentRecorder.addEventListener("stop", async () => {
            if (sessionFinished) {
                return;
            }

            sessionFinished = true;
            const audioBlob = new Blob(audioChunks, {  //the completed recording made from all audioChunks
                type: currentRecorder.mimeType
            });

            /*
             * Release the microphone and clear the recording state before waiting
             * for the network request. The local audioBlob remains available.
             */
            finishRecordingSession("Uploading audio", "Uploading");
            recordButton.disabled = true;

            try {
                const text = await uploadRecording(audioBlob);

                transcriptText.textContent = text;
                characterCount.textContent =
                    `${text.length} ${text.length === 1 ? "character" : "characters"}`;

                finishRecordingSession("Ready", "Ready");
            } catch (error) {
                console.error("Audio upload failed", error);
                finishRecordingSession("Upload failed", "Error");
            }
        });

        currentRecorder.addEventListener("error", event => {
            if (sessionFinished) {
                return;
            }

            sessionFinished = true;
            console.error("Recording failed", event.error ?? event);
            finishRecordingSession("Recording failed", "Error");
        });

        currentRecorder.start();
        startRecordingTimer();
        setRecordingVisualState(true);
        recordButton.disabled = false;
        recorderStatus.textContent = "Recording";
    } catch (error) {
        console.error("Could not start recording", error);
        finishRecordingSession("Microphone unavailable", "Unavailable");
    }
});


function formatUptime(totalSeconds) {
    const seconds = Math.max(0, Math.floor(totalSeconds));

    if (seconds < 60) {
        return `${seconds}s uptime`;
    }

    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}m ${remainingSeconds}s uptime`;
}

async function checkServerConnection() {
    try {
        const response = await fetch("/api/v1/admin/uptime");

        if (!response.ok) {
            throw new Error(`Server returned HTTP ${response.status}`);
        }

        const uptime = await response.json();
        statusCopy.textContent = `Online · ${formatUptime(uptime.serverUptimeSeconds)}`;
        serverStatus.classList.remove("offline");
        serverStatus.classList.add("online");
    } catch (error) {
        console.error("Could not check the server connection", error);
        statusCopy.textContent = "Server unavailable";
        serverStatus.classList.remove("online");
        serverStatus.classList.add("offline");
    }
}

checkServerConnection();
