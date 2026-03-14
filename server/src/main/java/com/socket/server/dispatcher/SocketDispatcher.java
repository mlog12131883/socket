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
     * @param messageType  메시지 타입
     * @param payload      JSON 바디 데이터
     * @return 컨트롤러 실행 결과
     */
    public Object dispatch(Socket clientSocket, int messageType, byte[] payload) {
        // 1. Execute interceptor Pre-Handle
        for (ChannelInterceptor interceptor : interceptors) {
            if (!interceptor.preHandle(clientSocket, messageType, payload)) {
                log.warn("Request blocked by interceptor: {}", interceptor.getClass().getSimpleName());
                return null;
            }
        }

        HandlerMapping.HandlerMethod handler = handlerMapping.getHandler(messageType);
        
        if (handler == null) {
            log.warn("Mapped handler not found. messageType: {}", messageType);
            throw new IllegalArgumentException("No handler found for messageType: " + messageType);
        }

        Method method = handler.getMethod();
        Object instance = handler.getInstance();
        
        try {
            // Parameter analysis and binding
            Object[] args = resolveArguments(method, clientSocket, payload);
            
            log.info("Dispatcher: Starting execution of method [{}]", method.getName());
            // Dynamic method invocation
            Object result = method.invoke(instance, args);

            // 2. Execute interceptor Post-Handle
            for (ChannelInterceptor interceptor : interceptors) {
                interceptor.postHandle(clientSocket, messageType, result);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error occurred during method execution", e);
            throw new RuntimeException("Method execution failed", e);
        }
    }

    private Object[] resolveArguments(Method method, Socket clientSocket, byte[] payload) throws Exception {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            
            // @com.socket.server.annotation.MessageBody 어노테이션이 있으면
            if (parameter.isAnnotationPresent(com.socket.server.annotation.MessageBody.class)) {
                Class<?> targetType = parameter.getType();
                // 직렬화기로 payload를 대상 클래스 타입으로 변환
                args[i] = serializer.deserialize(payload, targetType);
            } else if (parameter.getType().equals(Socket.class)) {
                // 파라미터 타입이 Socket이면 현재 클라이언트 소켓 주입
                args[i] = clientSocket;
            } else {
                // 어노테이션이 없으면 null 처리 (확장 가능)
                args[i] = null;
            }
        }
        return args;
    }
}
