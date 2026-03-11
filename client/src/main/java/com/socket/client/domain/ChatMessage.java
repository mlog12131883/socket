package com.socket.client.domain;

import java.time.LocalDateTime;

/**
 * 채팅 메시지 도메인 클래스 (클라이언트용)
 */
public class ChatMessage {
    private MessageType type;
    private String roomId;
    private String senderId;
    private String content;
    private LocalDateTime timestamp;

    public ChatMessage() {
        // 기본 생성자 (Jackson 용)
    }

    // Setters (Jackson 역직렬화를 위해 필요)
    public void setType(MessageType type) { this.type = type; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    public void setContent(String content) { this.content = content; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public ChatMessage(MessageType type, String roomId, String senderId, String content) {
        this.type = type;
        this.roomId = roomId;
        this.senderId = senderId;
        this.content = content;
        this.timestamp = LocalDateTime.now();
    }

    public MessageType getType() { return type; }
    public String getRoomId() { return roomId; }
    public String getSenderId() { return senderId; }
    public String getContent() { return content; }
    public LocalDateTime getTimestamp() { return timestamp; }
}
