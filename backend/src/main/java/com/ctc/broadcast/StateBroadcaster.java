package com.ctc.broadcast;

import com.ctc.interlocking.ConflictResult;
import com.ctc.interlocking.InterlockingEngine;
import com.ctc.interlocking.RouteResult;
import com.ctc.model.InterlockingState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

public class StateBroadcaster {

    private static volatile StateBroadcaster instance;

    private final ChannelGroup channels = new DefaultChannelGroup("ctc-channels", GlobalEventExecutor.INSTANCE);
    private final Gson gson = new Gson();
    private volatile InterlockingEngine engine;

    private StateBroadcaster() {
    }

    public static StateBroadcaster getInstance() {
        if (instance == null) {
            synchronized (StateBroadcaster.class) {
                if (instance == null) {
                    instance = new StateBroadcaster();
                }
            }
        }
        return instance;
    }

    public void setEngine(InterlockingEngine engine) {
        this.engine = engine;
    }

    public void addChannel(Channel channel) {
        channels.add(channel);
    }

    public void removeChannel(Channel channel) {
        channels.remove(channel);
    }

    public void broadcast() {
        if (engine == null) {
            return;
        }
        InterlockingState state = engine.getState();
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "state_update");
        wrapper.add("data", gson.toJsonTree(state));
        String json = gson.toJson(wrapper);
        channels.writeAndFlush(new TextWebSocketFrame(json));
    }

    public String getCurrentStateJson() {
        if (engine == null) {
            return "{}";
        }
        InterlockingState state = engine.getState();
        JsonObject wrapper = new JsonObject();
        wrapper.addProperty("type", "state_update");
        wrapper.add("data", gson.toJsonTree(state));
        return gson.toJson(wrapper);
    }

    public void handleClientMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";
        switch (type) {
            case "REQUEST_STATE" -> broadcast();
            case "request_route" -> handleRequestRoute(message);
            case "cancel_route" -> handleCancelRoute(message);
            default -> System.out.println("Unknown message type: " + type);
        }
    }

    private void handleRequestRoute(JsonObject message) {
        if (engine == null || !message.has("route_id")) {
            return;
        }
        String routeId = message.get("route_id").getAsString();
        RouteResult result = engine.requestRoute(routeId);
        System.out.println("Route request " + routeId + ": " + result.message());
        broadcast();

        if (!result.success() && !engine.getActiveConflicts().isEmpty()) {
            JsonObject conflictMsg = new JsonObject();
            conflictMsg.addProperty("type", "conflict_warning");
            conflictMsg.add("conflicts", gson.toJsonTree(engine.getActiveConflicts()));
            channels.writeAndFlush(new TextWebSocketFrame(gson.toJson(conflictMsg)));
        }
    }

    private void handleCancelRoute(JsonObject message) {
        if (engine == null || !message.has("route_id")) {
            return;
        }
        String routeId = message.get("route_id").getAsString();
        RouteResult result = engine.cancelRoute(routeId);
        System.out.println("Route cancel " + routeId + ": " + result.message());
        broadcast();
    }
}
