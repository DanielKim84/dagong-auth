package com.dagong.auth.security;

import com.dagong.auth.account.ActiveAccountChecker;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/** OAuth2/OIDC 엔드포인트, 클라이언트, 인가 저장소와 토큰 서명 키를 설정합니다. */
@Configuration
public class SecurityConfig {
    /**
     * 이 서버가 발급한 맛다공 API bearer token을 issuer의 JWKS 공개키와 issuer 값으로 검증합니다.
     * audience는 각 API 컨트롤러에서 확인합니다.
     */
    @Bean
    JwtDecoder jwtDecoder(@Value("${dagong.auth.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks").build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /** 맛다공 SPA의 API는 발급된 access token으로 인증하고 세션·CSRF 없이 처리합니다. */
    @Bean
    @Order(0)
    SecurityFilterChain matdagongApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /** OAuth2·OIDC 엔드포인트를 보호하고 미인증 요청을 공통 로그인 화면으로 보냅니다. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            ActiveAccountChecker activeAccountChecker,
            @Value("${dagong.auth.login-page-url}") String loginPageUrl) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, server -> server
                        .oidc(Customizer.withDefaults())
                        // 인가 요청 검증기를 교체해 등록된 localhost redirect_uri를 허용하고 계정 상태를 확인합니다.
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers ->
                                providers.forEach(provider -> {
                                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
                                        codeProvider.setAuthenticationValidator(
                                                authorizationRequestValidator(activeAccountChecker));
                                    }
                                }))))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint(loginPageUrl)));

        return http.build();
    }

    /** React 로그인 화면과 정적 파일은 공개하고 나머지 애플리케이션 경로는 보호합니다. */
    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            KakaoOAuth2UserService kakaoOAuth2UserService,
            @Value("${dagong.auth.login-page-url}") String loginPageUrl,
            @Value("${dagong.auth.login-success-url}") String loginSuccessUrl,
            @Value("${dagong.auth.login-failure-url}") String loginFailureUrl) throws Exception {
        // 세션 정리 API는 CSRF 토큰을 받을 수 없는 맛다공 SPA가 프록시(/auth-api)로 호출하므로 CSRF 검사에서 제외합니다.
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/auth/session/clear"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/login",
                                "/error",
                                "/auth/**",
                                "/oauth2/authorization/**",
                                "/login/oauth2/code/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint(loginPageUrl)))
                .oauth2Login(oauth2 -> oauth2
                        .loginPage(loginPageUrl)
                        // 저장된 OAuth 인가 요청이 있으면 로그인 뒤 이어서 처리하고, 단독 로그인은 화면으로 돌아옵니다.
                        .defaultSuccessUrl(loginSuccessUrl, false)
                        .failureUrl(loginFailureUrl)
                        .userInfoEndpoint(userInfo -> userInfo.userService(kakaoOAuth2UserService)));

        return http.build();
    }

