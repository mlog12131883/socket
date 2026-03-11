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
        // 커스텀 ThreadPoolExecutor 생성 (Worker)
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
                log.info("소켓 서버가 포트 {}에서 대기 중입니다... (WAS 방식 스레드 풀 적용)", port);

                while (isRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.info("클라이언트 접속 수락: {}", clientSocket.getInetAddress());
                        
                        // 실제 처리는 Worker 스레드 풀로 위임 (Non-blocking Accept)
                        try {
                            workerExecutor.submit(() -> handleClient(clientSocket));
                        } catch (RejectedExecutionException e) {
                            log.warn("작업 큐가 가득 차서 연결을 거절합니다: {}", clientSocket.getInetAddress());
                            clientSocket.close();
                        }
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
        log.info("클라이언트 처리 시작: {}", clientSocket.getInetAddress());
        try (DataInputStream in = new DataInputStream(clientSocket.getInputStream());
             DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream())) {

            // SessionRegistry에 소켓과 출력 스트림 등록 (동일 인스턴스 공유 보장)
            sessionRegistry.addSocket(clientSocket, out);

            // 연결 지속성 설정
            clientSocket.setKeepAlive(true); // TCP Keep-Alive 활성화
            clientSocket.setSoTimeout(0);    // 타임아웃 무제한 설정

            while (isRunning) {
                try {
                    // 1. 헤더(Payload 길이) 읽기 (4 bytes)
                    int length = in.readInt();
                    
                    // 2. 헤더(메시지 타입) 읽기 (4 bytes)
                    int messageType = in.readInt();

                    // 3. Body 데이터 읽기
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
                    // 연결 끊김 관련 예외는 외부로 던져 finally에서 정리하도록 함
                    throw e;
                } catch (Exception e) {
                    // 개별 메시지 처리 중 발생하는 비즈니스 로직 에러는 로그만 남기고 연결 유지
                    log.error("메시지 처리 중 에러 발생 (연결 상태 유지): {}", clientSocket.getInetAddress(), e);
                }
            }
        } catch (java.io.EOFException | java.net.SocketException e) {
            log.info("클라이언트 연결 종료 또는 초기화: {}", clientSocket.getInetAddress());
        } catch (Exception e) {
            if (isRunning) {
                log.error("클라이언트 통신 에너 (세션 종료): {}", clientSocket.getInetAddress(), e);
            }
        } finally {
            // SessionRegistry를 통해 userId 식별
            String userId = sessionRegistry.getUserId(clientSocket).orElse(clientSocket.getInetAddress().toString());
            
            // Observer 패턴: 연결 종료 이벤트 발행
            EventBus.getInstance().publish(new SessionClosedEvent(userId));
            
            // 세션 등록 정보 제거
            sessionRegistry.unregisterBySocket(clientSocket);
            
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                log.warn("소켓 닫기 실패", e);
            }
        }
    }

    @PreDestroy
    public void stopServer() {
        log.info("소켓 서버를 종료합니다. (Graceful Shutdown 시작)");
        isRunning = false;
        
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            log.error("서버 소켓 종료 중 오류 발생", e);
        }

        bossExecutor.shutdown();
        workerExecutor.shutdown();

        try {
            // 잔여 작업 처리를 위한 대기
            if (!workerExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("워커 스레드 풀이 30초 내에 종료되지 않아 강제 종료합니다.");
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("소켓 서버가 완전히 종료되었습니다.");
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
