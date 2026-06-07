package com.ctc.interlocking;

import com.ctc.model.TrackCircuitState;
import com.ctc.model.SwitchState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class ConflictPredictionEngine {

    private static final Logger LOGGER = Logger.getLogger(ConflictPredictionEngine.class.getName());
    private static final int SAFETY_ENVELOPE_DEPTH = 3;
    private static final int DFS_MAX_DEPTH = 10;

    private final TrackTopologyGraph topology;
    private final ConcurrentHashMap<String, TrackCircuitState> trackCircuits;
    private final ConcurrentHashMap<String, SwitchState> switches;
    private final ConcurrentHashMap<String, RouteDefinition> routeDefinitions;
    private final ConcurrentHashMap<String, Boolean> activeRoutes;
    private final ConcurrentHashMap<String, Boolean> approachLockedRoutes;

    private final List<ConflictResult.ConflictPoint> activeConflicts = new ArrayList<>();
    private volatile ConflictResult lastConflictResult = ConflictResult.noConflict();

    public ConflictPredictionEngine(TrackTopologyGraph topology,
                                     ConcurrentHashMap<String, TrackCircuitState> trackCircuits,
                                     ConcurrentHashMap<String, SwitchState> switches,
                                     ConcurrentHashMap<String, RouteDefinition> routeDefinitions,
                                     ConcurrentHashMap<String, Boolean> activeRoutes,
                                     ConcurrentHashMap<String, Boolean> approachLockedRoutes) {
        this.topology = topology;
        this.trackCircuits = trackCircuits;
        this.switches = switches;
        this.routeDefinitions = routeDefinitions;
        this.activeRoutes = activeRoutes;
        this.approachLockedRoutes = approachLockedRoutes;
    }

    public ConflictResult predictConflicts(String requestedRouteId) {
        long startTime = System.nanoTime();

        RouteDefinition requestedRoute = routeDefinitions.get(requestedRouteId);
        if (requestedRoute == null) {
            return ConflictResult.noConflict();
        }

        List<String> requestedPath = requestedRoute.getTrackCircuitPath();
        Set<String> requestedPathSet = new HashSet<>(requestedPath);

        List<ConflictResult.ConflictPoint> conflicts = new ArrayList<>();

        conflicts.addAll(checkSafetyEnvelopeOverlaps(requestedRouteId, requestedRoute, requestedPathSet));
        conflicts.addAll(checkOpposingRoutes(requestedRouteId, requestedRoute, requestedPathSet));
        conflicts.addAll(checkApproachLockedConflicts(requestedRouteId, requestedRoute, requestedPathSet));
        conflicts.addAll(checkSwitchPositionConflicts(requestedRouteId, requestedRoute, requestedPathSet));

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;

        ConflictResult result = new ConflictResult(
            !conflicts.isEmpty(),
            conflicts,
            requestedRouteId,
            elapsedMs
        );

        if (result.hasConflict()) {
            LOGGER.warning(String.format("冲突预判熔断! 进路: %s, 冲突点: %d, 耗时: %dms",
                requestedRouteId, conflicts.size(), elapsedMs));
            activeConflicts.clear();
            activeConflicts.addAll(conflicts);
        } else {
            activeConflicts.clear();
        }

        lastConflictResult = result;
        return result;
    }

    private List<ConflictResult.ConflictPoint> checkSafetyEnvelopeOverlaps(
            String routeId, RouteDefinition route, Set<String> requestedPathSet) {

        List<ConflictResult.ConflictPoint> conflicts = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : activeRoutes.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            if (entry.getKey().equals(routeId)) continue;

            RouteDefinition activeRouteDef = routeDefinitions.get(entry.getKey());
            if (activeRouteDef == null) continue;

            Set<String> occupiedOnRoute = new HashSet<>();
            for (String tcId : activeRouteDef.getTrackCircuitPath()) {
                TrackCircuitState tc = trackCircuits.get(tcId);
                if (tc != null && tc.isOccupied()) {
                    occupiedOnRoute.add(tcId);
                }
            }

            if (occupiedOnRoute.isEmpty()) continue;

            SafetyEnvelope envelope = buildSafetyEnvelope(
                "train_" + entry.getKey(), entry.getKey(), occupiedOnRoute);

            Set<String> overlapTracks = new HashSet<>(requestedPathSet);
            overlapTracks.retainAll(envelope.getFullEnvelope());

            if (!overlapTracks.isEmpty()) {
                for (String overlapTc : overlapTracks) {
                    double[] coords = topology.getCoordinates(overlapTc);
                    double x = coords != null ? coords[0] : 0;
                    double y = coords != null ? coords[1] : 0;

                    conflicts.add(new ConflictResult.ConflictPoint(
                        overlapTc,
                        ConflictResult.ConflictType.SAFETY_ENVELOPE_OVERLAP,
                        String.format("与列车 %s 的安全包络线重叠 (进路: %s)",
                            "train_" + entry.getKey(), entry.getKey()),
                        x, y, entry.getKey()
                    ));
                }
            }
        }

        return conflicts;
    }

    private SafetyEnvelope buildSafetyEnvelope(String trainId, String routeId, Set<String> occupiedTracks) {
        Set<String> approachingTracks = new HashSet<>();
        Set<String> rearClearanceTracks = new HashSet<>();

        for (String occupiedTc : occupiedTracks) {
            List<String> forwardReachable = topology.dfsFrom(occupiedTc, SAFETY_ENVELOPE_DEPTH);
            for (int i = 1; i < forwardReachable.size() && i <= SAFETY_ENVELOPE_DEPTH; i++) {
                approachingTracks.add(forwardReachable.get(i));
            }

            List<String> backwardReachable = topology.dfsFrom(occupiedTc, 1);
            for (int i = 1; i < backwardReachable.size() && i <= 1; i++) {
                rearClearanceTracks.add(backwardReachable.get(i));
            }
        }

        return new SafetyEnvelope(trainId, routeId, occupiedTracks, approachingTracks, rearClearanceTracks);
    }

    private List<ConflictResult.ConflictPoint> checkOpposingRoutes(
            String routeId, RouteDefinition route, Set<String> requestedPathSet) {

        List<ConflictResult.ConflictPoint> conflicts = new ArrayList<>();

        for (String conflictingId : route.getConflictingRoutes()) {
            if (!Boolean.TRUE.equals(activeRoutes.get(conflictingId))) continue;

            RouteDefinition conflictDef = routeDefinitions.get(conflictingId);
            if (conflictDef == null) continue;

            Set<String> conflictPath = new HashSet<>(conflictDef.getTrackCircuitPath());
            conflictPath.retainAll(requestedPathSet);

            if (!conflictPath.isEmpty()) {
                for (String tcId : conflictPath) {
                    double[] coords = topology.getCoordinates(tcId);
                    double x = coords != null ? coords[0] : 0;
                    double y = coords != null ? coords[1] : 0;

                    conflicts.add(new ConflictResult.ConflictPoint(
                        tcId,
                        ConflictResult.ConflictType.OPPOSING_ROUTE_ACTIVE,
                        String.format("敌对进路 %s 已激活", conflictingId),
                        x, y, conflictingId
                    ));
                }
            } else {
                for (String tcId : requestedPathSet) {
                    double[] coords = topology.getCoordinates(tcId);
                    double x = coords != null ? coords[0] : 0;
                    double y = coords != null ? coords[1] : 0;

                    conflicts.add(new ConflictResult.ConflictPoint(
                        tcId,
                        ConflictResult.ConflictType.OPPOSING_ROUTE_ACTIVE,
                        String.format("敌对进路 %s 已激活 (道岔冲突)", conflictingId),
                        x, y, conflictingId
                    ));
                    break;
                }
            }
        }

        return conflicts;
    }

    private List<ConflictResult.ConflictPoint> checkApproachLockedConflicts(
            String routeId, RouteDefinition route, Set<String> requestedPathSet) {

        List<ConflictResult.ConflictPoint> conflicts = new ArrayList<>();

        for (Map.Entry<String, Boolean> entry : approachLockedRoutes.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) continue;
            if (entry.getKey().equals(routeId)) continue;

            RouteDefinition lockedRoute = routeDefinitions.get(entry.getKey());
            if (lockedRoute == null) continue;

            Set<String> lockedPath = new HashSet<>(lockedRoute.getTrackCircuitPath());
            lockedPath.retainAll(requestedPathSet);

            for (String tcId : lockedPath) {
                double[] coords = topology.getCoordinates(tcId);
                double x = coords != null ? coords[0] : 0;
                double y = coords != null ? coords[1] : 0;

                conflicts.add(new ConflictResult.ConflictPoint(
                    tcId,
                    ConflictResult.ConflictType.APPROACH_LOCKED_CONFLICT,
                    String.format("进路 %s 接近锁闭中", entry.getKey()),
                    x, y, entry.getKey()
                ));
            }
        }

        return conflicts;
    }

    private List<ConflictResult.ConflictPoint> checkSwitchPositionConflicts(
            String routeId, RouteDefinition route, Set<String> requestedPathSet) {

        List<ConflictResult.ConflictPoint> conflicts = new ArrayList<>();

        for (Map.Entry<String, Integer> req : route.getSwitchRequirements().entrySet()) {
            SwitchState sw = switches.get(req.getKey());
            if (sw == null) continue;

            SwitchState.Position required = req.getValue() == 0 ?
                SwitchState.Position.NORMAL : SwitchState.Position.REVERSE;

            if (sw.getPosition() != required && sw.isLocked()) {
                for (String tcId : requestedPathSet) {
                    List<String> reachable = topology.dfsFrom(tcId, DFS_MAX_DEPTH);
                    if (reachable.contains("switch_" + req.getKey()) ||
                        isTrackConnectedToSwitch(tcId, req.getKey())) {
                        double[] coords = topology.getCoordinates(tcId);
                        double x = coords != null ? coords[0] : 0;
                        double y = coords != null ? coords[1] : 0;

                        conflicts.add(new ConflictResult.ConflictPoint(
                            tcId,
                            ConflictResult.ConflictType.SWITCH_POSITION_CONFLICT,
                            String.format("道岔 %s 位置冲突 (需要: %s, 当前: %s, 已锁闭)",
                                req.getKey(), required, sw.getPosition()),
                            x, y, null
                        ));
                        break;
                    }
                }
            }
        }

        return conflicts;
    }

    private boolean isTrackConnectedToSwitch(String trackId, String switchId) {
        String switchNum = switchId.replace("#", "");
        return trackId.contains(switchNum + "DG") || trackId.contains(switchNum + "G");
    }

    public List<ConflictResult.ConflictPoint> getActiveConflicts() {
        return new ArrayList<>(activeConflicts);
    }

    public ConflictResult getLastConflictResult() {
        return lastConflictResult;
    }

    public void clearConflicts() {
        activeConflicts.clear();
        lastConflictResult = ConflictResult.noConflict();
    }
}
