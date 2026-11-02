package com.dagong.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {
    private final AudienceValidator validator = new AudienceValidator("matdagong-web-dev");

    @Test
    void 맛다공_client가_audience에_있으면_통과한다() {
        Jwt jwt = token(List.of("matdagong-web-dev"));

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void 다른_client의_토큰이나_aud가_없는_토큰은_invalid_token으로_거부한다() {
        for (Jwt jwt : List.of(token(List.of("yeodagong-web-dev")), token(null))) {
            OAuth2TokenValidatorResult result = validator.validate(jwt);

            assertThat(result.hasErrors()).isTrue();
            assertThat(result.getErrors()).singleElement().satisfies(error -> assertThat(error.getErrorCode())
                    .isEqualTo(OAuth2ErrorCodes.INVALID_TOKEN));
        }
    }

    private static Jwt token(List<String> audience) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("11111111-1111-1111-1111-111111111111")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (audience != null) builder.audience(audience);
        return builder.build();
    }
}
