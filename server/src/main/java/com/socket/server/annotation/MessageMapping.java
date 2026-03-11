package com.socket.server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.socket.server.domain.MessageType;

/**
 * Annotation for mapping a specific message type onto a specific handler method.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MessageMapping {
    /**
     * The type of message to handle.
     */
    MessageType value();
}
