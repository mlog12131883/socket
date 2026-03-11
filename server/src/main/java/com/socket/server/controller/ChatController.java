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

    @MessageMapping(MessageType.ENTER)
    public ChatMessage handleEnter(@MessageBody ChatMessage message, Socket clientSocket) {
        log.info("[ChatController] 입장 메시지 처리: 방 [{}], 사용자 [{}]", message.getRoomId(), message.getSenderId());
        
        // 1. 사용자 객체 생성 및 세션 등록
        User user = new User(message.getSenderId(), message.getSenderId());
        sessionService.registerSession(message.getSenderId(), user);
        
        // SessionRegistry에 소켓-사용자 매핑 등록 (이미 등록된 out 스트림 사용)
        sessionRegistry.register(message.getSenderId(), clientSocket);

        // 2. 채팅방 조회 또는 생성
        ChatRoom room = roomService.getRoom(message.getRoomId())
                .orElseGet(() -> roomService.createRoom(message.getRoomId(), "New Room " + message.getRoomId()));

        // 3. 채팅방 입장
        roomService.joinRoom(message.getRoomId(), user);

        // 4. 상태 전이 (Connected -> Authenticated)
        user.setState(new com.socket.server.domain.state.AuthenticatedState());
        log.info("[ChatController] 사용자 [{}] 상태 변경: {}", user.getId(), user.getState().getStateName());

        // 5. 브로드캐스팅: 입장 알림 및 유저 목록
        message.setContent(message.getSenderId() + "님이 입장했습니다.");
        roomService.broadcast(message.getRoomId(), message);

        return message; 
    }

    @MessageMapping(MessageType.CHAT)
    public ChatMessage handleChat(@MessageBody ChatMessage message) {
        log.info("[ChatController] 일반 채팅 메시지 처리: 방 [{}], 사용자 [{}], 내용 [{}]", 
                message.getRoomId(), message.getSenderId(), message.getContent());
        
        // 브로드캐스팅: 전체 메시지 전파
        roomService.broadcast(message.getRoomId(), message);
        
        return message;
    }

    @MessageMapping(MessageType.LEAVE)
    public ChatMessage handleLeave(@MessageBody ChatMessage message) {
        log.info("[ChatController] 퇴장 메시지 처리: 방 [{}], 사용자 [{}]", message.getRoomId(), message.getSenderId());
        
        sessionService.getSession(message.getSenderId()).ifPresent(user -> {
            roomService.leaveRoom(message.getRoomId(), user);
            sessionService.removeSession(message.getSenderId());
            sessionRegistry.unregister(message.getSenderId());
        });

        message.setContent(message.getSenderId() + "님이 퇴장했습니다.");
        roomService.broadcast(message.getRoomId(), message);

        return message;
    }
}
