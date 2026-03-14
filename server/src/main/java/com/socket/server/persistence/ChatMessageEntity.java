package com.socket.server.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 100)
    private String roomId;

    @Column(name = "sender_id", nullable = false, length = 100)
    private String senderId;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static ChatMessageEntity of(String roomId, String senderId, String content, LocalDateTime createdAt) {
        ChatMessageEntity e = new ChatMessageEntity();
        e.roomId = roomId;
        e.senderId = senderId;
        e.content = content;
        e.createdAt = createdAt;
        return e;
    }
}

