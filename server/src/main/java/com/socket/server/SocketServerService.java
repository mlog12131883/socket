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
        // Create custom ThreadPoolExecutor (Worker)
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
                        
                        // Delegate actual processing to worker thread pool (Non-blocking Accept)
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

            // Register socket and output stream in SessionRegistry (ensures shared instance sharing)
            sessionRegistry.addSocket(clientSocket, out);

            // Connection persistence settings
            clientSocket.setKeepAlive(true); // Enable TCP Keep-Alive
            clientSocket.setSoTimeout(0);    // Set timeout to infinite

            while (isRunning) {
                try {
                    // 1. Read header (Payload length) (4 bytes)
                    int length = in.readInt();
                    
                    // 2. Read header (Message type) (4 bytes)
                    int messageType = in.readInt();

                    // 3. Read Body data
                    byte[] payload = new byte[length];
                    in.readFully(payload);

                    // 4. Routing and dynamic execution through Dispatcher (passing Socket)
                    Object response = dispatcher.dispatch(clientSocket, messageType, payload);

                    // Echo response
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
                    // Connection disconnection exceptions are thrown to be handled in finally block
                    throw e;
                } catch (Exception e) {
                    // Business logic errors during individual message processing are logged, and connection is maintained
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
            // Identify userId via SessionRegistry
            String userId = sessionRegistry.getUserId(clientSocket).orElse(clientSocket.getInetAddress().toString());
            
            // Observer Pattern: Publish session closed event
            EventBus.getInstance().publish(new SessionClosedEvent(userId));
            
            // Remove session registration info
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
            // Wait for remaining tasks to complete
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
