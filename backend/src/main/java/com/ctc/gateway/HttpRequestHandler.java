package com.ctc.gateway;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;

public class HttpRequestHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final String ALLOWED_ORIGINS = "*";
    private static final String CORS_HEADERS = "Content-Type, Authorization";

    private final String websocketPath;

    public HttpRequestHandler(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (isWebSocketRequest(request)) {
            addCorsHeadersForUpgrade(request);
            request.setHeaders(request.headers());
            ctx.fireChannelRead(request.retain());
            return;
        }

        if (HttpMethod.OPTIONS.equals(request.method())) {
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
            setCorsHeaders(response);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        sendNotFound(ctx, request);
    }

    private boolean isWebSocketRequest(FullHttpRequest request) {
        return request.uri().startsWith(websocketPath)
                && request.headers().contains(HttpHeaderNames.UPGRADE)
                && HttpHeaderValues.WEBSOCKET.contentEqualsIgnoreCase(
                        request.headers().get(HttpHeaderNames.UPGRADE));
    }

    private void addCorsHeadersForUpgrade(FullHttpRequest request) {
        String origin = request.headers().get(HttpHeaderNames.ORIGIN);
        if (origin != null) {
            request.headers().set(HttpHeaderNames.ORIGIN, origin);
        }
    }

    private void setCorsHeaders(FullHttpResponse response) {
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGINS);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS");
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, CORS_HEADERS);
        response.headers().set(HttpHeaderNames.ACCESS_CONTROL_MAX_AGE, 86400);
    }

    private void sendNotFound(ChannelHandlerContext ctx, FullHttpRequest request) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND);
        setCorsHeaders(response);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);

        ChannelFuture future = ctx.writeAndFlush(response);
        if (!HttpUtil.isKeepAlive(request)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
