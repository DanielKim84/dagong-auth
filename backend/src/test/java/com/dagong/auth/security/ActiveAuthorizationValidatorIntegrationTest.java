package com.dagong.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.dagong.auth.support.TestDatabase;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** 인가 기록이 남은 access token만 통과시키고, 저장된 토큰 정보를 되살리지 않고도 확인하는지 실제 스키마로 봅니다. */
class ActiveAuthorizationValidatorIntegrationTest {
    private static TestDatabase database;
    private ActiveAuthorizationValidator validator;

    @BeforeAll
    static void startDatabase() throws IOException {
        database = TestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (database != null) database.close();
    }

    @BeforeEach
    void setUp() {
        database.jdbc().update("TRUNCATE oauth2_registered_client CASCADE");
        database.jdbc()
                .update(
                        """
                        INSERT INTO oauth2_registered_client (
                            id, client_id, client_name, client_authentication_methods, authorization_grant_types,
                            scopes, client_settings, token_settings
                        ) VALUES ('client', 'matdagong-web-dev', '맛다공', 'none', 'authorization_code', 'openid', '{}', '{}')
                        """);
        // 실제 저장 형식처럼 되살릴 수 없는 목록 형식이 metadata에 들어 있어도 확인에는 영향이 없어야 합니다.
        database.jdbc()
                .update(
                        """
                        INSERT INTO oauth2_authorization (
                            id, registered_client_id, principal_name, authorization_grant_type,
                            access_token_value, access_token_metadata
                        ) VALUES ('auth-1', 'client', 'account', 'authorization_code', 'live-token',
                                  '{"aud":["java.util.ImmutableCollections$List12",["matdagong-web-dev"]]}')
                        """);
        validator = new ActiveAuthorizationValidator(database.jdbc());
    }

    @Test
    void 인가_기록이_남은_토큰은_통과한다() {
        assertThat(validator.validate(jwt("live-token")).hasErrors()).isFalse();
    }

    @Test
    void 인가_기록이_지워진_토큰은_거부한다() {
        assertThat(validator.validate(jwt("ended-token")).hasErrors()).isTrue();
    }

    private static Jwt jwt(String tokenValue) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject("account")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }
}
