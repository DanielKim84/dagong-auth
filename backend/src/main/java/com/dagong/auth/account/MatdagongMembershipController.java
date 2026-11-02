package com.dagong.auth.account;

import com.dagong.auth.account.MembershipModels.JoinRequest;
import com.dagong.auth.account.MembershipModels.JoinResult;
import com.dagong.auth.account.MembershipModels.MembershipStatus;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 맛다공이 공통 계정의 가입 여부를 확인하고 서비스 가입을 마칠 수 있는 OAuth 보호 API입니다.
 * 맛다공 client용 토큰(audience)인지는 JwtDecoder가 먼저 확인합니다.
 */
@RestController
@RequestMapping("/api/v1/matdagong/membership")
public class MatdagongMembershipController {
    private final MatdagongMembershipService membershipService;

    public MatdagongMembershipController(MatdagongMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    public MembershipStatus status(@AuthenticationPrincipal Jwt jwt) {
        return membershipService.status(accountId(jwt));
    }

    @PostMapping
    public JoinResult join(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinRequest request) {
        return membershipService.join(accountId(jwt), request);
    }

    private static UUID accountId(Jwt jwt) {
        String subject = jwt == null ? null : jwt.getSubject();
        if (subject != null) {
            try {
                return UUID.fromString(subject);
            } catch (IllegalArgumentException exception) {
                // 계정 UUID가 아닌 주체는 아래에서 401로 응답합니다.
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정 정보를 확인할 수 없습니다.");
    }
}
