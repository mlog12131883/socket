package com.socket.server.domain.state;

import com.socket.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisconnectedState implements SessionState {
    private static final Logger log = LoggerFactory.getLogger(DisconnectedState.class);

    @Override
    public void handle(User user, int messageType) {
        log.info("[State] 연결 종료 상태 - 사용자: {}", user.getNickname());
    }

    @Override
    public String getStateName() {
        return "DISCONNECTED";
    }
}
