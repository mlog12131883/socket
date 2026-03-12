package com.socket.server.repository;

import org.springframework.stereotype.Component;
import java.io.DataOutputStream;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository that manages socket output streams and socket-user mappings for connected users
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
     * Register output stream during socket connection (prior to user ID verification)
     */
    public void addSocket(Socket socket, DataOutputStream out) {
        socketToOut.put(socket, out);
    }

    /**
     * Register mapping between socket and user ID after user authentication (ENTER)
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
     * Manual registration (for testing or exceptional situations)
     */
    public void register(String userId, Socket socket, DataOutputStream out) {
        socketToOut.put(socket, out);
        userToOut.put(userId, out);
        socketToUser.put(socket, userId);
    }

    public void unregister(String userId) {
        userToOut.remove(userId);
        // Find and remove reverse mapping as well (performance optimization possible)
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
