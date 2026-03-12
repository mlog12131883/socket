package com.socket.server.domain.state;

import com.socket.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectedState implements SessionState {
    private static final Logger log = LoggerFactory.getLogger(ConnectedState.class);

    @Override
    public void handle(User user, int messageType) {
        log.info("[State] Simple connected state (unauthenticated) - user: {}", user.getNickname());
        // Can be designed so that transition is only possible on ENTER message
    }

    @Override
    public String getStateName() {
        return "CONNECTED";
    }
}
