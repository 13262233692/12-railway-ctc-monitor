package com.ctc.model;

public class SignalState {

    public enum Aspect {
        RED, YELLOW, GREEN
    }

    private String id;
    private String name;
    private Aspect aspect;
    private boolean locked;

    public SignalState() {
    }

    public SignalState(String id, String name, Aspect aspect, boolean locked) {
        this.id = id;
        this.name = name;
        this.aspect = aspect;
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

    public Aspect getAspect() {
        return aspect;
    }

    public void setAspect(Aspect aspect) {
        this.aspect = aspect;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }
}
