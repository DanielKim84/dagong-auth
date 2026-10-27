package com.dagong.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/** 맛다공·여다공 공통 인증 서버의 Spring Boot 시작점입니다. */
// 소셜 인증을 사용하므로 기본 메모리 사용자 자동 생성을 끕니다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DagongAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(DagongAuthApplication.class, args);
    }
}
