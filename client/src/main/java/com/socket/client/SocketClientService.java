package com.socket.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
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
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                        connected = true;
                        log.info("서버에 성공적으로 연결되었습니다!");

                        // 서버로 메시지 전송
                        log.info("서버로 메시지 전송 중...");
                        out.println("안녕하세요! 스프링 부트 ClientService에서 보내는 연결 인사입니다.");

                        // 서버로부터의 응답 수신
                        String response = in.readLine();
                        log.info("서버로부터 받은 응답: {}", response);

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
