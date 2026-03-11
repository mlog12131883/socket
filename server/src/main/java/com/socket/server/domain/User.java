package com.socket.server.domain;

import com.socket.server.domain.state.ConnectedState;
import com.socket.server.domain.state.SessionState;
import lombok.*;

/**
 * 사용자 도메인 클래스 (세션 역할 겸임)
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
public class User {
    @EqualsAndHashCode.Include
    private final String id;
    private String nickname;
    private SessionState state = new ConnectedState(); // 초기 상태: 연결됨(미인증)

    public User(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
        this.state = new ConnectedState(); 
    }

    // 의미 있는 비즈니스 로직을 통한 상태 변경 (캡슐화 원칙)
    public void disconnect() {
        // DisconnectedState로 전이 로직 등을 추가할 수 있음
    }

    public void connect() {
    }

    public void updateNickname(String newNickname) {
        if (newNickname != null && !newNickname.isBlank()) {
            this.nickname = newNickname;
        }
    }
}
