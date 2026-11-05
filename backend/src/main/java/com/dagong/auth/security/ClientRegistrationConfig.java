package com.dagong.auth.security;

import com.dagong.auth.account.AccountRoleService;
import com.dagong.auth.account.ActiveAccountChecker;
import com.dagong.auth.device.DeviceLoginService;
import com.dagong.auth.device.DeviceTrackingAuthorizationService;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

/** 맛다공 클라이언트 등록, 인가·동의 저장소, access token 클레임을 설정합니다. */
@Configuration
public class ClientRegistrationConfig {
    /**
     * 개발용 맛다공 공개 클라이언트를 최초 한 번 등록하고, 서버 시작 때마다 callback 주소·PKCE·동의 생략·
     * access token 수명을 설정값과 동기화합니다. DB에 저장된 그 밖의 클라이언트·토큰 설정은 유지합니다.
     * 브라우저 공개 클라이언트는 Authorization Code + PKCE를 사용하며 client secret을 저장하지 않습니다.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository(
            JdbcTemplate jdbc,
            @Value("${dagong.auth.matdagong-client-id}") String clientId,
            @Value("${dagong.auth.matdagong-redirect-uri}") String redirectUri,
            @Value("${dagong.auth.access-token-ttl}") Duration accessTokenTtl) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbc);

        RegisteredClient existingClient = repository.findByClientId(clientId);
        RegisteredClient.Builder client;
        ClientSettings.Builder settings;
        TokenSettings.Builder tokenSettings;
        if (existingClient == null) {
            client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientName("Matdagong Web (development)")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE);
            settings = ClientSettings.builder();
            tokenSettings = TokenSettings.builder();
        } else {
            client = RegisteredClient.from(existingClient);
            settings = ClientSettings.withSettings(
                    existingClient.getClientSettings().getSettings());
            tokenSettings =
                    TokenSettings.withSettings(existingClient.getTokenSettings().getSettings());
        }

        // save()는 기존 ID를 갱신합니다. 예전 callback 주소를 제거하고 현재 설정 하나만 허용합니다.
        repository.save(client.redirectUris(uris -> {
                    uris.clear();
                    uris.add(redirectUri);
                })
                .clientSettings(settings.requireProofKey(true)
                        // 공식 서비스인 맛다공은 별도 scope 동의 화면 없이 이어갑니다.
                        .requireAuthorizationConsent(false)
                        .build())
                // 브라우저 공개 클라이언트는 refresh token을 받지 않습니다. 로그아웃·권한 회수 뒤에도 토큰은 만료 전까지 유효하므로
                // 기본값은 30분으로 짧게 두고, 개발 PC에서만 AUTH_ACCESS_TOKEN_TTL로 늘립니다.
                .tokenSettings(
                        tokenSettings.accessTokenTimeToLive(accessTokenTtl).build())
                .build());

        return repository;
    }

    /**
     * access token을 발급할 때 세 가지를 처리합니다.
     * 1) 서비스 API가 다른 서비스의 토큰과 구분하도록 client_id를 audience로 지정합니다.
     * 2) 인가 코드를 받은 뒤 차단·탈퇴된 계정에는 토큰을 발급하지 않습니다.
     * 3) account_roles의 활성 권한(ADMIN, SUPER_TASTER)을 roles 클레임으로 서명해 전달합니다.
     */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenCustomizer(
            ActiveAccountChecker activeAccountChecker, AccountRoleService accountRoleService) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims()
                        .audience(List.of(context.getRegisteredClient().getClientId()));
                if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                        && !activeAccountChecker.isActive(context.getPrincipal().getName())) {
                    // 오류 코드는 OAuth2 표준 값(invalid_grant)을 그대로 씁니다.
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "이용이 제한된 계정입니다.", null));
                }
                try {
                    UUID accountId = UUID.fromString(context.getPrincipal().getName());
                    context.getClaims().claim("roles", accountRoleService.activeRoles(accountId));
                } catch (IllegalArgumentException ignored) {
                    // 주체가 계정 UUID가 아닌 토큰(예: 클라이언트 자체 인증)에는 계정 권한을 넣지 않습니다.
                }
            }
        };
    }

    /**
     * 인가 코드와 토큰을 DB에 저장해 서버를 재시작해도 활성 인가 상태를 유지합니다.
     * 맛다공 인가 코드를 저장할 때는 어느 기기의 로그인인지 함께 남깁니다(한 기기 로그인).
     */
    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbc,
            RegisteredClientRepository registeredClientRepository,
            DeviceLoginService deviceLogins,
            @Value("${dagong.auth.matdagong-client-id}") String clientId) {
        return new DeviceTrackingAuthorizationService(
                new JdbcOAuth2AuthorizationService(jdbc, registeredClientRepository),
                registeredClientRepository,
                deviceLogins,
                clientId);
    }

    /** 사용자가 클라이언트에 허용한 scope를 DB에 저장합니다. */
    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcTemplate jdbc, RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, registeredClientRepository);
    }
}
