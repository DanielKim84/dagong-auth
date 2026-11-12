package com.dagong.auth.account;

import java.util.Arrays;
import java.util.Optional;

/** 로그인에 쓰는 소셜 제공자입니다. code는 social_identities.provider_cd에 저장하는 값입니다. */
public enum SocialProvider {
    KAKAO("kakao"),
    NAVER("naver");

    /** application.yml의 spring.security.oauth2.client.registration 이름 */
    private final String registrationId;

    SocialProvider(String registrationId) {
        this.registrationId = registrationId;
    }

    public String code() {
        return name();
    }

    public static Optional<SocialProvider> fromRegistrationId(String registrationId) {
        return Arrays.stream(values())
                .filter(provider -> provider.registrationId.equals(registrationId))
                .findFirst();
    }
}
