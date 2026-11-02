package com.dagong.auth.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** 토큰의 audience에 지정한 client_id가 있는지 확인합니다. aud 클레임이 없는 토큰도 거부합니다. */
class AudienceValidator implements OAuth2TokenValidator<Jwt> {
    private final String clientId;

    AudienceValidator(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        if (audience != null && audience.contains(clientId)) return OAuth2TokenValidatorResult.success();
        // 오류 코드는 OAuth2 표준 값(invalid_token)을 그대로 씁니다.
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "맛다공용으로 발급된 토큰이 아닙니다.", null));
    }
}
