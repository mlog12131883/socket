package com.socket.server.dispatcher;

import com.socket.server.annotation.MessageMapping;
import com.socket.server.annotation.SocketController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandlerMapping {
    private static final Logger log = LoggerFactory.getLogger(HandlerMapping.class);
    
    private final Map<Integer, HandlerMethod> handlerMap = new HashMap<>();

    public HandlerMapping(String basePackage) throws Exception {
        log.info("Initializing HandlerMapping for base package: {}", basePackage);
        scanAndRegister(basePackage);
    }

    private void scanAndRegister(String basePackage) throws Exception {
        List<Class<?>> classes = getClasses(basePackage);
        for (Class<?> clazz : classes) {
            if (clazz.isAnnotationPresent(SocketController.class)) {
                log.info("Found @SocketController: {}", clazz.getName());
                // 인스턴스 생성 (기본 생성자 필요)
                Object instance = clazz.getDeclaredConstructor().newInstance();
                
                // 메서드 탐색
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
    }

    public HandlerMethod getHandler(int messageType) {
        return handlerMap.get(messageType);
    }

    /**
     * 특정 패키지 내의 모든 클래스를 찾습니다 (순수 자바 Reflection API 사용).
     */
    private List<Class<?>> getClasses(String packageName) throws ClassNotFoundException, IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        String path = packageName.replace('.', '/');
        Enumeration<URL> resources = classLoader.getResources(path);
        List<File> dirs = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
        }
        ArrayList<Class<?>> classes = new ArrayList<>();
        for (File directory : dirs) {
            classes.addAll(findClasses(directory, packageName));
        }
        return classes;
    }

    private List<Class<?>> findClasses(File directory, String packageName) throws ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        if (!directory.exists()) {
            return classes;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return classes;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                assert !file.getName().contains(".");
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
            }
        }
        return classes;
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