    /**
     * 인가 요청 검증기입니다. Spring Authorization Server 기본 검증기는 redirect_uri 호스트가 localhost이면
     * 400 오류를 내므로, 등록된 redirect URI와 문자 그대로 같은 localhost 주소만 예외로 통과시킵니다.
     * 그 외 주소와 scope 검증은 기본 규칙을 그대로 사용합니다.
     * SSO 세션이 남아 있어도 차단·탈퇴된 계정에는 인가 코드를 발급하지 않습니다.
     */
    private static Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> authorizationRequestValidator(
            ActiveAccountChecker activeAccountChecker) {
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> redirectUriValidator = context -> {
            // getAuthentication()은 제네릭 메서드라 람다 안에서는 타입을 명시해야 합니다.
            OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest = context.getAuthentication();
            String requestedRedirectUri = authorizationRequest.getRedirectUri();
            if (requestedRedirectUri != null
                    && isLocalhost(requestedRedirectUri)
                    && context.getRegisteredClient().getRedirectUris().contains(requestedRedirectUri)) {
                return;
            }
            OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_REDIRECT_URI_VALIDATOR.accept(context);
        };
        // 로그인 전 요청은 Kakao 로그인 단계에서 상태를 확인하므로, 인증된 사용자 요청만 검사합니다.
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> accountStatusValidator = context -> {
            OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest = context.getAuthentication();
            if (!(authorizationRequest.getPrincipal() instanceof Authentication principal)
                    || principal instanceof AnonymousAuthenticationToken
                    || !principal.isAuthenticated()) {
                return;
            }
            if (!activeAccountChecker.isActive(principal.getName())) {
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "이용이 제한된 계정입니다.", null),
                        authorizationRequest);
            }
        };
        return redirectUriValidator
                .andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR)
                .andThen(accountStatusValidator);
    }

    private static boolean isLocalhost(String uri) {
        try {
            return "localhost".equalsIgnoreCase(URI.create(uri).getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 개발용 맛다공 공개 클라이언트를 최초 한 번 등록하고, 서버 시작 때마다 callback 주소·PKCE·동의 생략·
     * access token 수명을 설정값과 동기화합니다. DB에 저장된 그 밖의 클라이언트·토큰 설정은 유지합니다.
     * 브라우저 공개 클라이언트는 Authorization Code + PKCE를 사용하며 client secret을 저장하지 않습니다.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository(
            JdbcOperations jdbcOperations,
            @Value("${dagong.auth.matdagong-client-id}") String clientId,
            @Value("${dagong.auth.matdagong-redirect-uri}") String redirectUri,
            @Value("${dagong.auth.access-token-ttl}") Duration accessTokenTtl) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcOperations);

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
            settings = ClientSettings.withSettings(existingClient.getClientSettings().getSettings());
            tokenSettings = TokenSettings.withSettings(existingClient.getTokenSettings().getSettings());
        }

        // save()는 기존 ID를 갱신합니다. 예전 callback 주소를 제거하고 현재 설정 하나만 허용합니다.
        repository.save(client
                .redirectUris(uris -> {
                    uris.clear();
                    uris.add(redirectUri);
                })
                .clientSettings(settings
                        .requireProofKey(true)
                        // 공식 서비스인 맛다공은 별도 scope 동의 화면 없이 이어갑니다.
                        .requireAuthorizationConsent(false)
                        .build())
                // 브라우저 공개 클라이언트는 refresh token을 받지 않습니다. 로그아웃·권한 회수 뒤에도 토큰은 만료 전까지 유효하므로
                // 기본값은 30분으로 짧게 두고, 개발 PC에서만 AUTH_ACCESS_TOKEN_TTL로 늘립니다.
                .tokenSettings(tokenSettings.accessTokenTimeToLive(accessTokenTtl).build())
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
            JdbcOperations jdbc, ActiveAccountChecker activeAccountChecker) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(List.of(context.getRegisteredClient().getClientId()));
                if (AuthorizationGrantType.AUTHORIZATION_CODE.equals(context.getAuthorizationGrantType())
                        && !activeAccountChecker.isActive(context.getPrincipal().getName())) {
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT, "이용이 제한된 계정입니다.", null));
                }
                try {
                    UUID userId = UUID.fromString(context.getPrincipal().getName());
                    List<String> roles = jdbc.query(
                            "SELECT role_cd FROM account_roles WHERE user_id = ? AND role_st = 'ACTIVE' ORDER BY role_cd",
                            (rs, row) -> rs.getString("role_cd"), userId);
                    context.getClaims().claim("roles", roles);
                } catch (IllegalArgumentException ignored) {
                    // 주체가 계정 UUID가 아닌 토큰(예: 클라이언트 자체 인증)에는 계정 권한을 넣지 않습니다.
                }
            }
        };
    }

    /** 인가 코드와 토큰을 DB에 저장해 서버를 재시작해도 활성 인가 상태를 유지합니다. */
    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    /** 사용자가 클라이언트에 허용한 scope를 DB에 저장합니다. */
    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    /**
     * 토큰 서명에 사용할 RSA 키를 PKCS12 파일에서 읽습니다. 인증서 지문을 JWK ID로 사용해
     * 재시작 후에도 같은 공개 키 식별자를 제공하고, 기존 토큰 검증을 이어갈 수 있게 합니다.
     */
    @Bean
    JWKSource<SecurityContext> jwkSource(
            @Value("${dagong.auth.keystore-path}") String keystorePath,
            @Value("${dagong.auth.keystore-password}") String keystorePassword,
            @Value("${dagong.auth.keystore-alias}") String keyAlias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(Path.of(keystorePath))) {
            keyStore.load(input, keystorePassword.toCharArray());
        }

        PrivateKey privateKey = (PrivateKey) keyStore.getKey(keyAlias, keystorePassword.toCharArray());
        X509Certificate certificate = (X509Certificate) keyStore.getCertificate(keyAlias);
        if (privateKey == null || certificate == null) {
            throw new IllegalStateException("Signing key alias is missing from PKCS12 keystore: " + keyAlias);
        }

        String keyId = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded()));
        RSAKey key = new RSAKey.Builder((RSAPublicKey) certificate.getPublicKey())
                .privateKey((RSAPrivateKey) privateKey)
                .keyID(keyId)
                .build();
        JWKSet keySet = new JWKSet(key);

        return (selector, context) -> selector.select(keySet);
    }

    /** 검색 문서와 발급 토큰에 표시할 인증 서버 issuer 주소를 설정합니다. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${dagong.auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder()
                .issuer(issuer)
                .build();
    }
}
