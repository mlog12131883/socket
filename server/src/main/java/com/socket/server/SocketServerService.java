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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.socket.server.event.EventBus;
import com.socket.server.event.SessionClosedEvent;
import com.socket.server.repository.SessionRegistry;

@Service
@lombok.RequiredArgsConstructor
public class SocketServerService {

    private static final Logger log = LoggerFactory.getLogger(SocketServerService.class);

    private static final int CORE_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 50;
    private static final int QUEUE_CAPACITY = 100;
    private static final long KEEP_ALIVE_TIME_SECONDS = 60L;

    @Value("${socket.server.port:65432}")
    private int port;

    private ServerSocket serverSocket;
    private final ExecutorService bossExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "Boss-Acceptor"));
    private ThreadPoolExecutor workerExecutor;
    private volatile boolean isRunning = false;

    private final JsonMessageSerializer<ChatMessage> serializer;
    private final com.socket.server.dispatcher.SocketDispatcher dispatcher;
    private final SessionRegistry sessionRegistry;

    @jakarta.annotation.PostConstruct
    public void init() {
        // 커스텀 ThreadPoolExecutor (Worker) 생성
        this.workerExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_TIME_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new NamedThreadFactory("Worker-Processor"),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startServer() {
        bossExecutor.submit(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                log.info("Socket server is listening on port {}... (WAS-style thread pool applied)", port);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("Client connection accepted: {}", clientSocket.getInetAddress());
                        
                        // 실제 처리는 워커 스레드 풀에 위임 (논블로킹 Accept)
                        try {
                            workerExecutor.submit(() -> handleClient(clientSocket));
                        } catch (RejectedExecutionException e) {
                            log.warn("Task queue is full, rejecting connection: {}", clientSocket.getInetAddress());
                            clientSocket.close();
                        }
                    } catch (IOException e) {
                        if (isRunning) {
                            log.error("Error occurred while accepting client connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Error creating server socket", e);
            }
        });
    }

    private void handleClient(Socket clientSocket) {
        log.info("Starting client processing: {}", clientSocket.getInetAddress());
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            // 소켓과 출력 스트림을 SessionRegistry에 등록 (공유 인스턴스 보장)
            sessionRegistry.addSocket(clientSocket, out);

            // 연결 유지 설정
            clientSocket.setKeepAlive(true); // TCP Keep-Alive 활성화
            clientSocket.setSoTimeout(0);    // 타임아웃 무한대 설정

            while (isRunning) {
                try {
                    // 1. 헤더 읽기 (페이로드 길이) (4 bytes)
                    int length = in.readInt();
                    
                    // 2. 헤더 읽기 (메시지 타입) (4 bytes)
                    int messageType = in.readInt();

                    // 3. 바디 데이터 읽기
                    byte[] payload = new byte[length];
                    in.readFully(payload);

                    // 4. Dispatcher를 통한 라우팅 및 동적 실행 (Socket 전달)
                    Object response = dispatcher.dispatch(clientSocket, messageType, payload);

                    // 에코 응답
                    if (response != null) {
                        byte[] responsePayload = serializer.serialize((ChatMessage) response);
                        
                        synchronized (out) {
                            out.writeInt(responsePayload.length);
                            out.writeInt(messageType);
                            out.write(responsePayload);
                            out.flush();
                        }
                    }
                } catch (java.io.EOFException | java.net.SocketException e) {
                    // 연결 종료 예외는 finally 블록에서 처리되도록 상위로 전파
                    throw e;
                } catch (Exception e) {
                    // 개별 메시지 처리 중 비즈니스 로직 오류는 로그만 남기고 연결 유지
                    log.error("Error processing message (maintaining connection): {}", clientSocket.getInetAddress(), e);
                }
            }
        } catch (java.io.EOFException | java.net.SocketException e) {
            log.info("Client connection terminated or initialized: {}", clientSocket.getInetAddress());
        } catch (Exception e) {
            if (isRunning) {
                log.error("Client communication error (session terminated): {}", clientSocket.getInetAddress(), e);
            }
        } finally {
            // SessionRegistry를 통해 userId 식별
            String userId = sessionRegistry.getUserId(clientSocket).orElse(clientSocket.getInetAddress().toString());
            
            // Observer 패턴: 세션 종료 이벤트 발행
            EventBus.getInstance().publish(new SessionClosedEvent(userId));
            
            // 세션 등록 정보 제거
            sessionRegistry.unregisterBySocket(clientSocket);
            
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                log.warn("Failed to close socket", e);
            }
        }
    }

    @PreDestroy
    public void stopServer() {
        log.info("Shutting down socket server... (Graceful shutdown started)");
        isRunning = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("Error occurred while closing server socket", e);
        }

        bossExecutor.shutdown();
        workerExecutor.shutdown();

        try {
            // 남은 작업이 완료될 때까지 대기
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Worker thread pool did not terminate within 30 seconds, forcing shutdown.");
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("Socket server has been completely shut down.");
    }

    /**
     * 스레드에 이름을 부여하기 위한 커스텀 스레드 팩토리
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String baseName;
        private final AtomicInteger threadId = new AtomicInteger(1);

        public NamedThreadFactory(String baseName) {
            this.baseName = baseName;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, baseName + "-" + threadId.getAndIncrement());
            if (thread.isDaemon()) thread.setDaemon(false);
            if (thread.getPriority() != Thread.NORM_PRIORITY) thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
