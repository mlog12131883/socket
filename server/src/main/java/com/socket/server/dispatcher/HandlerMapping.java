package com.socket.server.dispatcher;

import com.socket.server.annotation.MessageMapping;
import com.socket.server.annotation.SocketController;
import com.socket.server.domain.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

@Component
public class HandlerMapping {
    private static final Logger log = LoggerFactory.getLogger(HandlerMapping.class);
    
    private final ApplicationContext applicationContext;
    private final Map<Integer, HandlerMethod> handlerMap = new HashMap<>();

    public HandlerMapping(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing HandlerMapping using Spring ApplicationContext...");
        
        // 1. @SocketController 어노테이션이 붙은 모든 빈을 찾음
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(SocketController.class);
        
        for (Object instance : beans.values()) {
            Class<?> clazz = instance.getClass();
            log.info("Found @SocketController bean: {}", clazz.getName());
            
            // 2. 메서드 탐색
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(MessageMapping.class)) {
                    MessageMapping mapping = method.getAnnotation(MessageMapping.class);
                    int messageType = mapping.value().ordinal();
                    
                    HandlerMethod handlerMethod = new HandlerMethod(instance, method);
                    handlerMap.put(messageType, handlerMethod);
                    log.info("Registered mapping: messageType [{}] -> {}", messageType, method);
                }
            }
        }
    }

    public HandlerMethod getHandler(int messageType) {
        return handlerMap.get(messageType);
    }


    /**
     * 실행할 인스턴스와 메서드를 담는 래퍼 클래스
     */
    public static class HandlerMethod {
        private final Object instance;
        private final Method method;

        public HandlerMethod(Object instance, Method method) {
            this.instance = instance;
            this.method = method;
        }

        public Object getInstance() {
            return instance;
        }

        public Method getMethod() {
            return method;
        }
    }
}
