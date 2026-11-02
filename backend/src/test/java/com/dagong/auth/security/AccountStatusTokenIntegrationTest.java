package com.dagong.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dagong.auth.account.AccountRoleService;
import com.dagong.auth.account.AccountStatus;
import com.dagong.auth.account.ActiveAccountChecker;
import com.dagong.auth.support.TestDatabase;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** 실제 PostgreSQL에 Flyway 스키마를 적용하고, 차단·탈퇴 계정에 토큰이 발급되지 않는지 확인합니다. */
class AccountStatusTokenIntegrationTest {
    private static final UUID ACTIVE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BLOCKED = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DELETED = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private static TestDatabase database;
    private ActiveAccountChecker checker;
    private OAuth2TokenCustomizer<JwtEncodingContext> customizer;

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
        database.truncateAccounts();
        database.insertAccount(ACTIVE, AccountStatus.ACTIVE);
        database.insertAccount(BLOCKED, AccountStatus.BLOCKED);
        database.insertAccount(DELETED, AccountStatus.DELETED);
        database.jdbc()
                .update("INSERT INTO account_roles (user_id, role_cd, role_st) VALUES (?, 'ADMIN', 'ACTIVE')", ACTIVE);
        checker = new ActiveAccountChecker(database.jdbc());
        customizer =
                new ClientRegistrationConfig().accessTokenCustomizer(checker, new AccountRoleService(database.jdbc()));
    }

    @Test
    void 활성_계정만_이용_가능하다() {
        assertThat(checker.isActive(ACTIVE)).isTrue();
        assertThat(checker.isActive(BLOCKED)).isFalse();
        assertThat(checker.isActive(DELETED)).isFalse();
        assertThat(checker.isActive(UUID.randomUUID())).isFalse();
        assertThat(checker.isActive("anonymousUser")).isFalse();
    }

    @Test
    void 활성_계정의_access_token에는_audience와_권한이_들어간다() {
        JwtEncodingContext context = accessTokenContext(ACTIVE);

        customizer.customize(context);

        JwtClaimsSet claims = context.getClaims().build();
        assertThat(claims.getAudience()).containsExactly("matdagong-web-dev");
        assertThat(claims.<List<String>>getClaim("roles")).containsExactly("ADMIN");
    }

    @Test
    void 차단되거나_탈퇴한_계정에는_access_token을_발급하지_않는다() {
        assertThatThrownBy(() -> customizer.customize(accessTokenContext(BLOCKED)))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> customizer.customize(accessTokenContext(DELETED)))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    private static JwtEncodingContext accessTokenContext(UUID accountId) {
        RegisteredClient client = RegisteredClient.withId("client")
                .clientId("matdagong-web-dev")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost:4845/auth/callback")
                .build();
        return JwtEncodingContext.with(JwsHeader.with(SignatureAlgorithm.RS256), JwtClaimsSet.builder())
                .registeredClient(client)
                .principal(UsernamePasswordAuthenticationToken.authenticated(accountId.toString(), null, List.of()))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();
    }
}
