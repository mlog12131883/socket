package com.socket.server.controller;

import com.socket.server.annotation.MessageBody;
import com.socket.server.annotation.MessageMapping;
import com.socket.server.annotation.SocketController;
import com.socket.server.domain.ChatMessage;
import com.socket.server.domain.MessageType;
import com.socket.server.domain.User;
import com.socket.server.domain.ChatRoom;
import com.socket.server.repository.SessionRegistry;
import com.socket.server.service.SessionService;
import com.socket.server.service.RoomService;
import com.socket.server.service.ChatMessageWriteBehindService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

@SocketController
@RequiredArgsConstructor
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final SessionService sessionService;
    private final RoomService roomService;
    private final SessionRegistry sessionRegistry;
    private final ChatMessageWriteBehindService chatMessageWriteBehindService;

    @MessageMapping(MessageType.ENTER)
    public void handleEnter(@MessageBody ChatMessage message, Socket clientSocket) {
        log.info("[ChatController] Processing enter message: room [{}], user [{}]", message.getRoomId(), message.getSenderId());
        
        // 1. Create user object and register session
        User user = new User(message.getSenderId(), message.getSenderId());
        sessionService.registerSession(message.getSenderId(), user);
        
        // Register socket-user mapping in SessionRegistry (use existing out stream)
        sessionRegistry.register(message.getSenderId(), clientSocket);

        // 2. Fetch or create chat room
        // RoomService.getRoom()이 ChatRoom을 직접 리턴하므로 Optional.ofNullable()로 감쌉니다.
        ChatRoom room = java.util.Optional.ofNullable(roomService.getRoom(message.getRoomId()))
                .orElseGet(() -> roomService.createRoom(message.getRoomId(), "New Room " + message.getRoomId()));

        // 3. Enter chat room
        roomService.joinRoom(message.getRoomId(), user);

        // 4. State transition (Connected -> Authenticated)
        user.setState(new com.socket.server.domain.state.AuthenticatedState());
        log.info("[ChatController] User [{}] state changed: {}", user.getId(), user.getState().getStateName());

        // 5. Broadcasting: Enter notification and user list
        message.setContent(message.getSenderId() + " has entered.");
        roomService.broadcast(message.getRoomId(), message);
    }

    @MessageMapping(MessageType.CHAT)
    public void handleChat(@MessageBody ChatMessage message) {
        log.info("[ChatController] Processing chat message: room [{}], user [{}], content [{}]", 
                message.getRoomId(), message.getSenderId(), message.getContent());

        // 1) 실시간 브로드캐스트
        roomService.broadcast(message.getRoomId(), message);

        // 2) 비동기 Write-Behind 큐 적재
        chatMessageWriteBehindService.enqueue(message);
    }

    @MessageMapping(MessageType.LEAVE)
    public void handleLeave(@MessageBody ChatMessage message) {
        log.info("[ChatController] Processing leave message: room [{}], user [{}]", message.getRoomId(), message.getSenderId());
        
        java.util.Optional.ofNullable(sessionService.getSession(message.getSenderId())).ifPresent(user -> {
            roomService.leaveRoom(message.getRoomId(), user);
            sessionService.removeSession(message.getSenderId());
            sessionRegistry.unregister(message.getSenderId());
        });

        message.setContent(message.getSenderId() + " has left.");
        roomService.broadcast(message.getRoomId(), message);
    }
}
