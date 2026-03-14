package com.socket.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class ClientApplication {

	public static void main(String[] args) throws Exception {
		// Windows 환경에서 기본 인코딩(CP949)으로 인한 한국어 문자 깨짐 방지
		// System.out / System.err 를 UTF-8 스트림으로 교체
		System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
		System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
		SpringApplication.run(ClientApplication.class, args);
	}

}
