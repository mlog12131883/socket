package com.socket.server.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Component
public class SecurityInterceptor implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);
    private static final int MAX_PAYLOAD_SIZE = 1024 * 10; // 10KB limit

    @Override
    public boolean preHandle(Socket socket, int messageType, byte[] payload) {
        // 1. Verify payload size
        if (payload.length > MAX_PAYLOAD_SIZE) {
            log.warn("[Security] Payload size exceeded: {} bytes, Client: {}", payload.length, socket.getInetAddress());
            return false;
        }

        // 2. IP verification (Example: can be extended to blacklists or allowing only localhost)
        // log.info("[Security] Security check passed: {}", socket.getInetAddress());
        return true;
    }

    @Override
    public void postHandle(Socket socket, int messageType, Object result) {
        // Implement post-processing logic if needed
    }
}
