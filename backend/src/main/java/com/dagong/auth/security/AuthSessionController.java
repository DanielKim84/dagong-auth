package com.dagong.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** DB에서 계정이 삭제된 상태로 남은 SSO 세션을 끊어 새 소셜 인증을 유도합니다. */
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
