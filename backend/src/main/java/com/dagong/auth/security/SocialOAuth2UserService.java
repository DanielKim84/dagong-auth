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
 * 카카오·네이버 userinfo 응답에서 회원 고유번호·별명·프로필 사진 주소를 꺼내 공통 계정과 연결한 뒤 UUID 기반 인증 주체를 반환합니다.
 * 같은 사람이라도 제공자가 다르면 다른 계정입니다. 이메일로 계정을 합치지 않습니다.
 */
@Service
public class SocialOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    /** 네이버가 프로필 사진이 없는 회원에게 주는 기본 그림 주소의 경로입니다. */
    private static final String NAVER_DEFAULT_IMAGE_PATH = "/static/pwe/address/img_profile.png";

    /** social_identities.profile_img_url 칸 길이입니다. */
    private static final int MAX_IMAGE_URL_LENGTH = 1000;

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

        SocialAccountService.AccountIdentity identity = socialAccountService.authenticate(
                provider, profile.providerUserId(), profile.nickname(), profile.profileImageUrl());
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

    /**
     * 카카오: {"id": 123, "kakao_account": {"profile": {"nickname": …, "profile_image_url": …, "is_default_image": …}}}.
     * 별명·사진은 각 동의항목(profile_nickname·profile_image)에 동의했을 때만 오고, 카카오 기본 사진이면 쓰지 않습니다.
     */
    static SocialProfile kakaoProfile(Map<String, Object> attributes) {
        Map<String, Object> kakaoAccount = mapValue(attributes.get("kakao_account"));
        Map<String, Object> profile = mapValue(kakaoAccount.get("profile"));
        String imageUrl = Boolean.TRUE.equals(profile.get("is_default_image"))
                ? null
                : stringValue(profile.get("profile_image_url"));
        return new SocialProfile(
                stringValue(attributes.get("id")), stringValue(profile.get("nickname")), profileImageUrl(imageUrl));
    }

    /**
     * 네이버: {"resultcode": "00", "response": {"id": …, "nickname": …, "profile_image": …}}. 별명·사진은 네이버
     * Developers 제공 정보에서 고르고 사용자가 동의했을 때만 오며, 사진이 없으면 네이버 기본 그림이 와서 쓰지 않습니다.
     */
    static SocialProfile naverProfile(Map<String, Object> attributes) {
        Map<String, Object> response = mapValue(attributes.get("response"));
        String imageUrl = stringValue(response.get("profile_image"));
        return new SocialProfile(
                stringValue(response.get("id")),
                stringValue(response.get("nickname")),
                imageUrl != null && imageUrl.contains(NAVER_DEFAULT_IMAGE_PATH) ? null : profileImageUrl(imageUrl));
    }

    /**
     * 저장할 사진 주소입니다. 맛다공 서버가 https 주소만 내려받으므로 http는 https로 바꿉니다(카카오 CDN은 둘 다 받음).
     * 비었거나 그 밖의 주소, 칸보다 긴 주소는 버립니다.
     */
    static String profileImageUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String secure = url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
        return secure.startsWith("https://") && secure.length() <= MAX_IMAGE_URL_LENGTH ? secure : null;
    }

    /** 별명이 비면 계정 이름을 비워 두고, 서비스 가입 화면에서 사용자가 정합니다(SocialAccountService). */
    record SocialProfile(String providerUserId, String nickname, String profileImageUrl) {}

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
}
