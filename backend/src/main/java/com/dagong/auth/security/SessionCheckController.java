package com.dagong.auth.security;

import com.dagong.auth.account.ActiveAccountChecker;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 맛다공 게이트웨이가 요청을 서비스로 넘기기 전에 부르는 로그인 확인 주소입니다. DB에 아무것도 쓰지 않습니다.
 * 토큰 서명·audience와 인가 기록이 남아 있는지(다른 기기 로그인·로그아웃·탈퇴로 지워지지 않았는지)는 JwtDecoder가 먼저
 * 확인해 아니면 401로 답하고, 여기서는 계정이 차단·탈퇴되지 않았는지만 더 봅니다.
 */
@RestController
public class SessionCheckController {
    private final ActiveAccountChecker activeAccountChecker;

    public SessionCheckController(ActiveAccountChecker activeAccountChecker) {
        this.activeAccountChecker = activeAccountChecker;
    }

    @GetMapping("/api/v1/matdagong/session")
    public ResponseEntity<Void> check(@AuthenticationPrincipal Jwt jwt) {
        boolean isActive = jwt != null && activeAccountChecker.isActive(jwt.getSubject());
        return ResponseEntity.status(isActive ? HttpStatus.NO_CONTENT : HttpStatus.UNAUTHORIZED)
                .build();
    }
}
