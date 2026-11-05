package com.dagong.auth.device;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 맛다공 한 기기 로그인 규칙입니다. 계정마다 휴대폰 1대, PC 1대까지 로그인할 수 있고, 같은 종류의 다른 기기에서
 * 로그인하면 이전 기기의 맛다공 인가 기록(oauth2_authorization)을 지워 로그아웃시킵니다.
 * 맛다공 client의 인가 기록만 다루므로 다른 다공 서비스의 로그인에는 영향이 없습니다.
 */
@Service
public class DeviceLoginService {
    /**
     * 인가 기록이 아직 쓸 수 있는 상태(access token 또는 토큰 교환 전 인가 코드가 만료 전)인지 봅니다.
     * refresh token이 없는 공개 client라 access token이 만료된 기기는 로그인 중이 아닌 것으로 봅니다.
     */
    private static final String ACTIVE_AUTHORIZATION =
            "COALESCE(a.access_token_expires_at, a.authorization_code_expires_at) > now()";

    private final JdbcTemplate jdbc;

    public DeviceLoginService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 이 계정이 같은 종류의 다른 기기에서 맛다공에 로그인해 있는지 확인합니다. */
    @Transactional(readOnly = true)
    public boolean hasOtherActiveLogin(UUID accountId, Device device) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM matdagong_device_logins d
                    JOIN oauth2_authorization a ON a.id = d.authorization_id
                    WHERE d.user_id = ? AND d.device_type = ? AND d.device_id <> ? AND %s
                )
                """
                        .formatted(ACTIVE_AUTHORIZATION),
                Boolean.class,
                accountId,
                device.type().name(),
                device.id()));
    }

    /**
     * 같은 종류의 다른 기기 로그인을 끊습니다. 아직 로그인 중이던 기기에는 다음 로그인 화면에서 보일 안내를 남깁니다.
     * 이미 만료된 기록은 안내 없이 정리만 합니다.
     */
    @Transactional
    public void endOtherLogins(UUID accountId, Device device) {
        List<EndedLogin> others = jdbc.query(
                """
                SELECT d.authorization_id, d.device_id, (a.id IS NOT NULL AND %s) AS active
                FROM matdagong_device_logins d
                LEFT JOIN oauth2_authorization a ON a.id = d.authorization_id
                WHERE d.user_id = ? AND d.device_type = ? AND d.device_id <> ?
                """
                        .formatted(ACTIVE_AUTHORIZATION),
                (rs, row) -> new EndedLogin(
                        rs.getString("authorization_id"), rs.getString("device_id"), rs.getBoolean("active")),
                accountId,
                device.type().name(),
                device.id());
        if (others.isEmpty()) return;
        String[] authorizationIds =
                others.stream().map(EndedLogin::authorizationId).toArray(String[]::new);
        jdbc.update("DELETE FROM oauth2_authorization WHERE id = ANY(?::text[])", (Object) authorizationIds);
        jdbc.update("DELETE FROM matdagong_device_logins WHERE authorization_id = ANY(?::text[])", (Object)
                authorizationIds);
        others.stream()
                .filter(EndedLogin::isActive)
                .map(EndedLogin::deviceId)
                .distinct()
                .forEach(deviceId -> jdbc.update(
                        """
                        INSERT INTO matdagong_device_signouts (device_id) VALUES (?)
                        ON CONFLICT (device_id) DO UPDATE SET signed_out_dt = now()
                        """,
                        deviceId));
    }

    /**
     * 새로 만든 맛다공 인가 기록이 어느 기기의 것인지 남깁니다. 이 계정의 기록 중 인가 기록이 이미 사라진 행은 함께 정리합니다.
     * 같은 기기의 이전 기록은 지우지 않습니다. 같은 브라우저의 다른 탭이 아직 그 토큰을 쓰고 있을 수 있기 때문입니다.
     */
    @Transactional
    public void record(String authorizationId, UUID accountId, Device device) {
        jdbc.update(
                """
                INSERT INTO matdagong_device_logins (authorization_id, user_id, device_id, device_type)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (authorization_id) DO NOTHING
                """,
                authorizationId,
                accountId,
                device.id(),
                device.type().name());
        jdbc.update(
                """
                DELETE FROM matdagong_device_logins d
                WHERE d.user_id = ?
                  AND NOT EXISTS (SELECT 1 FROM oauth2_authorization a WHERE a.id = d.authorization_id)
                """,
                accountId);
    }

    /** 이 기기에서 로그아웃하면 이 기기의 맛다공 인가 기록을 지워, 다른 기기 로그인 때 이 기기를 묻지 않게 합니다. */
    @Transactional
    public void endThisDevice(String deviceId) {
        String[] authorizationIds = jdbc.queryForList(
                        "SELECT authorization_id FROM matdagong_device_logins WHERE device_id = ?",
                        String.class,
                        deviceId)
                .toArray(String[]::new);
        if (authorizationIds.length == 0) return;
        jdbc.update("DELETE FROM oauth2_authorization WHERE id = ANY(?::text[])", (Object) authorizationIds);
        jdbc.update("DELETE FROM matdagong_device_logins WHERE device_id = ?", deviceId);
    }

    /** 다른 기기 로그인으로 로그아웃된 기기였는지 확인하고, 한 번만 안내하도록 표시를 지웁니다. */
    @Transactional
    public boolean consumeSignout(String deviceId) {
        return jdbc.update("DELETE FROM matdagong_device_signouts WHERE device_id = ?", deviceId) > 0;
    }

    private record EndedLogin(String authorizationId, String deviceId, boolean isActive) {}
}
