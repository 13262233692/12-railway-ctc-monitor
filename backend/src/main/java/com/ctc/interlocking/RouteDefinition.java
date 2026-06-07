package com.ctc.interlocking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RouteDefinition {

    private String id;
    private String name;
    private String entrySignal;
    private List<String> trackCircuitPath = new ArrayList<>();
    private Map<String, Integer> switchRequirements = new HashMap<>();
    private Set<String> conflictingRoutes = new HashSet<>();

    public RouteDefinition() {
    }

    public RouteDefinition(String id, String name, String entrySignal,
                           List<String> trackCircuitPath,
                           Map<String, Integer> switchRequirements,
                           Set<String> conflictingRoutes) {
        this.id = id;
        this.name = name;
        this.entrySignal = entrySignal;
        this.trackCircuitPath = trackCircuitPath != null ? new ArrayList<>(trackCircuitPath) : new ArrayList<>();
        this.switchRequirements = switchRequirements != null ? new HashMap<>(switchRequirements) : new HashMap<>();
        this.conflictingRoutes = conflictingRoutes != null ? new HashSet<>(conflictingRoutes) : new HashSet<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEntrySignal() {
        return entrySignal;
    }

    public void setEntrySignal(String entrySignal) {
        this.entrySignal = entrySignal;
    }

    public List<String> getTrackCircuitPath() {
        return Collections.unmodifiableList(trackCircuitPath);
    }

    public void setTrackCircuitPath(List<String> trackCircuitPath) {
        this.trackCircuitPath = trackCircuitPath != null ? new ArrayList<>(trackCircuitPath) : new ArrayList<>();
    }

    public Map<String, Integer> getSwitchRequirements() {
        return Collections.unmodifiableMap(switchRequirements);
    }

    public void setSwitchRequirements(Map<String, Integer> switchRequirements) {
        this.switchRequirements = switchRequirements != null ? new HashMap<>(switchRequirements) : new HashMap<>();
    }

    public Set<String> getConflictingRoutes() {
        return Collections.unmodifiableSet(conflictingRoutes);
    }

    public void setConflictingRoutes(Set<String> conflictingRoutes) {
        this.conflictingRoutes = conflictingRoutes != null ? new HashSet<>(conflictingRoutes) : new HashSet<>();
    }

    @Override
    public String toString() {
        return "RouteDefinition{id='" + id + "', name='" + name + "', entrySignal='" + entrySignal +
                "', trackCircuitPath=" + trackCircuitPath +
                ", switchRequirements=" + switchRequirements +
                ", conflictingRoutes=" + conflictingRoutes + "}";
    }
}
