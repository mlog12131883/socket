package com.socket.server.repository;

import org.springframework.stereotype.Component;
import java.io.DataOutputStream;
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
    
    // Key: Socket, Value: DataOutputStream (동일 소켓에 대해 하나의 스트림 인스턴스 보장)
    private final Map<Socket, DataOutputStream> socketToOut = new ConcurrentHashMap<>();

    /**
     * 소켓 연결 시 출력 스트림을 먼저 등록 (유저 ID 확인 전)
     */
    public void addSocket(Socket socket, DataOutputStream out) {
        socketToOut.put(socket, out);
    }

    /**
     * 유저 인증(ENTER) 후 소켓과 유저 ID 매핑 등록
     */
    public void register(String userId, Socket socket) {
        DataOutputStream out = socketToOut.get(socket);
        if (out != null) {
            userToOut.put(userId, out);
            socketToUser.put(socket, userId);
        } else {
            throw new IllegalStateException("Socket must be added to SessionRegistry before registration: " + socket);
        }
    }

    /**
     * 수동 등록 (테스트 또는 예외 상황용)
     */
    public void register(String userId, Socket socket, DataOutputStream out) {
        socketToOut.put(socket, out);
        userToOut.put(userId, out);
        socketToUser.put(socket, userId);
    }

    public void unregister(String userId) {
        userToOut.remove(userId);
        // reverse mapping도 찾아서 제거 (성능 최적화 가능)
        socketToUser.entrySet().removeIf(entry -> entry.getValue().equals(userId));
    }

    public void unregisterBySocket(Socket socket) {
        socketToOut.remove(socket);
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
