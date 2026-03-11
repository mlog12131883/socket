package com.socket.server.domain;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 채팅 메시지 도메인 클래스
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    private MessageType type;
    private String roomId;
    private String senderId;
    private String content;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public ChatMessage(MessageType type, String roomId, String senderId, String content) {
        this.type = type;
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }
}
