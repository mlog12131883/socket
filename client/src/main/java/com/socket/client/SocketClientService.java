package com.socket.client;

import com.socket.client.domain.ChatMessage;
import com.socket.client.domain.MessageType;
import com.socket.client.serializer.JsonMessageSerializer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class SocketClientService {

    private static final Logger log = LoggerFactory.getLogger(SocketClientService.class);

    @Value("${socket.server.host:127.0.0.1}")
    private String host;

    @Value("${socket.server.port:65432}")
    private int port;

    @Value("${chat.user.id}")
    private String userId;

    private final JsonMessageSerializer<ChatMessage> serializer;
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "MessageReader"));
    private final ExecutorService inputExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "ConsoleInput"));
    
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private volatile boolean connected = false;

    @EventListener(ApplicationReadyEvent.class)
    public void startClient() {
        connect();
    }

    private void connect() {
        try {
            log.info("소켓 서버({}:{})에 연결 시도 중... (내 ID: {})", host, port, userId);
            socket = new Socket(host, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            connected = true;
            log.info("서버에 성공적으로 연결되었습니다!");

            // 1. 서버 메시지 수신 스레드 시작
            startMessageReader();

            // 2. 초기 입장 메시지 전송
            sendInitialEnterMessage();

            // 3. 사용자 입력 대기 루프 시작
            startConsoleInputLoop();

        } catch (IOException e) {
            log.error("서버 연결 실패: {}", e.getMessage());
        }
    }

    private void startMessageReader() {
        readerExecutor.submit(() -> {
            try {
                while (connected) {
                    try {
                        int length = in.readInt();
                        int typeOrdinal = in.readInt();
                        byte[] payload = new byte[length];
                        in.readFully(payload);

                        ChatMessage message = serializer.deserialize(payload, ChatMessage.class);
                        displayMessage(message);
                    } catch (IOException e) {
                        throw e; // 재연결 로직이 없으므로 루프 종료를 위해 던짐
                    } catch (Exception e) {
                        log.error("메시지 수신 처리 중 오류 발생 (무시하고 계속): {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    log.error("서버와 연결이 끊어졌습니다: {}", e.getMessage());
                    connected = false;
                }
            }
        });
    }

    private void startConsoleInputLoop() {
        inputExecutor.submit(() -> {
            java.util.Scanner scanner = new java.util.Scanner(System.in, StandardCharsets.UTF_8);
            System.out.println("========================================");
            System.out.println("채팅 클라이언트 (ID: " + userId + ")");
            System.out.println("메시지를 입력하고 엔터를 누르세요. (종료: /quit)");
            System.out.println("========================================");

            while (connected) {
                String input = scanner.nextLine();
                if ("/quit".equalsIgnoreCase(input)) {
                    disconnect();
                    break;
                }
                
                if (!input.trim().isEmpty()) {
                    sendMessage(new ChatMessage(MessageType.CHAT, "ROOM_1", userId, input));
                }
            }
        });
    }

    private void sendInitialEnterMessage() {
        sendMessage(new ChatMessage(MessageType.ENTER, "ROOM_1", userId, userId + "님이 입장했습니다."));
    }

    private void sendMessage(ChatMessage message) {
        try {
            byte[] payload = serializer.serialize(message);
            synchronized (out) {
                out.writeInt(payload.length);
                out.writeInt(message.getType().ordinal());
                out.write(payload);
                out.flush();
            }
        } catch (IOException e) {
            log.error("메시지 전송 실패: {}", e.getMessage());
        }
    }

    private void displayMessage(ChatMessage message) {
        String time = message.getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"));
        System.out.printf("[%s] %s: %s\n", time, message.getSenderId(), message.getContent());
    }

    private void disconnect() {
        connected = false;
        try {
            sendMessage(new ChatMessage(MessageType.LEAVE, "ROOM_1", userId, "퇴장합니다."));
            if (socket != null) socket.close();
            log.info("연결을 종료했습니다.");
            System.exit(0);
        } catch (IOException e) {
            log.error("종료 중 에러 발생: {}", e.getMessage());
        }
    }
}
