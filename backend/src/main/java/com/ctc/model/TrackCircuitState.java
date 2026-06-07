package com.ctc.model;

public class TrackCircuitState {

    private String id;
    private String name;
    private boolean occupied;
    private boolean locked;

    public TrackCircuitState() {
    }

    public TrackCircuitState(String id, String name, boolean occupied, boolean locked) {
        this.id = id;
        this.name = name;
        this.occupied = occupied;
        this.locked = locked;
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

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
