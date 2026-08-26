const serverStatus = document.querySelector("#server-status");
const statusCopy = serverStatus.querySelector(".status-copy");
const recordButton = document.querySelector("#record-button");

if (navigator.mediaDevices?.getUserMedia && "MediaRecorder" in window) { // If the browser can access  microphone and record audio, enable the button.
    recordButton.disabled = false;
}


const recorderStatus = document.querySelector(".recorder-footer p");

let microphoneStream;
let mediaRecorder;
let audioChunks = [];

if (navigator.mediaDevices?.getUserMedia && "MediaRecorder" in window) {
    recordButton.disabled = false;
    recorderStatus.textContent = "Ready";
}

recordButton.addEventListener("click", async () => {
    if (mediaRecorder?.state === "recording") {
        mediaRecorder.stop();
        return;
    }

    try {
        microphoneStream =
            await navigator.mediaDevices.getUserMedia({ audio: true });

        mediaRecorder = new MediaRecorder(microphoneStream);
        audioChunks = [];

        mediaRecorder.addEventListener("dataavailable", event => {
            if (event.data.size > 0) {
                audioChunks.push(event.data);
            }
        });

        mediaRecorder.addEventListener("stop", () => {
            const audioBlob = new Blob(audioChunks, {
                type: mediaRecorder.mimeType
            });

            console.log("Recorded audio:", audioBlob);

            microphoneStream.getTracks().forEach(track => track.stop());
            recorderStatus.textContent = "Ready";
        });

        mediaRecorder.start();
        recorderStatus.textContent = "Recording";
    } catch (error) {
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
