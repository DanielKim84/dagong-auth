package com.dagong.auth.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.dagong.auth.support.TestDatabase;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 PostgreSQL에서 카카오·네이버 회원번호로 공통 계정을 찾고 만드는 규칙을 확인합니다. */
class SocialAccountServiceIntegrationTest {
    private static TestDatabase database;
    private SocialAccountService service;

    @BeforeAll
    static void startDatabase() throws IOException {
        database = TestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() throws IOException {
        if (database != null) database.close();
    }

    @BeforeEach
    void setUp() {
        database.truncateAccounts();
        service = database.transactional(new SocialAccountService(database.jdbc()));
    }

    @Test
    void 같은_제공자의_같은_회원번호는_같은_계정으로_로그인한다() {
        var first = service.authenticate(SocialProvider.NAVER, "naver-1", "네이버맛객");
        var again = service.authenticate(SocialProvider.NAVER, "naver-1", "바뀐별명");

        assertThat(again.accountId()).isEqualTo(first.accountId());
        assertThat(again.status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void 제공자가_다르면_회원번호가_같아도_다른_계정이다() {
        var kakao = service.authenticate(SocialProvider.KAKAO, "same-id", "카카오맛객");
        var naver = service.authenticate(SocialProvider.NAVER, "same-id", "네이버맛객");

        assertThat(naver.accountId()).isNotEqualTo(kakao.accountId());
        assertThat(database.jdbc()
                        .queryForObject(
                                "SELECT provider_cd FROM social_identities WHERE user_id = ?",
                                String.class,
                                naver.accountId()))
                .isEqualTo("NAVER");
    }

    @Test
    void 별명을_받지_못하면_제공자별_기본_이름을_쓴다() {
        var naver = service.authenticate(SocialProvider.NAVER, "naver-2", " ");

        assertThat(database.jdbc()
                        .queryForObject(
                                "SELECT user_nm FROM accounts WHERE user_id = ?", String.class, naver.accountId()))
                .isEqualTo("네이버 사용자");
    }
}
