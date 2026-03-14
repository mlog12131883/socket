package com.socket.server.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Component
public class SecurityInterceptor implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);
    private static final int MAX_PAYLOAD_SIZE = 1024 * 10; // 10KB 제한

    @Override
    public boolean preHandle(Socket socket, int messageType, byte[] payload) {
        // 1. 페이로드 크기 검증
        if (payload.length > MAX_PAYLOAD_SIZE) {
            log.warn("[Security] Payload size exceeded: {} bytes, Client: {}", payload.length, socket.getInetAddress());
            return false;
        }

        // 2. IP 검증 (예: 블랙리스트 또는 localhost만 허용하도록 확장 가능)
        // log.info("[Security] Security check passed: {}", socket.getInetAddress());
        return true;
    }

    @Override
    public void postHandle(Socket socket, int messageType, Object result) {
        // 필요 시 후처리 로직 구현
    }
}
