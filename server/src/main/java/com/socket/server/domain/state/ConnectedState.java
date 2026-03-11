package com.socket.server.domain.state;

import com.socket.server.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectedState implements SessionState {
    private static final Logger log = LoggerFactory.getLogger(ConnectedState.class);

    @Override
    public void handle(User user, int messageType) {
        log.info("[State] 단순 연결 상태 (미인증) - 사용자: {}", user.getNickname());
        // 입장(ENTER) 메시지 시에만 다음 상태로 전이 가능하도록 설계 가능
    }

    @Override
    public String getStateName() {
        return "CONNECTED";
    }
}
