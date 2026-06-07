package com.ctc.interlocking;

import com.ctc.model.InterlockingState;
import com.ctc.model.SignalState;
import com.ctc.model.SignalState.Aspect;
import com.ctc.model.SwitchState;
import com.ctc.model.SwitchState.Position;
import com.ctc.model.TrackCircuitState;
import com.ctc.parser.BitstreamParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InterlockingEngine {

    private static final Logger LOGGER = Logger.getLogger(InterlockingEngine.class.getName());

    private final ConcurrentHashMap<String, TrackCircuitState> trackCircuits = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SignalState> signals = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SwitchState> switches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RouteDefinition> routeDefinitions = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, Boolean> activeRoutes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> approachLockedRoutes = new ConcurrentHashMap<>();
    private final LinkedList<String> routeQueue = new LinkedList<>();

    private final ReentrantLock evaluationLock = new ReentrantLock();

    private final BitstreamParser parser = new BitstreamParser();

    public void registerTrackCircuit(TrackCircuitState tc) {
        trackCircuits.put(tc.getId(), tc);
    }

    public void registerSignal(SignalState signal) {
        signals.put(signal.getId(), signal);
    }

    public void registerSwitch(SwitchState sw) {
        switches.put(sw.getId(), sw);
    }

    public void registerRoute(RouteDefinition route) {
        routeDefinitions.put(route.getId(), route);
        activeRoutes.put(route.getId(), false);
    }

    public RouteResult requestRoute(String routeId) {
        evaluationLock.lock();
        try {
            RouteDefinition def = routeDefinitions.get(routeId);
            if (def == null) {
                return new RouteResult(false, "路线不存在: " + routeId);
            }

            if (Boolean.TRUE.equals(activeRoutes.get(routeId))) {
                return new RouteResult(false, "路线已激活: " + routeId);
            }

            RouteValidation validation = validateRoute(def);
            if (!validation.valid()) {
                if (hasConflictingActiveRoute(def)) {
                    routeQueue.add(routeId);
                    LOGGER.info("路线排队等待: " + routeId + " - 原因: " + validation.reason());
                    return new RouteResult(false, "路线已加入队列: " + validation.reason());
                }
                return new RouteResult(false, validation.reason());
            }

            activateRoute(def);
            return new RouteResult(true, "路线设置成功: " + routeId);
        } finally {
            evaluationLock.unlock();
        }
    }

    public RouteResult cancelRoute(String routeId) {
        evaluationLock.lock();
        try {
            RouteDefinition def = routeDefinitions.get(routeId);
            if (def == null) {
                return new RouteResult(false, "路线不存在: " + routeId);
            }

            if (Boolean.TRUE.equals(approachLockedRoutes.get(routeId))) {
                return new RouteResult(false, "路线进路锁闭中，无法取消: " + routeId);
            }

            if (!Boolean.TRUE.equals(activeRoutes.get(routeId))) {
                return new RouteResult(false, "路线未激活: " + routeId);
            }

            deactivateRoute(def);
            processRouteQueue();
            return new RouteResult(true, "路线已取消: " + routeId);
        } finally {
            evaluationLock.unlock();
        }
    }

    public void processBitstream(byte[] data) {
        Map<String, Object> parsed = parser.parse(data);
        if (!Boolean.TRUE.equals(parsed.get("valid"))) {
            LOGGER.warning("比特流校验失败，丢弃数据");
            return;
        }

        evaluationLock.lock();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Boolean> tcStates = (Map<String, Boolean>) parsed.get("trackCircuits");
            if (tcStates != null) {
                tcStates.forEach((id, occupied) -> {
                    TrackCircuitState tc = trackCircuits.get(id);
                    if (tc != null) {
                        tc.setOccupied(occupied);
                    }
                });
            }

            @SuppressWarnings("unchecked")
            Map<String, Integer> swStates = (Map<String, Integer>) parsed.get("switches");
            if (swStates != null) {
                swStates.forEach((id, pos) -> {
                    SwitchState sw = switches.get(id);
                    if (sw != null) {
                        sw.setPosition(pos == 0 ? Position.NORMAL : Position.REVERSE);
                    }
                });
            }

            evaluate();
        } finally {
            evaluationLock.unlock();
        }
    }

    public void evaluate() {
        evaluationLock.lock();
        try {
            evaluateApproachLocking();
            evaluateRouteAutoRelease();
            evaluateSignalAspects();
            processRouteQueue();
        } finally {
            evaluationLock.unlock();
        }
    }

    public InterlockingState getState() {
        InterlockingState state = new InterlockingState();

        Map<String, Boolean> tcMap = new LinkedHashMap<>();
        trackCircuits.forEach((id, tc) -> tcMap.put(id, tc.isOccupied()));
        state.setTrackCircuits(tcMap);

        Map<String, String> sigMap = new LinkedHashMap<>();
        signals.forEach((id, sig) -> sigMap.put(id, sig.getAspect().name()));
        state.setSignals(sigMap);

        Map<String, Integer> swMap = new LinkedHashMap<>();
        switches.forEach((id, sw) -> swMap.put(id, sw.getPosition() == Position.NORMAL ? 0 : 1));
        state.setSwitches(swMap);

        Map<String, Boolean> routeMap = new LinkedHashMap<>();
        activeRoutes.forEach((id, active) -> routeMap.put(id, active));
        state.setRoutes(routeMap);

        state.setTimestamp(System.currentTimeMillis());
        return state;
    }

    public ConcurrentHashMap<String, TrackCircuitState> getTrackCircuits() {
        return trackCircuits;
    }

    public ConcurrentHashMap<String, SignalState> getSignals() {
        return signals;
    }

    public ConcurrentHashMap<String, SwitchState> getSwitches() {
        return switches;
    }

    public ConcurrentHashMap<String, RouteDefinition> getRouteDefinitions() {
        return routeDefinitions;
    }

    public ConcurrentHashMap<String, Boolean> getActiveRoutes() {
        return activeRoutes;
    }

    public ConcurrentHashMap<String, Boolean> getApproachLockedRoutes() {
        return approachLockedRoutes;
    }

    private RouteValidation validateRoute(RouteDefinition def) {
        for (String tcId : def.getTrackCircuitPath()) {
            TrackCircuitState tc = trackCircuits.get(tcId);
            if (tc == null) {
                return new RouteValidation(false, "轨道电路不存在: " + tcId);
            }
            if (tc.isOccupied()) {
                return new RouteValidation(false, "轨道电路被占用: " + tcId);
            }
        }

        for (Map.Entry<String, Integer> req : def.getSwitchRequirements().entrySet()) {
            SwitchState sw = switches.get(req.getKey());
            if (sw == null) {
                return new RouteValidation(false, "道岔不存在: " + req.getKey());
            }
            Position required = req.getValue() == 0 ? Position.NORMAL : Position.REVERSE;
            if (sw.getPosition() != required) {
                return new RouteValidation(false, "道岔位置不正确: " + req.getKey() +
                        " (需要: " + required + ", 当前: " + sw.getPosition() + ")");
            }
        }

        for (String conflictId : def.getConflictingRoutes()) {
            if (Boolean.TRUE.equals(activeRoutes.get(conflictId))) {
                return new RouteValidation(false, "敌对路线已激活: " + conflictId);
            }
        }

        return new RouteValidation(true, "验证通过");
    }

    private boolean hasConflictingActiveRoute(RouteDefinition def) {
        for (String conflictId : def.getConflictingRoutes()) {
            if (Boolean.TRUE.equals(activeRoutes.get(conflictId))) {
                return true;
            }
        }
        return false;
    }

    private void activateRoute(RouteDefinition def) {
        activeRoutes.put(def.getId(), true);
        approachLockedRoutes.put(def.getId(), false);

        for (String tcId : def.getTrackCircuitPath()) {
            TrackCircuitState tc = trackCircuits.get(tcId);
            if (tc != null) {
                tc.setLocked(true);
            }
        }

        for (Map.Entry<String, Integer> req : def.getSwitchRequirements().entrySet()) {
            SwitchState sw = switches.get(req.getKey());
            if (sw != null) {
                sw.setLocked(true);
            }
        }

        SignalState entrySignal = signals.get(def.getEntrySignal());
        if (entrySignal != null) {
            entrySignal.setLocked(true);
        }

        LOGGER.info("路线已激活: " + def.getId() + " (" + def.getName() + ")");
    }

    private void deactivateRoute(RouteDefinition def) {
        activeRoutes.put(def.getId(), false);
        approachLockedRoutes.remove(def.getId());

        for (String tcId : def.getTrackCircuitPath()) {
            TrackCircuitState tc = trackCircuits.get(tcId);
            if (tc != null) {
                tc.setLocked(false);
            }
        }

        for (Map.Entry<String, Integer> req : def.getSwitchRequirements().entrySet()) {
            SwitchState sw = switches.get(req.getKey());
            if (sw != null) {
                sw.setLocked(false);
            }
        }

        SignalState entrySignal = signals.get(def.getEntrySignal());
        if (entrySignal != null) {
            entrySignal.setAspect(Aspect.RED);
            entrySignal.setLocked(false);
        }

        LOGGER.info("路线已释放: " + def.getId() + " (" + def.getName() + ")");
    }

    private void evaluateApproachLocking() {
        activeRoutes.forEach((routeId, active) -> {
            if (!Boolean.TRUE.equals(active)) return;
            if (Boolean.TRUE.equals(approachLockedRoutes.get(routeId))) return;

            RouteDefinition def = routeDefinitions.get(routeId);
            if (def == null) return;

            List<String> path = def.getTrackCircuitPath();
            if (path.isEmpty()) return;

            String firstTc = path.get(0);
            TrackCircuitState tc = trackCircuits.get(firstTc);
            if (tc != null && tc.isOccupied()) {
                approachLockedRoutes.put(routeId, true);
                LOGGER.info("进路锁闭: " + routeId + " - 列车已接近");
            }
        });
    }

    private void evaluateRouteAutoRelease() {
        List<String> toRelease = new ArrayList<>();

        activeRoutes.forEach((routeId, active) -> {
            if (!Boolean.TRUE.equals(active)) return;

            RouteDefinition def = routeDefinitions.get(routeId);
            if (def == null) return;

            boolean allClear = true;
            for (String tcId : def.getTrackCircuitPath()) {
                TrackCircuitState tc = trackCircuits.get(tcId);
                if (tc != null && tc.isOccupied()) {
                    allClear = false;
                    break;
                }
            }

            if (allClear && Boolean.TRUE.equals(approachLockedRoutes.get(routeId))) {
                boolean trainHasPassed = true;
                for (String tcId : def.getTrackCircuitPath()) {
                    TrackCircuitState tc = trackCircuits.get(tcId);
                    if (tc != null && tc.isOccupied()) {
                        trainHasPassed = false;
                        break;
                    }
                }

                if (trainHasPassed) {
                    toRelease.add(routeId);
                }
            }
        });

        for (String routeId : toRelease) {
            RouteDefinition def = routeDefinitions.get(routeId);
            if (def != null) {
                deactivateRoute(def);
                LOGGER.info("列车通过后自动释放路线: " + routeId);
            }
        }
    }

    private void evaluateSignalAspects() {
        signals.forEach((sigId, signal) -> {
            if (signal.isLocked()) {
                RouteDefinition owningRoute = findRouteByEntrySignal(sigId);
                if (owningRoute != null && Boolean.TRUE.equals(activeRoutes.get(owningRoute.getId()))) {
                    signal.setAspect(computeSignalAspect(owningRoute));
                } else {
                    signal.setAspect(Aspect.RED);
                    signal.setLocked(false);
                }
            } else {
                signal.setAspect(Aspect.RED);
            }
        });
    }

    private Aspect computeSignalAspect(RouteDefinition route) {
        List<String> path = route.getTrackCircuitPath();

        for (String tcId : path) {
            TrackCircuitState tc = trackCircuits.get(tcId);
            if (tc != null && tc.isOccupied()) {
                return Aspect.YELLOW;
            }
        }

        String exitSignalId = findExitSignalForRoute(route);
        if (exitSignalId != null) {
            SignalState exitSignal = signals.get(exitSignalId);
            if (exitSignal != null && exitSignal.getAspect() == Aspect.RED) {
                return Aspect.YELLOW;
            }
        }

        return Aspect.GREEN;
    }

    private RouteDefinition findRouteByEntrySignal(String signalId) {
        for (RouteDefinition def : routeDefinitions.values()) {
            if (signalId.equals(def.getEntrySignal())) {
                return def;
            }
        }
        return null;
    }

    private String findExitSignalForRoute(RouteDefinition route) {
        String routeId = route.getId();
        if (routeId.contains("1") && !routeId.contains("10")) {
            if (signals.containsKey("S1")) return "S1";
            if (signals.containsKey("S")) return "S";
        }
        if (routeId.contains("2")) {
            if (signals.containsKey("S2")) return "S2";
            if (signals.containsKey("S")) return "S";
        }
        if (routeId.contains("3")) {
            if (signals.containsKey("S3")) return "S3";
            if (signals.containsKey("S")) return "S";
        }
        for (String sigId : signals.keySet()) {
            if (sigId.startsWith("S") && !sigId.equals(route.getEntrySignal())) {
                return sigId;
            }
        }
        return null;
    }

    private void processRouteQueue() {
        List<String> processed = new ArrayList<>();

        for (String routeId : routeQueue) {
            RouteDefinition def = routeDefinitions.get(routeId);
            if (def == null) {
                processed.add(routeId);
                continue;
            }

            if (Boolean.TRUE.equals(activeRoutes.get(routeId))) {
                processed.add(routeId);
                continue;
            }

            RouteValidation validation = validateRoute(def);
            if (validation.valid()) {
                activateRoute(def);
                processed.add(routeId);
                LOGGER.info("排队路线自动激活: " + routeId);
            }
        }

        processed.forEach(routeQueue::remove);
    }

    public record RouteResult(boolean success, String message) {
    }

    private record RouteValidation(boolean valid, String reason) {
    }
}
