package com.ctc.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InterlockingState {

    private Map<String, Boolean> trackCircuits = new HashMap<>();
    private Map<String, String> signals = new HashMap<>();
    private Map<String, Integer> switches = new HashMap<>();
    private Map<String, Boolean> routes = new HashMap<>();
    private List<Map<String, Object>> conflictWarnings = new ArrayList<>();
    private long timestamp = System.currentTimeMillis();

    public Map<String, Boolean> getTrackCircuits() {
        return trackCircuits;
    }

    public void setTrackCircuits(Map<String, Boolean> trackCircuits) {
        this.trackCircuits = trackCircuits;
    }

    public Map<String, String> getSignals() {
        return signals;
    }

    public void setSignals(Map<String, String> signals) {
        this.signals = signals;
    }

    public Map<String, Integer> getSwitches() {
        return switches;
    }

    public void setSwitches(Map<String, Integer> switches) {
        this.switches = switches;
    }

    public Map<String, Boolean> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, Boolean> routes) {
        this.routes = routes;
    }

    public List<Map<String, Object>> getConflictWarnings() {
        return conflictWarnings;
    }

    public void setConflictWarnings(List<Map<String, Object>> conflictWarnings) {
        this.conflictWarnings = conflictWarnings;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
