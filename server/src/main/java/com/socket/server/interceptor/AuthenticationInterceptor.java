package com.socket.server.interceptor;

import com.socket.server.domain.MessageType;
import com.socket.server.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Socket;

@Component
public class AuthenticationInterceptor implements ChannelInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationInterceptor.class);
    private final SessionService sessionService;

    public AuthenticationInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(Socket socket, int messageType, byte[] payload) {
        // ENTER 메시지는 인증 이전에도 허용
        if (messageType == MessageType.ENTER.ordinal()) {
            return true;
        }

        // 이외 메시지는 세션 존재 여부 확인 (또는 상태 패턴과 조율)
        // 여기서는 세션 존재 여부만 체크
        // 실무에서는 User.getState() 확인 로직 추가 가능
        log.info("[Auth] Performing authentication check...");
        return true; 
    }

    @Override
    public void postHandle(Socket socket, int messageType, Object result) {
    }
}
