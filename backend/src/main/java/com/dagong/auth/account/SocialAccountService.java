package com.dagong.auth.account;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 소셜 제공자의 고유 ID를 공통 계정과 연결하고 로그인 시각을 갱신합니다. */
@Service
public class SocialAccountService {
    private final JdbcTemplate jdbcTemplate;

    public SocialAccountService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Kakao 회원번호로 계정을 찾거나 생성합니다. 이메일은 계정 연락처로 자동 사용하지 않습니다. */
    @Transactional
    public AccountIdentity authenticateKakao(
            String providerUserId, String nickname, String providerEmail, boolean providerEmailVerified) {
        List<AccountIdentity> identities = jdbcTemplate.query(
                """
                SELECT a.id, a.status, si.id AS social_identity_id
                FROM social_identities si
                JOIN accounts a ON a.id = si.account_id
                WHERE si.provider = 'KAKAO'
                  AND si.provider_user_id = ?
                  AND si.disconnected_at IS NULL
                FOR UPDATE OF a, si
                """,
                (resultSet, rowNumber) -> new AccountIdentity(
                        UUID.fromString(resultSet.getString("id")),
                        AccountStatus.valueOf(resultSet.getString("status")),
                        UUID.fromString(resultSet.getString("social_identity_id"))),
                providerUserId);

        if (!identities.isEmpty()) {
            AccountIdentity identity = identities.get(0);
            if (identity.status() == AccountStatus.ACTIVE) {
                jdbcTemplate.update(
                        "UPDATE accounts SET last_login_at = now(), updated_at = now() WHERE id = ?",
                        identity.accountId());
                jdbcTemplate.update(
                        """
                        UPDATE social_identities
                        SET provider_email = ?, provider_email_verified = ?,
                            last_authenticated_at = now(), updated_at = now()
                        WHERE id = ? AND disconnected_at IS NULL
                        """,
                        providerEmail,
                        providerEmailVerified,
                        identity.socialIdentityId());
            }
            return identity;
        }

        UUID accountId = UUID.randomUUID();
        UUID socialIdentityId = UUID.randomUUID();
        String displayName = nickname == null || nickname.isBlank() ? "카카오 사용자" : nickname;

        // 이메일은 사용자가 선택적으로 등록하는 연락처이므로 소셜 계정의 이메일로 자동 채우지 않습니다.
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, display_name, status, created_at, updated_at, last_login_at)
                VALUES (?, ?, 'ACTIVE', now(), now(), now())
                """,
                accountId,
                displayName);
        jdbcTemplate.update(
                """
                INSERT INTO social_identities (
                    id, account_id, provider, provider_user_id, provider_email,
                    provider_email_verified, created_at, updated_at, last_authenticated_at
                ) VALUES (?, ?, 'KAKAO', ?, ?, ?, now(), now(), now())
                """,
                socialIdentityId,
                accountId,
                providerUserId,
                providerEmail,
                providerEmailVerified);

        return new AccountIdentity(accountId, AccountStatus.ACTIVE, socialIdentityId);
    }

    /** OAuth2 인증 계층이 계정 상태를 기준으로 로그인을 허용할 수 있게 반환합니다. */
    public record AccountIdentity(UUID accountId, AccountStatus status, UUID socialIdentityId) {}
}
