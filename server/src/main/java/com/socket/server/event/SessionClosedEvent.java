package com.socket.server.event;

public class SessionClosedEvent {
    private final String userId;

    public SessionClosedEvent(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}
