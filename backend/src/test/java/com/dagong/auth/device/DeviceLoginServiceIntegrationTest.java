package com.dagong.auth.device;

import static org.assertj.core.api.Assertions.assertThat;

import com.dagong.auth.support.TestDatabase;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 실제 PostgreSQL에서 같은 종류의 기기는 하나만 로그인하고, 다른 종류와 로그아웃한 기기는 묻지 않는지 확인합니다. */
class DeviceLoginServiceIntegrationTest {
    private static final UUID ACCOUNT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Device PHONE_A = new Device(UUID.randomUUID().toString(), DeviceType.MOBILE);
    private static final Device PHONE_B = new Device(UUID.randomUUID().toString(), DeviceType.MOBILE);
    private static final Device LAPTOP = new Device(UUID.randomUUID().toString(), DeviceType.PC);

    private static TestDatabase database;
    private DeviceLoginService logins;

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
        database.jdbc().update("TRUNCATE oauth2_authorization, matdagong_device_logins, matdagong_device_signouts");
        database.jdbc().update("TRUNCATE oauth2_registered_client CASCADE");
        database.jdbc()
                .update(
                        """
                        INSERT INTO oauth2_registered_client (
                            id, client_id, client_name, client_authentication_methods, authorization_grant_types,
                            scopes, client_settings, token_settings
                        ) VALUES ('client', 'matdagong-web-dev', '맛다공', 'none', 'authorization_code', 'openid', '{}', '{}')
                        """);
        logins = database.transactional(new DeviceLoginService(database.jdbc()));
    }

    @Test
    void 다른_휴대폰에_로그인이_남아_있으면_묻고_끊으면_그_휴대폰에_안내를_남긴다() {
        signIn("phone-a", PHONE_A, true);

        assertThat(logins.hasOtherActiveLogin(ACCOUNT, PHONE_B)).isTrue();
        // 같은 휴대폰(다른 탭 등)과 PC는 묻지 않습니다.
        assertThat(logins.hasOtherActiveLogin(ACCOUNT, PHONE_A)).isFalse();
        assertThat(logins.hasOtherActiveLogin(ACCOUNT, LAPTOP)).isFalse();

        logins.endOtherLogins(ACCOUNT, PHONE_B);

        assertThat(authorizationExists("phone-a")).isFalse();
        assertThat(logins.hasOtherActiveLogin(ACCOUNT, PHONE_B)).isFalse();
        assertThat(logins.consumeSignout(PHONE_A.id())).isTrue();
        assertThat(logins.consumeSignout(PHONE_A.id())).as("안내는 한 번만 보입니다").isFalse();
    }

    @Test
    void 휴대폰에서_로그인해도_PC_로그인은_그대로_둔다() {
        signIn("laptop", LAPTOP, true);
        signIn("phone-a", PHONE_A, true);

        logins.endOtherLogins(ACCOUNT, PHONE_B);

        assertThat(authorizationExists("laptop")).isTrue();
        assertThat(authorizationExists("phone-a")).isFalse();
        assertThat(logins.consumeSignout(LAPTOP.id())).isFalse();
    }

    @Test
    void 만료된_로그인은_묻지_않고_안내_없이_정리한다() {
        signIn("phone-a", PHONE_A, false);

        assertThat(logins.hasOtherActiveLogin(ACCOUNT, PHONE_B)).isFalse();
        logins.endOtherLogins(ACCOUNT, PHONE_B);

        assertThat(authorizationExists("phone-a")).isFalse();
        assertThat(logins.consumeSignout(PHONE_A.id())).isFalse();
    }

    @Test
    void 직접_로그아웃한_기기는_다른_기기_로그인_때_묻지_않는다() {
        signIn("phone-a", PHONE_A, true);

        logins.endThisDevice(PHONE_A.id());

        assertThat(authorizationExists("phone-a")).isFalse();
        assertThat(logins.hasOtherActiveLogin(ACCOUNT, PHONE_B)).isFalse();
    }

    /** 맛다공 인가 기록 하나를 만들고 기기를 남깁니다. isActive가 false면 access token이 이미 만료된 기록입니다. */
    private void signIn(String authorizationId, Device device, boolean isActive) {
        database.jdbc()
                .update(
                        """
                        INSERT INTO oauth2_authorization (
                            id, registered_client_id, principal_name, authorization_grant_type,
                            access_token_value, access_token_issued_at, access_token_expires_at
                        ) VALUES (?, 'client', ?, 'authorization_code', ?, now() - INTERVAL '1 hour',
                                  now() + CASE WHEN ? THEN INTERVAL '30 minutes' ELSE INTERVAL '-1 minute' END)
                        """,
                        authorizationId,
                        ACCOUNT.toString(),
                        "token-" + authorizationId,
                        isActive);
        logins.record(authorizationId, ACCOUNT, device);
    }

    private static boolean authorizationExists(String authorizationId) {
        return Boolean.TRUE.equals(database.jdbc()
                .queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM oauth2_authorization WHERE id = ?)",
                        Boolean.class,
                        authorizationId));
    }
}
