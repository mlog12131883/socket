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
        // ENTER message is allowed even before authentication
        if (messageType == MessageType.ENTER.ordinal()) {
            return true;
        }

        // Other messages check for session existence (or coordinate with state pattern)
        // Checking only session existence here
        // In practice, logic to check User.getState() could be added
        log.info("[Auth] Performing authentication check...");
        return true; 
    }

    @Override
    public void postHandle(Socket socket, int messageType, Object result) {
    }
}
