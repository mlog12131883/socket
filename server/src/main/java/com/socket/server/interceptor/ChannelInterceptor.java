package com.socket.server.interceptor;

import java.net.Socket;

public interface ChannelInterceptor {
    /**
     * Called before message processing.
     * @return true to proceed to next interceptor or dispatcher, false to abort.
     */
    boolean preHandle(Socket socket, int messageType, byte[] payload);

    /**
     * Called after message processing.
     */
    void postHandle(Socket socket, int messageType, Object result);
}
