const POLL_INTERVAL_MS = 300;

const MPH_TO_KMH = 1.60934;
const PSI_TO_BAR = 0.0689476;
const KG_TO_LB = 2.20462;
const NMM_TO_LBIN = 5.71015;
const NMM_TO_KGFMM = 0.101972;
const KGF_TO_LBF = 2.20462;

let currentUnitSystem = localStorage.getItem("unitSystem") || "english";
let lastTuningResult = null;

function el(id) {
  return document.getElementById(id);
}

function isMetric() {
  return currentUnitSystem === "metric";
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

function setIfNotFocused(id, value) {
  const field = el(id);
  if (document.activeElement !== field) {
    field.value = value;
  }
}

function corner(id, corners, digits, convert = (v) => v) {
  const node = el(id);
  const fmt = (v) => convert(v).toFixed(digits);
  node.children[0].textContent = `FL ${fmt(corners.frontLeft)}`;
  node.children[1].textContent = `FR ${fmt(corners.frontRight)}`;
  node.children[2].textContent = `RL ${fmt(corners.rearLeft)}`;
  node.children[3].textContent = `RR ${fmt(corners.rearRight)}`;
}

function formatSpeed(mph) {
  return isMetric() ? `${(mph * MPH_TO_KMH).toFixed(0)} km/h` : `${mph.toFixed(0)} mph`;
}

function formatPressure(psi) {
  return isMetric() ? `${(psi * PSI_TO_BAR).toFixed(2)} bar` : `${psi.toFixed(1)} psi`;
}

function formatSpring(nMm) {
  const unit = el("springUnit").value;
  if (unit === "lbin") {
    return `${(nMm * NMM_TO_LBIN).toFixed(2)} lbs/in`;
  }
  if (unit === "kgfmm") {
    return `${(nMm * NMM_TO_KGFMM).toFixed(3)} kgf/mm`;
  }
  return `${nMm.toFixed(2)} N/mm`;
}

function formatRideHeightLevel(level) {
  return level.toFixed(1);
}

function formatAero(kgf) {
  return isMetric() ? `${kgf.toFixed(1)} kgf` : `${(kgf * KGF_TO_LBF).toFixed(1)} lbf`;
}

function convertGuidanceUnits(text) {
  if (!isMetric()) {
    return text;
  }
  return text.replace(/(\d+(\.\d+)?)\s*mph/g, (match, num) => `${Math.round(parseFloat(num) * MPH_TO_KMH)} km/h`);
}

function weightInKg() {
  const value = parseFloat(el("weightKg").value);
  return isMetric() ? value : value / KG_TO_LB;
}

function updateUnitLabels() {
  el("weightUnitLabel").textContent = isMetric() ? "kg" : "lb";
  el("tempUnitLabel").textContent = isMetric() ? "C" : "F";
}

el("unitSystem").addEventListener("change", () => {
  const newSystem = el("unitSystem").value;
  if (newSystem !== currentUnitSystem) {
    const field = el("weightKg");
    const current = parseFloat(field.value);
    if (!isNaN(current)) {
      field.value = Math.round(newSystem === "metric" ? current / KG_TO_LB : current * KG_TO_LB);
    }
    currentUnitSystem = newSystem;
    localStorage.setItem("unitSystem", currentUnitSystem);
  }
  updateUnitLabels();
  if (lastTuningResult) {
    renderTuning(lastTuningResult);
  }
});

el("springUnit").addEventListener("change", () => {
  if (lastTuningResult) {
    renderTuning(lastTuningResult);
  }
});

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
  el("speed").textContent = formatSpeed(t.speedMph);
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
    corner("tireTemp", t.tireTempCelsius, 1, (c) => (isMetric() ? c : (c * 9) / 5 + 32));
  }

  if (t.carPerformanceIndex > 0) {
    setIfNotFocused("drivetrainInput", t.drivetrain);
    setIfNotFocused("performanceIndex", t.carPerformanceIndex);
  }
}

