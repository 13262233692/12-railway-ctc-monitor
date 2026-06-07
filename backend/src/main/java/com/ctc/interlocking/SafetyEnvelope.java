package com.ctc.interlocking;

import java.util.*;

public class SafetyEnvelope {

    private final String trainId;
    private final String routeId;
    private final Set<String> occupiedTracks;
    private final Set<String> approachingTracks;
    private final Set<String> rearClearanceTracks;

    public SafetyEnvelope(String trainId, String routeId,
                          Set<String> occupiedTracks,
                          Set<String> approachingTracks,
                          Set<String> rearClearanceTracks) {
        this.trainId = trainId;
        this.routeId = routeId;
        this.occupiedTracks = occupiedTracks != null ? new HashSet<>(occupiedTracks) : new HashSet<>();
        this.approachingTracks = approachingTracks != null ? new HashSet<>(approachingTracks) : new HashSet<>();
        this.rearClearanceTracks = rearClearanceTracks != null ? new HashSet<>(rearClearanceTracks) : new HashSet<>();
    }

    public Set<String> getFullEnvelope() {
        Set<String> full = new HashSet<>();
        full.addAll(occupiedTracks);
        full.addAll(approachingTracks);
        full.addAll(rearClearanceTracks);
        return full;
    }

    public boolean overlapsWith(Set<String> trackIds) {
        Set<String> envelope = getFullEnvelope();
        for (String id : trackIds) {
            if (envelope.contains(id)) return true;
        }
        return false;
    }

    public String getTrainId() { return trainId; }
    public String getRouteId() { return routeId; }
    public Set<String> getOccupiedTracks() { return Collections.unmodifiableSet(occupiedTracks); }
    public Set<String> getApproachingTracks() { return Collections.unmodifiableSet(approachingTracks); }
    public Set<String> getRearClearanceTracks() { return Collections.unmodifiableSet(rearClearanceTracks); }
}
