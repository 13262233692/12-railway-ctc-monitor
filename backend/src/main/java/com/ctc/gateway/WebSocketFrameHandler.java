package com.ctc.gateway;

import com.ctc.broadcast.StateBroadcaster;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            StateBroadcaster.getInstance().addChannel(ctx.channel());
            System.out.println("WebSocket client connected: " + ctx.channel().remoteAddress());

            TextWebSocketFrame initFrame = new TextWebSocketFrame(
                    StateBroadcaster.getInstance().getCurrentStateJson());
            ctx.channel().writeAndFlush(initFrame);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String message = frame.text();
        try {
            JsonElement element = JsonParser.parseString(message);
            if (element.isJsonObject()) {
                JsonObject json = element.getAsJsonObject();
                String type = json.has("type") ? json.get("type").getAsString() : "unknown";
                System.out.println("Received message type: " + type + " from " + ctx.channel().remoteAddress());
                StateBroadcaster.getInstance().handleClientMessage(json);
            }
        } catch (Exception e) {
            System.err.println("Failed to parse message: " + message + ", error: " + e.getMessage());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        StateBroadcaster.getInstance().removeChannel(ctx.channel());
        System.out.println("WebSocket client disconnected: " + ctx.channel().remoteAddress());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
