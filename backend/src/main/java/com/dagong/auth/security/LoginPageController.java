package com.dagong.auth.security;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 로그인 URL을 React 화면으로 연결합니다. 소셜 제공자 인증은 별도의 서버 흐름에서 처리합니다. */
@Controller
public class LoginPageController {
    @GetMapping("/login")
    public String login() {
        // Gradle 빌드가 프런트 결과물을 /auth 경로에 포함하므로 해당 정적 HTML로 전달합니다.
        return "forward:/auth/index.html";
    }
}
