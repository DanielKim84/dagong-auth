package com.dagong.auth.security;

import com.dagong.auth.account.AccountStatus;
import com.dagong.auth.account.SocialAccountService;
import com.dagong.auth.account.SocialProvider;
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

/**
 * 카카오·네이버 userinfo 응답에서 회원 고유번호와 별명을 꺼내 공통 계정과 연결한 뒤 UUID 기반 인증 주체를 반환합니다.
 * 같은 사람이라도 제공자가 다르면 다른 계정입니다. 이메일로 계정을 합치지 않습니다.
 */
@Service
public class SocialOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final SocialAccountService socialAccountService;

    public SocialOAuth2UserService(SocialAccountService socialAccountService) {
        this.socialAccountService = socialAccountService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        SocialProvider provider = SocialProvider.fromRegistrationId(
                        request.getClientRegistration().getRegistrationId())
                .orElseThrow(() -> new OAuth2AuthenticationException(
                        new OAuth2Error("unsupported_social_provider", "지원하지 않는 소셜 로그인입니다.", null)));

        Map<String, Object> attributes = delegate.loadUser(request).getAttributes();
        SocialProfile profile =
                switch (provider) {
                    case KAKAO -> kakaoProfile(attributes);
                    case NAVER -> naverProfile(attributes);
                };
        if (profile.providerUserId() == null || profile.providerUserId().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("social_user_id_missing", "소셜 회원번호를 받지 못했습니다.", null));
        }

        SocialAccountService.AccountIdentity identity =
                socialAccountService.authenticate(provider, profile.providerUserId(), profile.nickname());
        if (identity.status() != AccountStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_not_active", "이용이 제한된 계정입니다.", null));
        }

        // Authorization DB에는 원본 소셜 attributes(회원번호 등)를 저장하지 않고 공통 계정 ID만 둡니다.
        // 이 값들은 토큰 발급 뒤 필요하지 않으며, 저장된 인증 정보를 안전하게 복원할 수 있게 합니다.
        // 계정 UUID가 인증 주체 이름이 되어 토큰 sub와 계정 상태·권한 조회에 그대로 쓰입니다.
        // 서비스 권한은 토큰 발급 때 account_roles에서 읽으므로 로그인 주체에는 권한을 두지 않습니다.
        Map<String, Object> accountAttributes =
                Map.of("dagong_account_id", identity.accountId().toString());
        return new DefaultOAuth2User(List.of(), accountAttributes, "dagong_account_id");
    }

    /** 카카오: {"id": 123, "kakao_account": {"profile": {"nickname": …}}, "properties": {"nickname": …}} */
    private static SocialProfile kakaoProfile(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = mapValue(attributes.get("kakao_account"));
        Map<String, Object> profile = mapValue(kakaoAccount.get("profile"));
        Map<String, Object> properties = mapValue(attributes.get("properties"));
        return new SocialProfile(
                stringValue(attributes.get("id")),
                firstNonBlank(stringValue(profile.get("nickname")), stringValue(properties.get("nickname"))));
    }

    /** 네이버: {"resultcode": "00", "response": {"id": …, "nickname": …, "name": …}} */
    private static SocialProfile naverProfile(Map<String, Object> attributes) {
        Map<String, Object> response = mapValue(attributes.get("response"));
        return new SocialProfile(
                stringValue(response.get("id")),
                firstNonBlank(stringValue(response.get("nickname")), stringValue(response.get("name"))));
    }

    private record SocialProfile(String providerUserId, String nickname) {}

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
