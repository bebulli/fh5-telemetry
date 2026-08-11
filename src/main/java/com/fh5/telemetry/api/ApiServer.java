package com.fh5.telemetry.api;

import com.fh5.telemetry.app.RecordingResult;
import com.fh5.telemetry.app.TelemetryService;
import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.model.TelemetryPacket;
import com.fh5.telemetry.tuning.CarSpec;
import com.fh5.telemetry.tuning.DrivingSymptom;
import com.fh5.telemetry.tuning.TuningRecommendation;
import com.fh5.telemetry.tuning.TuningStyle;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * Local HTTP API and static file server for the web UI. Uses only the JDK's
 * built-in HttpServer so the project doesn't need a web framework.
 */
public final class ApiServer {

    private final HttpServer server;
    private final TelemetryService service;

    public ApiServer(int port, TelemetryService service) throws IOException {
        this.service = service;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "api-server");
            t.setDaemon(true);
            return t;
        }));

        server.createContext("/api/status", this::handleStatus);
        server.createContext("/api/listener", this::handleListener);
        server.createContext("/api/telemetry/latest", this::handleLatest);
        server.createContext("/api/telemetry/summary", this::handleSummary);
        server.createContext("/api/telemetry/reset", this::handleReset);
        server.createContext("/api/telemetry/reset-peaks", this::handleResetPeaks);
        server.createContext("/api/recording/start", this::handleRecordingStart);
        server.createContext("/api/recording/stop", this::handleRecordingStop);
        server.createContext("/api/recordings", this::handleRecordings);
        server.createContext("/api/recordings/replay", this::handleReplay);
        server.createContext("/api/recordings/summary", this::handleRecordingSummary);
        server.createContext("/api/recordings/telemetry", this::handleRecordingTelemetry);
        server.createContext("/api/tuning", this::handleTuning);
        server.createContext("/", new StaticFileHandler());
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("listening", service.isListening());
        status.put("bindAddress", service.boundAddress());
        status.put("port", service.boundPort());
        status.put("packetsReceived", service.packetsReceived());
        status.put("recording", service.isRecording());
        status.put("activeRecordingFile", service.activeRecordingFile().orElse(null));
        status.put("replaying", service.isReplaying());
        sendJson(exchange, 200, status);
    }

    private void handleListener(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, error("POST required"));
            return;
        }
        try {
            RequestParams params = RequestParams.from(exchange);
            String bindAddress = params.get("bindAddress", "");
            int port = params.requireInt("port");
            service.stopListening();
            service.startListening(bindAddress, port);
            handleStatus(exchange);
        } catch (Exception e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private void handleLatest(HttpExchange exchange) throws IOException {
        Optional<TelemetryPacket> latest = service.latestPacket();
        if (latest.isEmpty()) {
            sendJson(exchange, 204, Map.of());
            return;
        }
        sendJson(exchange, 200, JsonMappers.telemetryPacket(latest.get()));
    }

    private void handleSummary(HttpExchange exchange) throws IOException {
        var summary = service.sampleSummary();
        if (summary.isEmpty()) {
            sendJson(exchange, 204, Map.of());
            return;
        }
        sendJson(exchange, 200, JsonMappers.sampleSummary(summary.get()));
    }

    private void handleReset(HttpExchange exchange) throws IOException {
        service.resetSample();
        sendJson(exchange, 200, Map.of("reset", true));
    }

    private void handleResetPeaks(HttpExchange exchange) throws IOException {
        boolean reset = service.resetPeaks();
        sendJson(exchange, 200, Map.of("reset", reset));
    }

    private void handleRecordingStart(HttpExchange exchange) throws IOException {
        try {
            RequestParams params = RequestParams.from(exchange);
            String name = params.get("name", "");
            String file = service.startRecording(name);
            sendJson(exchange, 200, Map.of("file", file));
        } catch (Exception e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private void handleRecordingStop(HttpExchange exchange) throws IOException {
        Optional<RecordingResult> result = service.stopRecording();
        if (result.isEmpty()) {
            sendJson(exchange, 400, error("No recording in progress"));
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("file", result.get().file());
        body.put("packetsRecorded", result.get().packetsRecorded());
        sendJson(exchange, 200, body);
    }

    private void handleRecordings(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, Map.of("recordings", service.listRecordings()));
    }

    private void handleRecordingSummary(HttpExchange exchange) throws IOException {
        try {
            RequestParams params = RequestParams.from(exchange);
            String file = params.require("file");
            sendJson(exchange, 200, JsonMappers.recordingSummary(service.readRecordingSummary(file)));
        } catch (Exception e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private void handleRecordingTelemetry(HttpExchange exchange) throws IOException {
        try {
            RequestParams params = RequestParams.from(exchange);
            String file = params.require("file");
            long startMs = params.getLong("startMs", 0);
            long endMs = params.getLong("endMs", Long.MAX_VALUE);

            List<Map<String, Object>> samples = service.readRecordingWindow(file, startMs, endMs).stream()
                    .map(JsonMappers::recordedSample)
                    .toList();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("file", file);
            body.put("samples", samples);
            sendJson(exchange, 200, body);
        } catch (Exception e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private void handleReplay(HttpExchange exchange) throws IOException {
        try {
            RequestParams params = RequestParams.from(exchange);
            String file = params.require("file");
            service.replay(file);
            sendJson(exchange, 200, Map.of("replaying", file));
        } catch (Exception e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private void handleTuning(HttpExchange exchange) throws IOException {
        try {
            RequestParams params = RequestParams.from(exchange);
            CarSpec spec = new CarSpec(
                    params.requireFloat("weightKg"),
                    DrivetrainType.valueOf(params.require("drivetrain").toUpperCase()),
                    params.requireFloat("powerHp"),
                    params.requireInt("performanceIndex"),
                    Float.parseFloat(params.get("frontWeightDistributionPct", "50")));
            TuningStyle style = TuningStyle.valueOf(params.get("style", "GRIP").toUpperCase());
            Set<DrivingSymptom> symptoms = parseSymptoms(params.get("symptoms", ""));

            Optional<TuningRecommendation> recommendation = service.computeTuning(spec, style, symptoms);
            if (recommendation.isEmpty()) {
                sendJson(exchange, 400, error("No driving samples yet. Drive for a few seconds first."));
                return;
            }
            sendJson(exchange, 200, JsonMappers.tuningRecommendation(recommendation.get()));
        } catch (Exception e) {
            sendJson(exchange, 400, error(e.getMessage()));
        }
    }

    private static Set<DrivingSymptom> parseSymptoms(String csv) {
        if (csv.isBlank()) {
            return Set.of();
        }
        Set<DrivingSymptom> symptoms = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            if (!token.isBlank()) {
                symptoms.add(DrivingSymptom.valueOf(token.trim().toUpperCase()));
            }
        }
        return symptoms;
    }

    private static Map<String, Object> error(String message) {
        return Map.of("error", message == null ? "invalid request" : message);
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        if (status == 204) {
            exchange.sendResponseHeaders(204, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
        exchange.close();
    }

    /** Serves the UI's static files (index.html, app.js, style.css) from the classpath. */
    private static final class StaticFileHandler implements com.sun.net.httpserver.HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath().equals("/") ? "/index.html" : uri.getPath();
            String resourcePath = "/web" + path;

            try (InputStream in = StaticFileHandler.class.getResourceAsStream(resourcePath)) {
                if (in == null) {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                    return;
                }
                byte[] bytes = in.readAllBytes();
                exchange.getResponseHeaders().add("Content-Type", contentType(path));
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
            exchange.close();
        }

        private static String contentType(String path) {
            if (path.endsWith(".html")) return "text/html; charset=utf-8";
            if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (path.endsWith(".css")) return "text/css; charset=utf-8";
            return "application/octet-stream";
        }
    }
}
