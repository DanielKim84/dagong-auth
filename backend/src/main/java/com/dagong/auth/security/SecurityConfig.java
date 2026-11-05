package com.dagong.auth.security;

import com.dagong.auth.account.ActiveAccountChecker;
import com.dagong.auth.device.DeviceLoginService;
import com.dagong.auth.device.SingleDeviceLoginFilter;
import java.net.URI;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;

/**
 * 보안 필터 체인을 설정합니다: 맛다공 API(bearer token), OAuth2·OIDC 인가 서버 엔드포인트, 로그인 화면.
 * 클라이언트 등록은 {@link ClientRegistrationConfig}, 서명 키는 {@link SigningKeyConfig}에 있습니다.
 */
@Configuration
public class SecurityConfig {
    /**
     * 이 서버가 발급한 맛다공 API bearer token을 issuer의 JWKS 공개키(RS256), issuer, audience로 검증합니다.
     * audience에 맛다공 client_id가 없거나, 다른 기기 로그인·로그아웃·탈퇴로 인가 기록이 지워진 토큰이면
     * 컨트롤러에 닿기 전에 401로 거부합니다.
     */
    @Bean
    JwtDecoder jwtDecoder(
            @Value("${dagong.auth.issuer}") String issuer,
            @Value("${dagong.auth.matdagong-client-id}") String clientId,
            JdbcTemplate jdbc) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/oauth2/jwks")
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new AudienceValidator(clientId),
                new ActiveAuthorizationValidator(jdbc)));
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

    /**
     * OAuth2·OIDC 엔드포인트를 보호하고 미인증 요청을 공통 로그인 화면으로 보냅니다.
     * 맛다공 인가 요청에는 한 기기 로그인 필터를 인가 엔드포인트보다 앞에 둡니다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            ActiveAccountChecker activeAccountChecker,
            DeviceLoginService deviceLogins,
            @Value("${dagong.auth.matdagong-client-id}") String clientId,
            @Value("${dagong.auth.login-page-url}") String loginPageUrl)
            throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();

        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, server -> server.oidc(Customizer.withDefaults())
                        // 인가 요청 검증기를 교체해 등록된 localhost redirect_uri를 허용하고 계정 상태를 확인합니다.
                        .authorizationEndpoint(endpoint ->
                                endpoint.authenticationProviders(providers -> providers.forEach(provider -> {
                                    if (provider
                                            instanceof
                                            OAuth2AuthorizationCodeRequestAuthenticationProvider
                                            codeProvider) {
                                        codeProvider.setAuthenticationValidator(
                                                authorizationRequestValidator(activeAccountChecker));
                                    }
                                }))))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // 로그인 사용자를 읽은 직후, 인가 코드를 발급하는 엔드포인트 필터보다 먼저 실행됩니다.
                .addFilterAfter(
                        new SingleDeviceLoginFilter(deviceLogins, clientId, loginPageUrl),
                        SecurityContextHolderFilter.class)
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(loginPageUrl)));

        return http.build();
    }

    /** React 로그인 화면과 정적 파일은 공개하고 나머지 애플리케이션 경로는 보호합니다. */
    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            SocialOAuth2UserService socialOAuth2UserService,
            @Value("${dagong.auth.login-page-url}") String loginPageUrl,
            @Value("${dagong.auth.login-success-url}") String loginSuccessUrl,
            @Value("${dagong.auth.login-failure-url}") String loginFailureUrl)
            throws Exception {
        // 세션 정리 API는 CSRF 토큰을 받을 수 없는 맛다공 SPA가 프록시(/auth-api)로 호출하므로 CSRF 검사에서 제외합니다.
        // 한 기기 로그인 확인·취소도 CSRF 토큰이 없는 로그인 화면이 부릅니다. 둘 다 이 세션에 확인을 기다리는 로그인이
        // 있을 때만 동작하고, 그 로그인을 계속하거나 그만두는 일만 하므로 다른 사이트가 불러도 얻는 것이 없습니다.
        http.csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/auth/session/clear", "/auth/device/confirm", "/auth/device/cancel"))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/login", "/error", "/auth/**", "/oauth2/authorization/**", "/login/oauth2/code/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(
                        ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(loginPageUrl)))
                .oauth2Login(oauth2 -> oauth2.loginPage(loginPageUrl)
                        // 저장된 OAuth 인가 요청이 있으면 로그인 뒤 이어서 처리하고, 단독 로그인은 화면으로 돌아옵니다.
                        .defaultSuccessUrl(loginSuccessUrl, false)
                        .failureUrl(loginFailureUrl)
                        .userInfoEndpoint(userInfo -> userInfo.userService(socialOAuth2UserService)));

        return http.build();
    }

    /** 검색 문서와 발급 토큰에 표시할 인증 서버 issuer 주소를 설정합니다. */
    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${dagong.auth.issuer}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
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
        // 로그인 전 요청은 소셜 로그인 단계에서 상태를 확인하므로, 인증된 사용자 요청만 검사합니다.
        Consumer<OAuth2AuthorizationCodeRequestAuthenticationContext> accountStatusValidator = context -> {
            OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest = context.getAuthentication();
            if (!(authorizationRequest.getPrincipal() instanceof Authentication principal)
                    || principal instanceof AnonymousAuthenticationToken
                    || !principal.isAuthenticated()) {
                return;
            }
            if (!activeAccountChecker.isActive(principal.getName())) {
                // 오류 코드는 OAuth2 표준 값(access_denied)을 그대로 씁니다.
                throw new OAuth2AuthorizationCodeRequestAuthenticationException(
                        new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, "이용이 제한된 계정입니다.", null), authorizationRequest);
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
}
