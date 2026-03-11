package com.socket.server.dispatcher;

import com.socket.server.annotation.MessageBody;
import com.socket.server.serializer.JsonMessageSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Component
public class SocketDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SocketDispatcher.class);

    private final HandlerMapping handlerMapping;
    private final JsonMessageSerializer<?> serializer;

    public SocketDispatcher(JsonMessageSerializer<?> serializer) throws Exception {
        this.serializer = serializer;
        // 서버의 컨트롤러 패키지로 하드코딩 또는 설정 주입 가능
        this.handlerMapping = new HandlerMapping("com.socket.server.controller");
    }

    /**
     * 수신된 메시지를 적절한 컨트롤러 메서드로 디스패치합니다.
     * 
     * @param messageType 메시지 타입 (예: ENTER, CHAT 등)
     * @param payload JSON 바디 데이터
     * @return 컨트롤러 실행 결과 (응답으로 전달할 객체)
     */
    public Object dispatch(int messageType, byte[] payload) {
        HandlerMapping.HandlerMethod handler = handlerMapping.getHandler(messageType);
        
        if (handler == null) {
            log.warn("매핑된 핸들러를 찾을 수 없습니다. messageType: {}", messageType);
            throw new IllegalArgumentException("No handler found for messageType: " + messageType);
        }

        Method method = handler.getMethod();
        Object instance = handler.getInstance();
        
        try {
            // 매개변수 분석 및 바인딩
            Object[] args = resolveArguments(method, payload);
            
            log.info("디스패처: 메서드 [{}] 실행 시작", method.getName());
            // 동적 메서드 호출
            return method.invoke(instance, args);
            
        } catch (Exception e) {
            log.error("메서드 실행 중 오류 발생", e);
            throw new RuntimeException("Method execution failed", e);
        }
    }

    private Object[] resolveArguments(Method method, byte[] payload) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            
            // @MessageBody 어노테이션이 있는 경우
            if (parameter.isAnnotationPresent(MessageBody.class)) {
                Class<?> targetType = parameter.getType();
                // 직렬화기를 이용해 페이로드를 대상 클래스 타입으로 변환
                args[i] = serializer.deserialize(payload, targetType);
            } else {
                // 어노테이션이 없는 경우 기본적으로 null 처리 (또는 다른 확장 가능)
                args[i] = null;
            }
        }
        return args;
    }
}
