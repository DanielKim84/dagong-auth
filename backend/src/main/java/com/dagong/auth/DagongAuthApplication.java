package com.dagong.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** 맛다공·여다공 공통 인증 서버의 Spring Boot 시작점입니다. */
// 별도 인증 사용자를 아직 구성하지 않았으므로 임의의 메모리 사용자 자동 생성을 끕니다.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class DagongAuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(DagongAuthApplication.class, args);
    }
}
