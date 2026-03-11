package com.socket.server.controller;

import com.socket.server.annotation.MessageBody;
import com.socket.server.annotation.MessageMapping;
import com.socket.server.annotation.SocketController;
import com.socket.server.domain.ChatMessage;
import com.socket.server.domain.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SocketController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @MessageMapping(MessageType.ENTER)
    public ChatMessage handleEnter(@MessageBody ChatMessage message) {
        log.info("[ChatController] 입장 메시지 처리: 방 [{}], 사용자 [{}]", message.getRoomId(), message.getSenderId());
        message.setContent(message.getSenderId() + "님이 " + message.getRoomId() + " 방에 입장했습니다.");
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
        message.setContent(message.getSenderId() + "님이 " + message.getRoomId() + " 방에서 퇴장했습니다.");
        return message; // 에코 응답용
    }
}
