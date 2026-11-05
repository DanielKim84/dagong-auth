package com.dagong.auth.account;

import java.util.Arrays;
import java.util.Optional;

/** 로그인에 쓰는 소셜 제공자입니다. code는 social_identities.provider_cd에 저장하는 값입니다. */
public enum SocialProvider {
    KAKAO("kakao", "카카오 사용자"),
    NAVER("naver", "네이버 사용자");

    /** application.yml의 spring.security.oauth2.client.registration 이름 */
    private final String registrationId;
    /** 제공자가 별명을 주지 않았을 때 쓰는 이름 */
    private final String defaultDisplayName;

    SocialProvider(String registrationId, String defaultDisplayName) {
        this.registrationId = registrationId;
        this.defaultDisplayName = defaultDisplayName;
    }

    public String code() {
        return name();
    }

    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    public static Optional<SocialProvider> fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equals(registrationId))
                .findFirst();
    }
}
