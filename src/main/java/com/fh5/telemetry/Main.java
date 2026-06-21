package com.fh5.telemetry;

import com.fh5.telemetry.api.ApiServer;
import com.fh5.telemetry.app.TelemetryService;
import com.fh5.telemetry.display.ConsoleDisplay;
import com.fh5.telemetry.display.TuningDisplay;
import com.fh5.telemetry.model.DrivetrainType;
import com.fh5.telemetry.sample.SampleSessionGenerator;
import com.fh5.telemetry.sniff.RawPacketSniffer;
import com.fh5.telemetry.tuning.CarSpec;
import com.fh5.telemetry.tuning.TelemetrySampleAggregator;
import com.fh5.telemetry.tuning.TuningHeuristicsEngine;
import com.fh5.telemetry.tuning.TuningRecommendation;
import com.fh5.telemetry.tuning.TuningStyle;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

public final class Main {

    private static final int DEFAULT_UDP_PORT = 6767;
    private static final int DEFAULT_API_PORT = 7070;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "ui";

        switch (mode) {
            case "sniff" -> RawPacketSniffer.main(new String[0]);
            case "listen" -> runConsoleListener();
            case "sample" -> runSampleDemo();
            default -> runUi();
        }
    }

    private static void runUi() throws Exception {
        TelemetryService service = new TelemetryService(Path.of("recordings"));
        service.startListening("", DEFAULT_UDP_PORT);

        ApiServer api = new ApiServer(DEFAULT_API_PORT, service);
        api.start();

        System.out.println("Listening for FH5 Data Out on UDP " + DEFAULT_UDP_PORT);
        System.out.println("Open http://localhost:" + DEFAULT_API_PORT + " in a browser");

        new CountDownLatch(1).await();
    }

    private static void runConsoleListener() throws Exception {
        TelemetryService service = new TelemetryService(Path.of("recordings"));
        ConsoleDisplay display = new ConsoleDisplay();

        service.startListening("", DEFAULT_UDP_PORT);
        System.out.println("Listening for FH5 Data Out on UDP " + DEFAULT_UDP_PORT);

        while (true) {
            service.latestPacket().ifPresent(display::print);
            Thread.sleep(500);
        }
    }

    private static void runSampleDemo() {
        System.out.println("Running a synthetic driving session (no game connection needed)...");
        ConsoleDisplay display = new ConsoleDisplay();
        TelemetrySampleAggregator aggregator = new TelemetrySampleAggregator();

        new SampleSessionGenerator().generate(200).forEach(packet -> {
            aggregator.add(packet);
        });
        new SampleSessionGenerator().generate(1).forEach(display::print);

        CarSpec spec = new CarSpec(1500f, DrivetrainType.AWD, 550f, 800);
        TuningHeuristicsEngine engine = new TuningHeuristicsEngine();
        TuningDisplay tuningDisplay = new TuningDisplay();
        aggregator.summarize().ifPresent(summary -> {
            System.out.println();
            tuningDisplay.print(engine.recommend(spec, summary, TuningStyle.GRIP));
            System.out.println();
            tuningDisplay.print(engine.recommend(spec, summary, TuningStyle.DRIFT));
        });
    }
}
