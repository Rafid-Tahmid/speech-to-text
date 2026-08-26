const serverStatus = document.querySelector("#server-status");
const statusCopy = serverStatus.querySelector(".status-copy");
const recordButton = document.querySelector("#record-button");
const recordingTimer = document.querySelector("#recording-timer");
const recorderStatus = document.querySelector(".recorder-footer p");
const MAX_RECORDING_MS = 5 * 60 * 1000;

let timerInterval;
let recordingStartedAt;
let microphoneStream;
let mediaRecorder;
let audioChunks = [];

if (navigator.mediaDevices?.getUserMedia && "MediaRecorder" in window) { // checks browser-support mic
    recordButton.disabled = false;
    recorderStatus.textContent = "Ready";
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


recordButton.addEventListener("click", async () => {
    if (mediaRecorder?.state === "recording") {
        mediaRecorder.stop();
        return;
    }

    try {
        microphoneStream =
            await navigator.mediaDevices.getUserMedia({ audio: true }); //		the live sound coming from the microphone


        mediaRecorder = new MediaRecorder(microphoneStream);
        audioChunks = [];

        mediaRecorder.addEventListener("dataavailable", event => {  //		records the microphoneStream

            if (event.data.size > 0) {
                audioChunks.push(event.data); //				stores pieces of the recorded audio

            }
        });
        mediaRecorder.addEventListener("stop", () => {
			stopRecordingTimer();
            const audioBlob = new Blob(audioChunks, {  //the completed recording made from all audioChunks
                type: mediaRecorder.mimeType
            });

            console.log("Recorded audio:", audioBlob);

            microphoneStream.getTracks().forEach(track => track.stop());
            recorderStatus.textContent = "Ready";
        });

        mediaRecorder.start();
		startRecordingTimer();
        recorderStatus.textContent = "Recording";
    } catch (error) {
		stopRecordingTimer();
        console.error("Could not start recording", error);
        recorderStatus.textContent = "Microphone unavailable";
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