async function refreshSummary() {
  const summary = await getJson("/api/telemetry/summary");
  if (!summary) {
    el("peakPower").textContent = "-";
    el("maxSpeed").textContent = "-";
    return;
  }

  el("maxSpeed").textContent = formatSpeed(summary.topSpeedMph);

  if (summary.peakPowerHp === undefined) {
    el("peakPower").textContent = "-";
    return;
  }
  el("peakPower").textContent = `${Math.round(summary.peakPowerHp)} hp`;
  setIfNotFocused("powerHp", Math.round(summary.peakPowerHp));
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
  lastTuningResult = result;
  const container = el("tuningResult");
  const axleRow = (label, pair, format) => `
    <div class="axle-row">
      <span class="col-label">${label}</span>
      <span>Front: ${format(pair.front)}</span>
      <span>Rear: ${format(pair.rear)}</span>
    </div>`;
  const degrees = (v) => `${v.toFixed(2)}&deg;`;
  const plain = (v) => v.toFixed(2);
  const pct = (v) => `${v.toFixed(1)}%`;
  const singleRow = (label, value, format) => `
    <div class="axle-row">
      <span class="col-label">${label}</span>
      <span>${format(value)}</span>
    </div>`;

  const awdRows = result.centerDiffRearBiasPct !== undefined
    ? `
      ${singleRow("Rear diff lock, accel", result.rearDiffAccelLockPct, pct)}
      ${singleRow("Rear diff lock, decel", result.rearDiffDecelLockPct, pct)}
      ${singleRow("Center split (% to rear)", result.centerDiffRearBiasPct, pct)}
    `
    : "";

  container.innerHTML = `
    ${axleRow("Tire pressure", result.tirePressurePsi, formatPressure)}
    ${axleRow("Camber", result.camberDegrees, degrees)}
    ${axleRow("Toe", result.toeDegrees, degrees)}
    ${singleRow("Front caster", result.frontCasterDegrees, degrees)}
    ${axleRow("Ride height (0-10)", result.rideHeightLevel, formatRideHeightLevel)}
    ${axleRow("Aero", result.aeroKgf, formatAero)}
    ${singleRow("Brake balance (front)", result.brakeBalanceFrontPct, pct)}
    ${singleRow("Brake pressure", result.brakePressurePct, pct)}
    ${singleRow(result.centerDiffRearBiasPct !== undefined ? "Front diff lock, accel" : "Diff lock, accel", result.diffAccelLockPct, pct)}
    ${singleRow(result.centerDiffRearBiasPct !== undefined ? "Front diff lock, decel" : "Diff lock, decel", result.diffDecelLockPct, pct)}
    ${awdRows}
    ${axleRow("Anti-roll bar", result.antiRollBarStiffness, plain)}
    ${axleRow("Spring rate", result.springRateNmm, formatSpring)}
    ${axleRow("Rebound damping", result.reboundDamping, plain)}
    ${axleRow("Bump damping", result.bumpDamping, plain)}
    <p><strong>Gearing:</strong> ${convertGuidanceUnits(result.gearing.guidance)}</p>
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

el("resetPeaksBtn").onclick = () => postForm("/api/telemetry/reset-peaks", {}).catch(showError);

function checkedSymptoms() {
  return Array.from(document.querySelectorAll(".symptom:checked"))
    .map((el) => el.value)
    .join(",");
}

el("tuneBtn").onclick = () =>
  postForm("/api/tuning", {
    weightKg: weightInKg(),
    drivetrain: el("drivetrainInput").value,
    powerHp: el("powerHp").value,
    performanceIndex: el("performanceIndex").value,
    style: el("styleInput").value,
    symptoms: checkedSymptoms(),
  })
    .then(renderTuning)
    .catch((err) => {
      el("tuningResult").innerHTML = `<p class="error-text">${err.message}</p>`;
    });

setInterval(() => {
  refreshStatus().catch(() => {});
  refreshLatest().catch(() => {});
  refreshSummary().catch(() => {});
}, POLL_INTERVAL_MS);

el("unitSystem").value = currentUnitSystem;
updateUnitLabels();
refreshStatus().catch(() => {});
refreshRecordings().catch(() => {});
