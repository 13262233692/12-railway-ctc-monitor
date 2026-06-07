package com.ctc.parser;

import com.ctc.interlocking.InterlockingConfig;
import com.ctc.interlocking.InterlockingEngine;
import com.ctc.interlocking.RouteDefinition;
import com.ctc.model.InterlockingState;
import com.ctc.model.SignalState.Aspect;
import com.ctc.model.SwitchState.Position;
import com.ctc.model.TrackCircuitState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SimulationDataGenerator {

    private static final Logger LOGGER = Logger.getLogger(SimulationDataGenerator.class.getName());

    private static final String[] TRACK_CIRCUIT_IDS = InterlockingConfig.TRACK_CIRCUIT_IDS.toArray(new String[0]);
    private static final String[] SIGNAL_IDS = InterlockingConfig.SIGNAL_IDS.toArray(new String[0]);
    private static final String[] SWITCH_IDS = InterlockingConfig.SWITCH_IDS.toArray(new String[0]);
    private static final String[] ROUTE_IDS = {"Route1", "Route2", "Route3", "Route4", "Route5", "Route6"};

    private final InterlockingEngine engine;
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger simulationTick = new AtomicInteger(0);

    public SimulationDataGenerator(InterlockingEngine engine) {
        this.engine = engine;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulation-generator");
            t.setDaemon(true);
            return t;
        });
    }

    public byte[] generateBitstream() {
        InterlockingState state = engine.getState();
        byte[] frame = new byte[6];

        int tcWord = 0;
        Map<String, Boolean> tcStates = state.getTrackCircuits();
        for (int i = 0; i < Math.min(TRACK_CIRCUIT_IDS.length, 16); i++) {
            Boolean occupied = tcStates.get(TRACK_CIRCUIT_IDS[i]);
            if (Boolean.TRUE.equals(occupied)) {
                tcWord |= (1 << i);
            }
        }
        frame[0] = (byte) ((tcWord >> 8) & 0xFF);
        frame[1] = (byte) (tcWord & 0xFF);

        int sigWord = 0;
        Map<String, String> sigStates = state.getSignals();
        for (int i = 0; i < Math.min(SIGNAL_IDS.length, 8); i++) {
            String aspect = sigStates.get(SIGNAL_IDS[i]);
            int bits = switch (aspect != null ? aspect : "RED") {
                case "GREEN" -> 0b10;
                case "YELLOW" -> 0b01;
                default -> 0b00;
            };
            sigWord |= (bits << (i * 2));
        }
        frame[2] = (byte) ((sigWord >> 8) & 0xFF);
        frame[3] = (byte) (sigWord & 0xFF);

        int swWord = 0;
        Map<String, Integer> swStates = state.getSwitches();
        for (int i = 0; i < Math.min(SWITCH_IDS.length, 8); i++) {
            Integer pos = swStates.get(SWITCH_IDS[i]);
            if (pos != null && pos == 1) {
                swWord |= (1 << i);
            }
        }
        frame[4] = (byte) (swWord & 0xFF);

        int routeWord = 0;
        Map<String, Boolean> routeStates = state.getRoutes();
        for (int i = 0; i < Math.min(ROUTE_IDS.length, 8); i++) {
            Boolean active = routeStates.get(ROUTE_IDS[i]);
            if (Boolean.TRUE.equals(active)) {
                routeWord |= (1 << i);
            }
        }
        frame[5] = (byte) (routeWord & 0xFF);

        BitstreamParser.computeChecksum(frame);
        return frame;
    }

    public void simulateTrainArrival(String routeId) {
        RouteDefinition def = engine.getRouteDefinitions().get(routeId);
        if (def == null) {
            LOGGER.warning("模拟到达失败: 路线不存在 " + routeId);
            return;
        }

        var result = engine.requestRoute(routeId);
        LOGGER.info("模拟列车到达 - 请求路线 " + routeId + ": " + result.message());

        if (result.success()) {
            engine.evaluate();
        }
    }

    public void simulateTrainDeparture(String routeId) {
        RouteDefinition def = engine.getRouteDefinitions().get(routeId);
        if (def == null) {
            LOGGER.warning("模拟出发失败: 路线不存在 " + routeId);
            return;
        }

        for (String tcId : def.getTrackCircuitPath()) {
            TrackCircuitState tc = engine.getTrackCircuits().get(tcId);
            if (tc != null) {
                tc.setOccupied(false);
            }
        }

        engine.evaluate();

        LOGGER.info("模拟列车出发完成: " + routeId);
    }

    public void simulateOccupancySequence(String routeId) {
        RouteDefinition def = engine.getRouteDefinitions().get(routeId);
        if (def == null) {
            LOGGER.warning("模拟占用序列失败: 路线不存在 " + routeId);
            return;
        }

        List<String> path = def.getTrackCircuitPath();
        scheduler.schedule(() -> {
            try {
                var result = engine.requestRoute(routeId);
                LOGGER.info("占用序列[0] - 请求路线: " + result.message());

                if (result.success()) {
                    for (int i = 0; i < path.size(); i++) {
                        final int step = i;
                        scheduler.schedule(() -> {
                            if (step > 0) {
                                TrackCircuitState prevTc = engine.getTrackCircuits().get(path.get(step - 1));
                                if (prevTc != null) prevTc.setOccupied(false);
                            }
                            TrackCircuitState tc = engine.getTrackCircuits().get(path.get(step));
                            if (tc != null) tc.setOccupied(true);

                            engine.evaluate();
                            LOGGER.info("占用序列[" + (step + 1) + "/" + path.size() + "] - 占用: " + path.get(step));
                        }, (i + 1) * 2L, TimeUnit.SECONDS);
                    }

                    int clearDelay = (path.size() + 1) * 2;
                    scheduler.schedule(() -> {
                        TrackCircuitState lastTc = engine.getTrackCircuits().get(path.get(path.size() - 1));
                        if (lastTc != null) lastTc.setOccupied(false);

                        engine.evaluate();
                        LOGGER.info("占用序列完成 - 路线清空: " + routeId);
                    }, clearDelay, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "占用序列执行异常", e);
            }
        }, 1L, TimeUnit.SECONDS);
    }

    public void startPeriodicGeneration(int intervalMs, java.util.function.Consumer<byte[]> consumer) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                byte[] data = generateBitstream();
                consumer.accept(data);
                simulationTick.incrementAndGet();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "周期生成数据异常", e);
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        LOGGER.info("周期数据生成已启动, 间隔: " + intervalMs + "ms");
    }

    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOGGER.info("模拟数据生成器已停止");
    }

    public int getSimulationTick() {
        return simulationTick.get();
    }
}
