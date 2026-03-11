package com.socket.server.controller;

import com.socket.server.annotation.MessageBody;
import com.socket.server.annotation.MessageMapping;
import com.socket.server.annotation.SocketController;
import com.socket.server.domain.ChatMessage;
import com.socket.server.domain.MessageType;
import com.socket.server.domain.User;
import com.socket.server.domain.ChatRoom;
import com.socket.server.service.SessionService;
import com.socket.server.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SocketController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final SessionService sessionService;
    private final RoomService roomService;

    public ChatController(SessionService sessionService, 
                          RoomService roomService) {
        this.sessionService = sessionService;
        this.roomService = roomService;
    }

    @MessageMapping(MessageType.ENTER)
    public ChatMessage handleEnter(@MessageBody ChatMessage message) {
        log.info("[ChatController] 입장 메시지 처리: 방 [{}], 사용자 [{}]", message.getRoomId(), message.getSenderId());
        
        // 1. 사용자 객체 생성 및 세션 등록
        User user = new User(message.getSenderId(), message.getSenderId());
        sessionService.registerSession(message.getSenderId(), user); // senderId를 sessionId 대용으로 사용

        // 2. 채팅방 조회 또는 생성
        ChatRoom room = roomService.getRoom(message.getRoomId())
                .orElseGet(() -> roomService.createRoom(message.getRoomId(), "New Room " + message.getRoomId()));

        // 3. 채팅방 입장
        roomService.joinRoom(message.getRoomId(), user);

        message.setContent(message.getSenderId() + "님이 " + message.getRoomId() + " 방에 입장했습니다. (현재 접속자: " + room.getActiveUsers().size() + "명)");
        return message; // 에코 응답용
    }

    @MessageMapping(MessageType.CHAT)
    public ChatMessage handleChat(@MessageBody ChatMessage message) {
        log.info("[ChatController] 일반 채팅 메시지 처리: 방 [{}], 사용자 [{}], 내용 [{}]", 
                message.getRoomId(), message.getSenderId(), message.getContent());
        // 추가 로직...
        return message; // 에코 응답용
    }

    @MessageMapping(MessageType.LEAVE)
    public ChatMessage handleLeave(@MessageBody ChatMessage message) {
        log.info("[ChatController] 퇴장 메시지 처리: 방 [{}], 사용자 [{}]", message.getRoomId(), message.getSenderId());
        
        sessionService.getSession(message.getSenderId()).ifPresent(user -> {
            roomService.leaveRoom(message.getRoomId(), user);
            sessionService.removeSession(message.getSenderId());
        });

        message.setContent(message.getSenderId() + "님이 " + message.getRoomId() + " 방에서 퇴장했습니다.");
        return message; // 에코 응답용
    }
}
