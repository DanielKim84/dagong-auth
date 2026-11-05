package com.dagong.auth.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 인증 서버 API(맛다공 가입 상태, 게이트웨이의 로그인 확인 등)에 온 access token의 인가 기록이 아직 남아 있는지 확인합니다.
 * 다른 기기 로그인·로그아웃·탈퇴로 인가 기록을 지운 토큰은 서명이 맞아도 401로 거부합니다.
 *
 * <p>인가 기록 저장소(findByToken)로 읽으면 저장된 토큰 정보(JSON)를 객체로 되살리는데, 그 안의 목록 형식을 보안 설정이
 * 허용하지 않아 실패합니다. 여기서는 그 토큰 값의 행이 있는지만 보면 되므로 색인(idx_oauth2_access_token)으로 직접 확인합니다.
 */
class ActiveAuthorizationValidator implements OAuth2TokenValidator<Jwt> {
    private final JdbcTemplate jdbc;

    ActiveAuthorizationValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Boolean exists = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM oauth2_authorization WHERE access_token_value = ?)",
                Boolean.class,
                jwt.getTokenValue());
        if (Boolean.TRUE.equals(exists)) return OAuth2TokenValidatorResult.success();
        // 오류 코드는 OAuth2 표준 값(invalid_token)을 그대로 씁니다.
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "로그인이 끝난 토큰입니다.", null));
    }
}
