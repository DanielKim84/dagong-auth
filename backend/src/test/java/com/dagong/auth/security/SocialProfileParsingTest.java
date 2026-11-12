package com.dagong.auth.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 카카오·네이버 userinfo 응답에서 별명과 프로필 사진 주소를 꺼내는 규칙을 확인합니다. */
class SocialProfileParsingTest {

    @Test
    void 카카오는_동의한_사진을_https로_받고_기본_사진은_버린다() {
        var withPhoto = SocialOAuth2UserService.kakaoProfile(Map.of(
                "id",
                123L,
                "kakao_account",
                Map.of(
                        "profile",
                        Map.of(
                                "nickname", "카카오맛객",
                                "profile_image_url", "http://k.kakaocdn.net/dn/a/img_640x640.jpg",
                                "is_default_image", false))));
        assertThat(withPhoto.providerUserId()).isEqualTo("123");
        assertThat(withPhoto.nickname()).isEqualTo("카카오맛객");
        assertThat(withPhoto.profileImageUrl()).isEqualTo("https://k.kakaocdn.net/dn/a/img_640x640.jpg");

        var defaultPhoto = SocialOAuth2UserService.kakaoProfile(Map.of(
                "id",
                123L,
                "kakao_account",
                Map.of(
                        "profile",
                        Map.of("profile_image_url", "https://k.kakaocdn.net/default.jpg", "is_default_image", true))));
        assertThat(defaultPhoto.profileImageUrl()).isNull();

        // 사진 동의를 하지 않으면 profile_image_url이 오지 않습니다.
        var noConsent = SocialOAuth2UserService.kakaoProfile(
                Map.of("id", 123L, "kakao_account", Map.of("profile", Map.of("nickname", "카카오맛객"))));
        assertThat(noConsent.profileImageUrl()).isNull();
    }

    @Test
    void 네이버는_기본_그림을_버리고_올린_사진만_받는다() {
        var withPhoto = SocialOAuth2UserService.naverProfile(Map.of(
                "response",
                Map.of("id", "n-1", "nickname", "네이버맛객", "profile_image", "https://phinf.pstatic.net/contact/a.jpg")));
        assertThat(withPhoto.profileImageUrl()).isEqualTo("https://phinf.pstatic.net/contact/a.jpg");

        var defaultPhoto = SocialOAuth2UserService.naverProfile(Map.of(
                "response",
                Map.of("id", "n-1", "profile_image", "https://ssl.pstatic.net/static/pwe/address/img_profile.png")));
        assertThat(defaultPhoto.profileImageUrl()).isNull();
    }

    @Test
    void 사진_주소는_https만_남기고_너무_길면_버린다() {
        assertThat(SocialOAuth2UserService.profileImageUrl(" ")).isNull();
        assertThat(SocialOAuth2UserService.profileImageUrl("ftp://k.kakaocdn.net/a.jpg"))
                .isNull();
        assertThat(SocialOAuth2UserService.profileImageUrl("https://k.kakaocdn.net/" + "a".repeat(1000)))
                .isNull();
    }
}
