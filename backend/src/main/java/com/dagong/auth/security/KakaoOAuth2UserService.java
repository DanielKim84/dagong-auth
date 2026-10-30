package com.dagong.auth.security;

import com.dagong.auth.account.AccountStatus;
import com.dagong.auth.account.SocialAccountService;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/** Kakao userinfo 응답을 공통 계정과 연결한 뒤 UUID 기반 인증 주체를 반환합니다. */
@Service
public class KakaoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final SocialAccountService socialAccountService;

    public KakaoOAuth2UserService(SocialAccountService socialAccountService) {
        this.socialAccountService = socialAccountService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        if (!"kakao".equals(request.getClientRegistration().getRegistrationId())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("unsupported_social_provider"));
        }

        OAuth2User kakaoUser = delegate.loadUser(request);
        Map<String, Object> attributes = kakaoUser.getAttributes();
        String providerUserId = stringValue(attributes.get("id"));
        if (providerUserId == null || providerUserId.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("kakao_user_id_missing"));
        }

        Map<String, Object> kakaoAccount = mapValue(attributes.get("kakao_account"));
        Map<String, Object> profile = mapValue(kakaoAccount.get("profile"));
        Map<String, Object> properties = mapValue(attributes.get("properties"));
        String nickname = firstNonBlank(
                stringValue(profile.get("nickname")),
                stringValue(properties.get("nickname")));

        SocialAccountService.AccountIdentity identity = socialAccountService.authenticateKakao(providerUserId, nickname);
        if (identity.status() != AccountStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_not_active"));
        }

        // Authorization DB에는 원본 Kakao attributes(숫자형 ID 등)를 저장하지 않고 공통 계정 ID만 둡니다.
        // 이 값들은 토큰 발급 뒤 필요하지 않으며, 저장된 인증 정보를 안전하게 복원할 수 있게 합니다.
        // 계정 UUID가 인증 주체 이름이 되어 토큰 sub와 계정 상태·권한 조회에 그대로 쓰입니다.
        // 서비스 권한은 토큰 발급 때 account_roles에서 읽으므로 로그인 주체에는 권한을 두지 않습니다.
        Map<String, Object> accountAttributes = Map.of("dagong_account_id", identity.accountId().toString());
        return new DefaultOAuth2User(List.of(), accountAttributes, "dagong_account_id");
    }

    private static Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedMap = (Map<String, Object>) map;
            return typedMap;
        }
        return Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}
