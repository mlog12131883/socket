package com.socket.client;

import com.socket.client.domain.ChatMessage;
import com.socket.client.domain.MessageType;
import com.socket.client.serializer.JsonMessageSerializer;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SocketClientService {

    private static final Logger log = LoggerFactory.getLogger(SocketClientService.class);

    @Value("${socket.server.host:127.0.0.1}")
    private String host;

    @Value("${socket.server.port:65432}")
    private int port;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final JsonMessageSerializer<ChatMessage> serializer;

    public SocketClientService(JsonMessageSerializer<ChatMessage> serializer) {
        this.serializer = serializer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connectToServer() {
        executorService.submit(() -> {
            boolean connected = false;
            int maxRetries = 5;
            int retries = 0;

            // 서버가 뜰 때까지 주기적으로 재시도
            while (!connected && retries < maxRetries) {
                try {
                    log.info("소켓 서버({}:{})에 연결 시도 중... (시도 횟수: {}/{})", host, port, retries + 1, maxRetries);
                    try (Socket socket = new Socket(host, port);
                         DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                         DataInputStream in = new DataInputStream(socket.getInputStream())) {

                        connected = true;
                        log.info("서버에 성공적으로 연결되었습니다!");

                        // 서버로 메시지 객체 전송
                        log.info("서버로 메시지 전송 중...");
                        ChatMessage enterMessage = new ChatMessage(MessageType.ENTER, "ROOM_1", "User1", "안녕하세요! 클라이언트 입장입니다.");
                        byte[] payload = serializer.serialize(enterMessage);
                        
                        out.writeInt(payload.length);
                        out.writeInt(enterMessage.getType().ordinal()); // 메시지 타입 (4 bytes)
                        out.write(payload);
                        out.flush();

                        // 서버로부터의 응답 수신
                        int length = in.readInt();
                        int messageType = in.readInt(); // 메시지 타입 (4 bytes)
                        byte[] responsePayload = new byte[length];
                        in.readFully(responsePayload);
                        
                        ChatMessage responseMessage = serializer.deserialize(responsePayload, ChatMessage.class);
                        log.info("서버로부터 받은 응답: [타입={}] {}", responseMessage.getType(), responseMessage.getContent());

                    }
                } catch (IOException e) {
                    retries++;
                    log.warn("서버 연결 실패. 3초 후 재시도합니다... ({} / {})", retries, maxRetries);
                    try {
                        Thread.sleep(3000); // 3초 대기
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            if (!connected) {
                log.error("소켓 서버 연결에 최종 실패했습니다. (최대 재시도 횟수 초과)");
            }
        });
    }
}
