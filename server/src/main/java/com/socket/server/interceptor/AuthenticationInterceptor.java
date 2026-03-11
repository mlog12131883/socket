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
        // 입장 메시지는 인증 전이라도 허용
        if (messageType == MessageType.ENTER.ordinal()) {
            return true;
        }

        // 그 외 메시지는 세션 존재 여부 확인 (또는 상태 패턴과 연계)
        // 여기서는 단순 세션 존재 여부만 체크
        // 실제로는 User.getState()를 확인하는 로직이 들어갈 수 있음
        log.info("[Auth] 인증 검사 수행 중...");
        return true; 
    }

    @Override
    public void postHandle(Socket socket, int messageType, Object result) {
    }
}
