package com.ctc.interlocking;

import java.util.*;

public class ConflictResult {

    public enum ConflictType {
        SAFETY_ENVELOPE_OVERLAP,
        OPPOSING_ROUTE_ACTIVE,
        APPROACH_LOCKED_CONFLICT,
        SWITCH_POSITION_CONFLICT
    }

    private final boolean hasConflict;
    private final List<ConflictPoint> conflictPoints;
    private final String rejectedRouteId;
    private final long predictionTimeMs;

    public ConflictResult(boolean hasConflict, List<ConflictPoint> conflictPoints,
                          String rejectedRouteId, long predictionTimeMs) {
        this.hasConflict = hasConflict;
        this.conflictPoints = conflictPoints != null ? new ArrayList<>(conflictPoints) : new ArrayList<>();
        this.rejectedRouteId = rejectedRouteId;
        this.predictionTimeMs = predictionTimeMs;
    }

    public static ConflictResult noConflict() {
        return new ConflictResult(false, List.of(), null, 0);
    }

    public boolean hasConflict() { return hasConflict; }
    public List<ConflictPoint> getConflictPoints() { return Collections.unmodifiableList(conflictPoints); }
    public String getRejectedRouteId() { return rejectedRouteId; }
    public long getPredictionTimeMs() { return predictionTimeMs; }

    public static class ConflictPoint {
        private final String trackId;
        private final ConflictType type;
        private final String description;
        private final double x;
        private final double y;
        private final String conflictingRouteId;

        public ConflictPoint(String trackId, ConflictType type, String description,
                             double x, double y, String conflictingRouteId) {
            this.trackId = trackId;
            this.type = type;
            this.description = description;
            this.x = x;
            this.y = y;
            this.conflictingRouteId = conflictingRouteId;
        }

        public String getTrackId() { return trackId; }
        public ConflictType getType() { return type; }
        public String getDescription() { return description; }
        public double getX() { return x; }
        public double getY() { return y; }
        public String getConflictingRouteId() { return conflictingRouteId; }
    }
}
