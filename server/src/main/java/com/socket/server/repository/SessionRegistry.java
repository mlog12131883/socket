package com.socket.server.repository;

import org.springframework.stereotype.Component;
import java.io.DataOutputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접속 중인 사용자의 소켓 출력 스트림을 관리하는 저장소
 * 다중 관리자 간의 브로드캐팅을 위해 메모리 상주형으로 설계
 */
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 접속 중인 사용자의 소켓 출력 스트림 및 소켓-사용자 매핑을 관리하는 저장소
 */
@Component
public class SessionRegistry {
    // Key: UserId, Value: DataOutputStream
    private final Map<String, DataOutputStream> userToOut = new ConcurrentHashMap<>();
    // Key: Socket, Value: UserId (연결 종료 시 식별용)
    private final Map<Socket, String> socketToUser = new ConcurrentHashMap<>();

    public void register(String userId, Socket socket, DataOutputStream out) {
        userToOut.put(userId, out);
        socketToUser.put(socket, userId);
    }

    public void unregister(String userId) {
        userToOut.remove(userId);
        // reverse mapping은 unregisterBySocket에서 처리하는 것이 일반적
    }

    public void unregisterBySocket(Socket socket) {
        String userId = socketToUser.remove(socket);
        if (userId != null) {
            userToOut.remove(userId);
        }
    }

    public Optional<String> getUserId(Socket socket) {
        return Optional.ofNullable(socketToUser.get(socket));
    }

    public Optional<DataOutputStream> getOutputStream(String userId) {
        return Optional.ofNullable(userToOut.get(userId));
    }

    public Map<String, DataOutputStream> getAllSessions() {
        return userToOut;
    }
}
