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
     * Dispatches the received message to the appropriate controller method.
     * 
     * @param clientSocket Client socket (used in interceptors)
     * @param messageType Message type
     * @param payload JSON body data
     * @return Controller execution result
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
            
            // If @com.socket.server.annotation.MessageBody annotation is present
            if (parameter.isAnnotationPresent(com.socket.server.annotation.MessageBody.class)) {
                Class<?> targetType = parameter.getType();
                // Convert payload to target class type using the serializer
                args[i] = serializer.deserialize(payload, targetType);
            } else if (parameter.getType().equals(Socket.class)) {
                // If parameter type is Socket, inject current client socket
                args[i] = clientSocket;
            } else {
                // Handle as null if no annotation is present (can be extended)
                args[i] = null;
            }
        }
        return args;
    }
}
