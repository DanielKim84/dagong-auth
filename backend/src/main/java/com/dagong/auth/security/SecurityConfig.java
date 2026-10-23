package com.dagong.auth.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.InputStream;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

/** OAuth2/OIDC 엔드포인트, 클라이언트, 인가 저장소와 토큰 서명 키를 설정합니다. */
@Configuration
public class SecurityConfig {
    /** 인증 서버 엔드포인트에 전용 보안 규칙을 적용합니다. */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/login")));

        return http.build();
    }

    /** React 로그인 화면과 정적 파일은 공개하고 나머지 애플리케이션 경로는 보호합니다. */
    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/auth/**").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(
                        new LoginUrlAuthenticationEntryPoint("/login")));

        return http.build();
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

        if (repository.findByClientId(clientId) == null) {
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
                            .requireAuthorizationConsent(true)
                            .build())
                    .build();
            repository.save(matdagong);
        }

        // save()는 신규 행을 추가하므로, 이미 등록된 클라이언트의 callback 주소는 SQL로 갱신합니다.
        jdbcOperations.update(
                "UPDATE oauth2_registered_client SET redirect_uris = ? WHERE client_id = ?",
                redirectUri,
                clientId);

        return repository;
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
