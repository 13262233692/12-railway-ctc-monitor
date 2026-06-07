package com.ctc.model;

public class SwitchState {

    public enum Position {
        NORMAL, REVERSE
    }

    private String id;
    private String name;
    private Position position;
    private boolean locked;

    public SwitchState() {
    }

    public SwitchState(String id, String name, Position position, boolean locked) {
        this.id = id;
        this.name = name;
        this.position = position;
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

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
