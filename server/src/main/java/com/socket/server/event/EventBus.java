package com.socket.server.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Singleton event bus governing global events (Observer pattern)
 */
public class EventBus {
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);
    private static final EventBus instance = new EventBus();

    private final List<Consumer<Object>> subscribers = new ArrayList<>();

    private EventBus() {}

    public static EventBus getInstance() {
        return instance;
    }

    public synchronized void subscribe(Consumer<Object> subscriber) {
        subscribers.add(subscriber);
    }

    public synchronized void publish(Object event) {
        log.info("[EventBus] Event published: {}", event.getClass().getSimpleName());
        for (Consumer<Object> subscriber : subscribers) {
            subscriber.accept(event);
        }
    }
}
