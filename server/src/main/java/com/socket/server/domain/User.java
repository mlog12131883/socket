package com.socket.server.domain;

import java.util.Objects;

/**
 * 사용자 도메인 클래스
 * 특정 인프라에 종속되지 않는 순수 자바 객체(POJO)로 설계
 */
public class User {
    private final String id;
    private String nickname;
    private boolean isConnected;

    public User(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
        this.isConnected = true;
    }

    // 의미 있는 비즈니스 로직을 통한 상태 변경 (캡슐화 원칙)
    public void disconnect() {
        this.isConnected = false;
    }

    public void connect() {
        this.isConnected = true;
    }

    public void updateNickname(String newNickname) {
        if (newNickname != null && !newNickname.isBlank()) {
            this.nickname = newNickname;
        }
    }

    // Getter
    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public boolean isConnected() { return isConnected; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
