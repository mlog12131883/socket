package com.socket.server.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class SessionClosedEvent {
    private final String userId;
}
