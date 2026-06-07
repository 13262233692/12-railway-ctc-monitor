package com.ctc;

import com.ctc.broadcast.StateBroadcaster;
import com.ctc.gateway.CtcServer;
import com.ctc.interlocking.InterlockingConfig;
import com.ctc.interlocking.InterlockingEngine;
import com.ctc.parser.SimulationDataGenerator;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App {

    private static final int DEFAULT_PORT = 8080;
    private static final String[] ROUTE_IDS = {"Route1", "Route2", "Route3", "Route4", "Route5", "Route6"};
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default: " + DEFAULT_PORT);
            }
        }

        System.out.println("Starting Railway CTC Monitor Server...");

        InterlockingConfig config = new InterlockingConfig();
        InterlockingEngine engine = config.buildEngine();
        SimulationDataGenerator simulator = new SimulationDataGenerator(engine);

        StateBroadcaster broadcaster = StateBroadcaster.getInstance();
        broadcaster.setEngine(engine);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ctc-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                String routeId = ROUTE_IDS[RANDOM.nextInt(ROUTE_IDS.length)];
                simulator.simulateOccupancySequence(routeId);
            } catch (Exception e) {
                System.err.println("Simulation event error: " + e.getMessage());
            }
        }, 5, 10, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                broadcaster.broadcast();
            } catch (Exception e) {
                System.err.println("Broadcast error: " + e.getMessage());
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down Railway CTC Monitor...");
            scheduler.shutdown();
            simulator.stop();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            System.out.println("Shutdown complete.");
        }));

        try {
            new CtcServer(port).start();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Server interrupted: " + e.getMessage());
        }
    }
}
