package com.dagong.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dagong.auth.account.MembershipModels.JoinRequest;
import com.dagong.auth.account.MembershipModels.MembershipStatus;
import com.dagong.auth.support.TestDatabase;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 실제 PostgreSQL에서 맛다공 가입 확인·가입 SQL과 상태별 응답을 확인합니다. */
class MatdagongMembershipServiceIntegrationTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BLOCKED_ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static TestDatabase database;
    private MatdagongMembershipService service;

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
        database.insertAccount(ACCOUNT, AccountStatus.ACTIVE);
        database.insertAccount(BLOCKED_ACCOUNT, AccountStatus.BLOCKED);
        service = database.transactional(
                new MatdagongMembershipService(database.jdbc(), new ActiveAccountChecker(database.jdbc())));
    }

    @Test
    void 가입_전에는_미가입이고_가입하면_닉네임과_이메일이_저장된다() {
        MembershipStatus before = service.status(ACCOUNT);
        assertThat(before.status()).isNull();
        assertThat(before.registered()).isFalse();

        assertThat(service.join(ACCOUNT, new JoinRequest("  새맛객 ", " taster@example.com "))
                        .joined())
                .isTrue();
        // 중복 제출도 성공으로 봅니다.
        assertThat(service.join(ACCOUNT, new JoinRequest("새맛객", null)).joined()).isTrue();

        MembershipStatus after = service.status(ACCOUNT);
        assertThat(after.displayName()).isEqualTo("새맛객");
        assertThat(after.email()).isNull();
        assertThat(after.status()).isEqualTo(ServiceStatus.ACTIVE);
        assertThat(after.registered()).isTrue();
    }

    @Test
    void 탈퇴한_서비스는_다시_가입할_수_있다() {
        insertService(ACCOUNT, ServiceStatus.WITHDRAWN);

        service.join(ACCOUNT, new JoinRequest("돌아온맛객", null));

        assertThat(service.status(ACCOUNT).status()).isEqualTo(ServiceStatus.ACTIVE);
    }

    @Test
    void 맛다공_이용이_차단되면_확인과_가입이_403이고_입력은_저장되지_않는다() {
        insertService(ACCOUNT, ServiceStatus.BLOCKED);

        assertForbidden(() -> service.status(ACCOUNT), "맛다공 이용이 제한된 계정입니다.");
        assertForbidden(() -> service.join(ACCOUNT, new JoinRequest("바뀐이름", null)), "맛다공 이용이 제한된 계정입니다.");
        assertThat(database.jdbc()
                        .queryForObject("SELECT user_nm FROM accounts WHERE user_id = ?", String.class, ACCOUNT))
                .isEqualTo("맛객");
    }

    @Test
    void 계정_전체가_차단되면_403이다() {
        assertForbidden(() -> service.status(BLOCKED_ACCOUNT), "이용이 제한된 계정입니다.");
        assertForbidden(() -> service.join(BLOCKED_ACCOUNT, new JoinRequest("맛객", null)), "이용이 제한된 계정입니다.");
    }

    @Test
    void 맛다공_이용이_차단돼도_진입_시각은_남긴다() {
        insertService(ACCOUNT, ServiceStatus.BLOCKED);

        assertForbidden(() -> service.status(ACCOUNT), "맛다공 이용이 제한된 계정입니다.");

        assertThat(database.jdbc()
                        .queryForObject(
                                "SELECT last_access_dt IS NOT NULL FROM account_services WHERE user_id = ?",
                                Boolean.class,
                                ACCOUNT))
                .isTrue();
    }

    private static void insertService(UUID accountId, ServiceStatus status) {
        database.jdbc()
                .update(
                        """
                        INSERT INTO account_services (membership_id, user_id, svc_cd, svc_st, del_dt)
                        VALUES (?, ?, 'MATDAGONG', ?, CASE WHEN ? = 'WITHDRAWN' THEN now() END)
                        """,
                        UUID.randomUUID(),
                        accountId,
                        status.name(),
                        status.name());
    }

    private static void assertForbidden(Runnable action, String reason) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(exception.getReason()).isEqualTo(reason);
        });
    }
}
