package com.socket.server;

import com.socket.server.domain.ChatMessage;
import com.socket.server.serializer.JsonMessageSerializer;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SocketServerService {

    private static final Logger log = LoggerFactory.getLogger(SocketServerService.class);

    @Value("${socket.server.port:65432}")
    private int port;

    private ServerSocket serverSocket;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private boolean isRunning = false;

    private final JsonMessageSerializer<ChatMessage> serializer;

    public SocketServerService(JsonMessageSerializer<ChatMessage> serializer) {
        this.serializer = serializer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startServer() {
        executorService.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                log.info("소켓 서버가 포트 {}에서 대기 중입니다...", port);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        handleClient(clientSocket);
                    } catch (IOException e) {
                        if (isRunning) {
                            log.error("클라이언트 연결 수락 중 오류 발생", e);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("서버 소켓 생성 오류", e);
            }
        });
    }

    private void handleClient(Socket clientSocket) {
        executorService.submit(() -> {
            log.info("클라이언트 접속: {}", clientSocket.getInetAddress());
            try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

                while (true) {
                    // 1. 헤더(Payload 길이) 읽기 (4 bytes)
                    int length = in.readInt();
                    
                    // 2. Body 데이터 읽기
                    byte[] payload = new byte[length];
                    in.readFully(payload);

                    // 3. JSON 역직렬화
                    ChatMessage message = serializer.deserialize(payload, ChatMessage.class);
                    log.info("수신한 메시지 [{}]: 타입={}, 룸={}, 보낸사람={}, 내용={}", 
                        clientSocket.getInetAddress(), message.getType(), message.getRoomId(), message.getSenderId(), message.getContent());

                    // 에코 응답
                    byte[] responsePayload = serializer.serialize(message);
                    out.writeInt(responsePayload.length);
                    out.write(responsePayload);
                    out.flush();
                }
            } catch (java.io.EOFException e) {
                log.info("클라이언트 연결 종료: {}", clientSocket.getInetAddress());
            } catch (Exception e) {
                log.error("클라이언트 연결 끊김 또는 통신 에러: {}", clientSocket.getInetAddress(), e);
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    log.warn("소켓 닫기 실패", e);
                }
            }
        });
    }

    @PreDestroy
    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                log.info("소켓 서버가 종료되었습니다.");
            }
        } catch (IOException e) {
            log.error("서버 종료 중 오류 발생", e);
        }
        executorService.shutdown();
    }
}
