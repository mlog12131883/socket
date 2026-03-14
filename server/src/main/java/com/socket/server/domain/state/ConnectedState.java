package com.socket.server.domain.state;

import com.socket.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectedState implements SessionState {
    private static final Logger log = LoggerFactory.getLogger(ConnectedState.class);

    @Override
    public void handle(User user, int messageType) {
        log.info("[State] Simple connected state (unauthenticated) - user: {}", user.getNickname());
        // ENTER 메시지에서만 전환이 가능하도록 설계 가능
    }

    @Override
    public String getStateName() {
        return "CONNECTED";
    }
}
