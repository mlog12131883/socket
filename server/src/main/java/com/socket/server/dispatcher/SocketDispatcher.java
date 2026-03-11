package com.socket.server.dispatcher;

import com.socket.server.interceptor.ChannelInterceptor;
import com.socket.server.serializer.JsonMessageSerializer;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SocketDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SocketDispatcher.class);

    private final HandlerMapping handlerMapping;
    private final JsonMessageSerializer<?> serializer;
    private final List<ChannelInterceptor> interceptors;

    /**
     * 수신된 메시지를 적절한 컨트롤러 메서드로 디스패치합니다.
     * 
     * @param clientSocket 클라이언트 소켓 (인터셉터에서 사용)
     * @param messageType 메시지 타입
     * @param payload JSON 바디 데이터
     * @return 컨트롤러 실행 결과
     */
    public Object dispatch(Socket clientSocket, int messageType, byte[] payload) {
        // 1. 인터셉터 Pre-Handle 실행
        for (ChannelInterceptor interceptor : interceptors) {
            if (!interceptor.preHandle(clientSocket, messageType, payload)) {
                log.warn("인터셉터에 의해 요청이 차단되었습니다: {}", interceptor.getClass().getSimpleName());
                return null;
            }
        }

        HandlerMapping.HandlerMethod handler = handlerMapping.getHandler(messageType);
        
        if (handler == null) {
            log.warn("매핑된 핸들러를 찾을 수 없습니다. messageType: {}", messageType);
            throw new IllegalArgumentException("No handler found for messageType: " + messageType);
        }

        Method method = handler.getMethod();
        Object instance = handler.getInstance();
        
        try {
            // 매개변수 분석 및 바인딩
            Object[] args = resolveArguments(method, clientSocket, payload);
            
            log.info("디스패처: 메서드 [{}] 실행 시작", method.getName());
            // 동적 메서드 호출
            Object result = method.invoke(instance, args);

            // 2. 인터셉터 Post-Handle 실행
            for (ChannelInterceptor interceptor : interceptors) {
                interceptor.postHandle(clientSocket, messageType, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("메서드 실행 중 오류 발생", e);
            throw new RuntimeException("Method execution failed", e);
        }
    }

    private Object[] resolveArguments(Method method, Socket clientSocket, byte[] payload) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            
            // @com.socket.server.annotation.MessageBody 어노테이션이 있는 경우
            if (parameter.isAnnotationPresent(com.socket.server.annotation.MessageBody.class)) {
                Class<?> targetType = parameter.getType();
                // 직렬화기를 이용해 페이로드를 대상 클래스 타입으로 변환
                args[i] = serializer.deserialize(payload, targetType);
            } else if (parameter.getType().equals(Socket.class)) {
                // Socket 타입인 경우 현재 클라이언트 소켓 주입
                args[i] = clientSocket;
            } else {
                // 어노테이션이 없는 경우 기본적으로 null 처리 (또는 다른 확장 가능)
                args[i] = null;
            }
        }
        return args;
    }
}
