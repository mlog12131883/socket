package com.socket.server.service;

import com.socket.server.domain.ChatMessage;
import com.socket.server.persistence.ChatMessageEntity;
import com.socket.server.persistence.ChatMessageJpaRepository;
import com.socket.server.repository.ChatMessageBufferRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageWriteBehindService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageWriteBehindService.class);

    // 한 번에 최대 500건 처리
    private static final int BATCH_SIZE = 500;

    private final ChatMessageBufferRepository bufferRepository;
    private final ChatMessageJpaRepository jpaRepository;

    @Async("chatWriteBehindExecutor")
    public void enqueue(ChatMessage message) {
        bufferRepository.push(message);
    }

    /**
     * 배치 플러시:
     * - 이전 실행이 끝난 시점 기준으로 5초 뒤에 다시 실행
     * - Redis 버퍼에서 최대 500건씩 여러 번 꺼내 JPA로 저장
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void flushBatch() {
        log.info("[ChatMessageWriteBehindService] flushBatch scheduler triggered");
        while (true) {
            List<ChatMessage> batch = bufferRepository.popBatch(BATCH_SIZE);
            if (batch.isEmpty()) {
                log.info("[ChatMessageWriteBehindService] No messages to flush (buffer empty)");
                return;
            }

            var entities = batch.stream()
                    .map(m -> ChatMessageEntity.of(
                            m.getRoomId(),
                            m.getSenderId(),
                            m.getContent(),
                            m.getTimestamp()
                    ))
                    .collect(Collectors.toList());


            jpaRepository.saveAll(entities);
            log.info("Flushed {} chat messages to DB (write-behind JPA)", entities.size());
        }
    }
}

