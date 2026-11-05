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

/** 실제 PostgreSQL에서 맛다공 가입 확인·가입·가입 취소 SQL과 상태별 응답을 확인합니다. */
class MatdagongMembershipServiceIntegrationTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID BLOCKED_ACCOUNT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String MATDAGONG_CLIENT_ID = "matdagong-web-test";

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
        service = database.transactional(new MatdagongMembershipService(
                database.jdbc(), new ActiveAccountChecker(database.jdbc()), MATDAGONG_CLIENT_ID));
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
    void 가입_상태에_연결된_소셜_제공자를_함께_알려준다() {
        assertThat(service.status(ACCOUNT).loginProviders()).isEmpty();
        database.jdbc()
                .update(
                        """
                        INSERT INTO social_identities (social_id, user_id, provider_cd, provider_uid, reg_dt, last_auth_dt)
                        VALUES (?, ?, 'NAVER', 'naver-1', now(), now())
                        """,
                        UUID.randomUUID(),
                        ACCOUNT);

        assertThat(service.status(ACCOUNT).loginProviders()).containsExactly("NAVER");
    }

    @Test
    void 탈퇴한_서비스는_다시_가입할_수_있다() {
        insertService(ACCOUNT, ServiceStatus.WITHDRAWN);

        service.join(ACCOUNT, new JoinRequest("돌아온맛객", null));

        assertThat(service.status(ACCOUNT).status()).isEqualTo(ServiceStatus.ACTIVE);
    }

    @Test
    void 가입을_취소하면_미가입으로_보이고_공통_계정은_남으며_다시_가입할_수_있다() {
        service.join(ACCOUNT, new JoinRequest("떠날맛객", null));

        service.cancel(ACCOUNT);
        // 이미 취소한 뒤 다시 요청해도 성공으로 봅니다.
        service.cancel(ACCOUNT);

        MembershipStatus cancelled = service.status(ACCOUNT);
        assertThat(cancelled.status()).isEqualTo(ServiceStatus.WITHDRAWN);
        assertThat(cancelled.registered()).isFalse();
        assertThat(database.jdbc()
                        .queryForObject(
                                "SELECT del_dt IS NOT NULL FROM account_services WHERE user_id = ?",
                                Boolean.class,
                                ACCOUNT))
                .isTrue();
        assertThat(database.jdbc()
                        .queryForObject("SELECT user_st FROM accounts WHERE user_id = ?", String.class, ACCOUNT))
                .isEqualTo(AccountStatus.ACTIVE.name());

        service.join(ACCOUNT, new JoinRequest("돌아온맛객", null));
        assertThat(service.status(ACCOUNT).registered()).isTrue();
    }

    @Test
    void 가입을_취소하면_그_계정의_맛다공_로그인_기록만_지워진다() {
        var jdbc = database.jdbc();
        jdbc.update("DELETE FROM oauth2_authorization");
        jdbc.update("DELETE FROM oauth2_registered_client");
        insertClient("client-matdagong", MATDAGONG_CLIENT_ID);
        insertClient("client-other", "yeodagong-web-test");
        insertAuthorization("auth-mine", "client-matdagong", ACCOUNT);
        insertAuthorization("auth-other-service", "client-other", ACCOUNT);
        insertAuthorization("auth-other-account", "client-matdagong", BLOCKED_ACCOUNT);
        service.join(ACCOUNT, new JoinRequest("떠날맛객", null));

        service.cancel(ACCOUNT);

        assertThat(jdbc.queryForList("SELECT id FROM oauth2_authorization ORDER BY id", String.class))
                .containsExactly("auth-other-account", "auth-other-service");
    }

    @Test
    void 가입_이력이_없어도_취소는_성공하고_맛다공_차단은_취소로_풀리지_않는다() {
        service.cancel(ACCOUNT);
        assertThat(service.status(ACCOUNT).status()).isNull();

        insertService(ACCOUNT, ServiceStatus.BLOCKED);
        assertForbidden(() -> service.cancel(ACCOUNT), "맛다공 이용이 제한된 계정입니다.");
        assertThat(database.jdbc()
                        .queryForObject("SELECT svc_st FROM account_services WHERE user_id = ?", String.class, ACCOUNT))
                .isEqualTo(ServiceStatus.BLOCKED.name());
        assertForbidden(() -> service.cancel(BLOCKED_ACCOUNT), "이용이 제한된 계정입니다.");
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

    private static void insertClient(String id, String clientId) {
        database.jdbc()
                .update(
                        """
                        INSERT INTO oauth2_registered_client (id, client_id, client_name, client_authentication_methods,
                            authorization_grant_types, scopes, client_settings, token_settings)
                        VALUES (?, ?, ?, 'none', 'authorization_code', 'openid', '{}', '{}')
                        """,
                        id,
                        clientId,
                        clientId);
    }

    private static void insertAuthorization(String id, String registeredClientId, UUID accountId) {
        database.jdbc()
                .update(
                        """
                        INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type)
                        VALUES (?, ?, ?, 'authorization_code')
                        """,
                        id,
                        registeredClientId,
                        accountId.toString());
    }
}
