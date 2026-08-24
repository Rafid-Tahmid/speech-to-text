const serverStatus = document.querySelector("#server-status");
const statusCopy = serverStatus.querySelector(".status-copy");

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
