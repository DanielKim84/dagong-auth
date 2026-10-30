package com.dagong.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 인증 서버의 SSO 세션을 끊습니다. 맛다공이 로그아웃할 때와, 토큰이 거부되거나 DB에서 계정이 사라져
 * SSO 세션만 남았을 때 호출해 다음 인가 요청에서 소셜 인증을 다시 받게 합니다.
 */
@RestController
public class AuthSessionController {
    @PostMapping("/auth/session/clear")
    public ResponseEntity<Void> clear(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
