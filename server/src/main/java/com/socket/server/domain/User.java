package com.socket.server.domain;

import com.socket.server.domain.state.ConnectedState;
import com.socket.server.domain.state.SessionState;
import lombok.*;

/**
 * User domain class (also serves as session)
 */
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
public class User {
    @EqualsAndHashCode.Include
    private final String id;
    private String nickname;
    private SessionState state = new ConnectedState(); // Initial state: Connected (Unauthenticated)

    public User(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
        this.state = new ConnectedState(); 
    }

    // State change through meaningful business logic (encapsulation principle)
    public void disconnect() {
        // Transition logic to DisconnectedState, etc. can be added
    }

    public void connect() {
    }

    public void updateNickname(String newNickname) {
        if (newNickname != null && !newNickname.isBlank()) {
            this.nickname = newNickname;
        }
    }
}
