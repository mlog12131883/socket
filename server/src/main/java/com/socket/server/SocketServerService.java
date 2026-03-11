package com.socket.server;

import jakarta.annotation.PreDestroy;
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
            try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {

                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    log.info("수신한 데이터 [{}]: {}", clientSocket.getInetAddress(), inputLine);
                    // 클라이언트로 Echo 응답
                    out.println("서버 응답: " + inputLine);
                }
            } catch (IOException e) {
                log.error("클라이언트 통신 에러: {}", clientSocket.getInetAddress(), e);
            } finally {
                try {
                    clientSocket.close();
                    log.info("클라이언트 연결 종료: {}", clientSocket.getInetAddress());
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
