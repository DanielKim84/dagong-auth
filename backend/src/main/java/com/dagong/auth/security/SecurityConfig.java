package com.dagong.auth.security;

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
import java.util.Base64;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
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
    /** 자체 발급한 서명키와 issuer로 맛다공 API bearer token을 검증합니다. */
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

    /** 인증 서버 엔드포인트 보안을 적용하고, OAuth 클라이언트 등록을 서버 시작 단계에서 완료합니다. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            @Value("${dagong.auth.login-page-url:/login}") String loginPageUrl) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, server -> server
                        .oidc(Customizer.withDefaults())
                        // 기본 검증기는 redirect_uri의 localhost 호스트를 거부하므로 등록된 주소와 정확히 같을 때만 허용합니다.
                        .authorizationEndpoint(endpoint -> endpoint.authenticationProviders(providers ->
                                providers.forEach(provider -> {
                                    if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeProvider) {
                                        codeProvider.setAuthenticationValidator(authorizationRequestValidator());
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
            @Value("${dagong.auth.login-page-url:/login}") String loginPageUrl,
            @Value("${dagong.auth.login-success-url:/login}") String loginSuccessUrl,
            @Value("${dagong.auth.login-failure-url:/login?error}") String loginFailureUrl) throws Exception {
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
     */
    private static Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> authorizationRequestValidator() {
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
        return redirectUriValidator.andThen(OAuth2AuthorizationCodeRequestAuthenticationValidator.DEFAULT_SCOPE_VALIDATOR);
    }

    private static boolean isLocalhost(String uri) {
        try {
            return "localhost".equalsIgnoreCase(URI.create(uri).getHost());
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 개발용 맛다공 공개 클라이언트를 최초 한 번 등록하고 callback 주소를 설정값과 동기화합니다.
     * 브라우저 공개 클라이언트는 Authorization Code + PKCE를 사용하며 client secret을 저장하지 않습니다.
     */
    @Bean
    RegisteredClientRepository registeredClientRepository(
            JdbcOperations jdbcOperations,
            @Value("${dagong.auth.matdagong-client-id:matdagong-web-dev}") String clientId,
            @Value("${dagong.auth.matdagong-redirect-uri:http://localhost:4845/auth/callback}")
                    String redirectUri) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcOperations);

        RegisteredClient existingClient = repository.findByClientId(clientId);
        if (existingClient == null) {
            RegisteredClient matdagong = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(clientId)
                    .clientName("Matdagong Web (development)")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(redirectUri)
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            // 맛다공은 공통계정의 공식 서비스이므로 기본 scope 동의 화면 없이 callback으로 이어갑니다.
                            .requireAuthorizationConsent(false)
                            .build())
                    .build();
            repository.save(matdagong);
        } else {
            // 기존 DB에 저장된 클라이언트 설정도 동의 화면 생략과 PKCE 필수 정책으로 맞춥니다.
            ClientSettings clientSettings = ClientSettings.withSettings(existingClient.getClientSettings().getSettings())
                    .requireProofKey(true)
                    .requireAuthorizationConsent(false)
                    .build();
            repository.save(RegisteredClient.from(existingClient).clientSettings(clientSettings).build());
        }

        // save()는 신규 행을 추가하므로, 이미 등록된 클라이언트의 callback 주소는 SQL로 갱신합니다.
        jdbcOperations.update(
                "UPDATE oauth2_registered_client SET redirect_uris = ? WHERE client_id = ?",
                redirectUri,
                clientId);

        return repository;
    }

    /** 맛다공 API가 토큰을 다른 서비스의 토큰과 구분할 수 있도록 access token audience를 지정합니다. */
    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> accessTokenAudienceCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                context.getClaims().audience(java.util.List.of(context.getRegisteredClient().getClientId()));
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
            @Value("${dagong.auth.keystore-path:${user.home}/.dagong-auth/auth-signing.p12}") String keystorePath,
            @Value("${dagong.auth.keystore-password}") String keystorePassword,
            @Value("${dagong.auth.keystore-alias:dagong-auth-signing}") String keyAlias) throws Exception {
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
