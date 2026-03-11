package com.socket.server.domain.state;

import com.socket.server.domain.User;

public interface SessionState {
    void handle(User user, int messageType);
    String getStateName();
}
