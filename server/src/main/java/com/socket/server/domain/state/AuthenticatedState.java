package com.socket.server.domain.state;

import com.socket.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthenticatedState implements SessionState {
    private static final Logger log = LoggerFactory.getLogger(AuthenticatedState.class);

    @Override
    public void handle(User user, int messageType) {
        log.info("[State] Authentication completed state (normal communication possible) - user: {}", user.getNickname());
    }

    @Override
    public String getStateName() {
        return "AUTHENTICATED";
    }
}
