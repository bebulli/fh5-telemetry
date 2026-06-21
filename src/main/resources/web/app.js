const POLL_INTERVAL_MS = 300;

function el(id) {
  return document.getElementById(id);
}

async function postForm(path, fields) {
  const body = new URLSearchParams(fields);
  const res = await fetch(path, { method: "POST", body });
  const data = res.status === 204 ? {} : await res.json();
  if (!res.ok) {
    throw new Error(data.error || `Request to ${path} failed`);
  }
  return data;
}

async function getJson(path) {
  const res = await fetch(path);
  if (res.status === 204) {
    return null;
  }
  return res.json();
}

function corner(id, corners, digits) {
  const node = el(id);
  const fmt = (v) => Number(v).toFixed(digits);
  node.children[0].textContent = `FL ${fmt(corners.frontLeft)}`;
  node.children[1].textContent = `FR ${fmt(corners.frontRight)}`;
  node.children[2].textContent = `RL ${fmt(corners.rearLeft)}`;
  node.children[3].textContent = `RR ${fmt(corners.rearRight)}`;
}

async function refreshStatus() {
  const status = await getJson("/api/status");
  el("listenerStatus").textContent = status.listening
    ? `listening on ${status.bindAddress || "0.0.0.0"}:${status.port}`
    : "not listening";
  el("packetsReceived").textContent = status.packetsReceived;
  el("recordingStatus").textContent = status.recording
    ? `recording to ${status.activeRecordingFile}`
    : (status.replaying ? "replaying a session" : "not recording");
}

async function refreshLatest() {
  const t = await getJson("/api/telemetry/latest");
  if (!t) {
    return;
  }
  el("speed").textContent = `${t.speedMph.toFixed(0)} mph`;
  el("rpm").textContent = `${t.currentEngineRpm.toFixed(0)} / ${t.engineMaxRpm.toFixed(0)}`;
  el("gear").textContent = t.gear !== undefined ? t.gear : "-";
  el("drivetrain").textContent = t.drivetrain;
  el("carInfo").textContent = `${t.carOrdinal} / ${t.carClass} / ${t.carPerformanceIndex}`;

  const badge = el("drivingState");
  badge.textContent = t.drivingState;
  badge.className = "badge " + (t.drivingState === "DRIVING" ? "driving" : "static");

  corner("slipRatio", t.tireSlipRatio, 2);
  corner("suspTravel", t.suspensionTravelNormalized, 2);
  if (t.tireTempCelsius) {
    corner("tireTemp", t.tireTempCelsius, 1);
  }
}

async function refreshRecordings() {
  const data = await getJson("/api/recordings");
  const list = el("recordingsList");
  list.innerHTML = "";
  for (const name of data.recordings) {
    const li = document.createElement("li");
    const label = document.createElement("span");
    label.textContent = name;
    const replayBtn = document.createElement("button");
    replayBtn.textContent = "Replay";
    replayBtn.onclick = () => postForm("/api/recordings/replay", { file: name }).catch(showError);
    li.append(label, replayBtn);
    list.append(li);
  }
}

function showError(err) {
  alert(err.message || String(err));
}

function renderTuning(result) {
  const container = el("tuningResult");
  const axleRow = (label, pair, unit) => `
    <div class="axle-row">
      <span class="col-label">${label}</span>
      <span>Front: ${pair.front.toFixed(2)}${unit}</span>
      <span>Rear: ${pair.rear.toFixed(2)}${unit}</span>
    </div>`;

  container.innerHTML = `
    ${axleRow("Tire pressure", result.tirePressurePsi, " psi")}
    ${axleRow("Camber", result.camberDegrees, "&deg;")}
    ${axleRow("Toe", result.toeDegrees, "&deg;")}
    ${axleRow("Anti-roll bar", result.antiRollBarStiffness, "")}
    ${axleRow("Spring rate", result.springRateLbsPerIn, " lbs/in")}
    ${axleRow("Rebound damping", result.reboundDamping, "")}
    ${axleRow("Bump damping", result.bumpDamping, "")}
    <p><strong>Gearing:</strong> ${result.gearing.guidance}</p>
    <ul>${result.notes.map((n) => `<li>${n}</li>`).join("")}</ul>
  `;
}

el("startListenerBtn").onclick = () =>
  postForm("/api/listener", { bindAddress: el("bindAddress").value, port: el("port").value })
    .then(refreshStatus)
    .catch(showError);

el("startRecordingBtn").onclick = () =>
  postForm("/api/recording/start", { name: el("recordingName").value })
    .then(refreshStatus)
    .catch(showError);

el("stopRecordingBtn").onclick = () =>
  postForm("/api/recording/stop", {})
    .then(() => {
      refreshStatus();
      refreshRecordings();
    })
    .catch(showError);

el("resetSampleBtn").onclick = () => postForm("/api/telemetry/reset", {}).catch(showError);

el("tuneBtn").onclick = () =>
  postForm("/api/tuning", {
    weightKg: el("weightKg").value,
    drivetrain: el("drivetrainInput").value,
    powerHp: el("powerHp").value,
    performanceIndex: el("performanceIndex").value,
    style: el("styleInput").value,
  })
    .then(renderTuning)
    .catch((err) => {
      el("tuningResult").innerHTML = `<p class="error-text">${err.message}</p>`;
    });

setInterval(() => {
  refreshStatus().catch(() => {});
  refreshLatest().catch(() => {});
}, POLL_INTERVAL_MS);

refreshStatus().catch(() => {});
refreshRecordings().catch(() => {});
