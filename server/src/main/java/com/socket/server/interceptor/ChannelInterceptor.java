package com.socket.server.interceptor;

import java.net.Socket;

public interface ChannelInterceptor {
    /**
     * 메시지 처리 전 호출.
     * @return true이면 다음 인터셉터 또는 디스패처로 진행, false이면 중단.
     */
    boolean preHandle(Socket socket, int messageType, byte[] payload);

    /**
     * 메시지 처리 후 호출.
     */
    void postHandle(Socket socket, int messageType, Object result);
}
