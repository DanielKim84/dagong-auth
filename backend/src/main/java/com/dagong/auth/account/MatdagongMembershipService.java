package com.dagong.auth.account;

import com.dagong.auth.account.MembershipModels.JoinRequest;
import com.dagong.auth.account.MembershipModels.JoinResult;
import com.dagong.auth.account.MembershipModels.MembershipStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 공통 계정의 맛다공 가입 여부를 확인하고 서비스 가입·가입 취소를 처리합니다. */
@Service
public class MatdagongMembershipService {
    /** account_services.svc_cd에 저장하는 맛다공 서비스 코드입니다. */
    private static final String SERVICE_CODE = "MATDAGONG";

    private static final String SERVICE_BLOCKED_MESSAGE = "맛다공 이용이 제한된 계정입니다.";

    private final JdbcTemplate jdbc;
    private final ActiveAccountChecker activeAccountChecker;
    /** 맛다공 웹의 OAuth client_id. 가입 취소 때 이 client로 남은 로그인 기록만 지웁니다. */
    private final String matdagongClientId;

    public MatdagongMembershipService(
            JdbcTemplate jdbc,
            ActiveAccountChecker activeAccountChecker,
            @Value("${dagong.auth.matdagong-client-id}") String matdagongClientId) {
        this.jdbc = jdbc;
        this.activeAccountChecker = activeAccountChecker;
        this.matdagongClientId = matdagongClientId;
    }

    /**
     * 맛다공 앱 진입(로그인 callback 포함) 때마다 가입 여부를 확인하고 최근 이용 시각을 갱신합니다.
     * 서비스가 차단된 계정의 진입 시각도 남기도록 403 응답에서는 갱신을 되돌리지 않습니다.
     */
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public MembershipStatus status(UUID accountId) {
        requireActiveAccount(accountId);
        jdbc.update(
                "UPDATE account_services SET last_access_dt = now() WHERE user_id = ? AND svc_cd = ?",
                accountId,
                SERVICE_CODE);
        List<MembershipStatus> rows = jdbc.query(
                """
                SELECT a.user_nm, a.email, s.svc_st
                FROM accounts a
                LEFT JOIN account_services s
                  ON s.user_id = a.user_id AND s.svc_cd = ?
                WHERE a.user_id = ?
                """,
                (rs, row) -> {
                    String serviceStatus = rs.getString("svc_st");
                    ServiceStatus status = serviceStatus == null ? null : ServiceStatus.valueOf(serviceStatus);
                    return new MembershipStatus(
                            rs.getString("user_nm"),
                            rs.getString("email"),
                            status,
                            status == ServiceStatus.ACTIVE,
                            List.of());
                },
                SERVICE_CODE,
                accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공통 계정을 찾을 수 없습니다.");

        MembershipStatus current = rows.get(0);
        if (current.status() == ServiceStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SERVICE_BLOCKED_MESSAGE);
        }
        List<String> loginProviders = jdbc.queryForList(
                """
                SELECT provider_cd FROM social_identities
                WHERE user_id = ? AND unlink_dt IS NULL
                ORDER BY reg_dt
                """,
                String.class,
                accountId);
        return new MembershipStatus(
                current.displayName(), current.email(), current.status(), current.registered(), loginProviders);
    }

    /** 신규 사용자의 선택 입력을 저장하고 맛다공 서비스 가입을 완료합니다. 이미 가입된 경우도 성공으로 봅니다. */
    @Transactional
    public JoinResult join(UUID accountId, JoinRequest request) {
        requireActiveAccount(accountId);
        jdbc.update(
                "UPDATE accounts SET user_nm = ?, email = ?, upd_dt = now() WHERE user_id = ?",
                request.displayName(),
                request.email(),
                accountId);
        int updated = jdbc.update(
                """
                INSERT INTO account_services (membership_id, user_id, svc_cd, svc_st, join_dt)
                VALUES (?, ?, ?, 'ACTIVE', now())
                ON CONFLICT (user_id, svc_cd) DO UPDATE
                SET svc_st = 'ACTIVE', join_dt = now(),
                    del_req_dt = NULL, del_dt = NULL
                WHERE account_services.svc_st IN ('WITHDRAWAL_PENDING', 'WITHDRAWN')
                """,
                UUID.randomUUID(),
                accountId,
                SERVICE_CODE);
        if (updated == 0) {
            // 이미 가입된 경우는 중복 제출로 보고 성공 처리합니다. BLOCKED 상태는 가입시킬 수 없습니다.
            String status = jdbc.queryForObject(
                    "SELECT svc_st FROM account_services WHERE user_id = ? AND svc_cd = ?",
                    String.class,
                    accountId,
                    SERVICE_CODE);
            if (!ServiceStatus.ACTIVE.name().equals(status)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, SERVICE_BLOCKED_MESSAGE);
            }
        }
        return new JoinResult(true);
    }

    /**
     * 맛다공 가입만 탈퇴(WITHDRAWN)로 바꿔 다음 진입 때 가입 절차를 다시 밟게 합니다. 공통 계정·카카오 연결은 다른 다공
     * 서비스도 쓰므로 남깁니다. 가입 행은 관리자 조치 이력(account_admin_actions)이 외래 키로 참조하고 join()이 다시
     * 활성화하므로 지우지 않습니다. 가입 이력이 없거나 이미 탈퇴했으면 그대로 성공하고, 서비스 차단(BLOCKED)은 재가입으로
     * 풀리지 않도록 상태를 두고 403으로 답합니다.
     */
    @Transactional
    public void cancel(UUID accountId) {
        requireActiveAccount(accountId);
        jdbc.update(
                """
                UPDATE account_services
                SET svc_st = 'WITHDRAWN', del_req_dt = COALESCE(del_req_dt, now()), del_dt = now()
                WHERE user_id = ? AND svc_cd = ? AND svc_st IN ('ACTIVE', 'WITHDRAWAL_PENDING')
                """,
                accountId,
                SERVICE_CODE);
        Boolean blocked = jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM account_services WHERE user_id = ? AND svc_cd = ? AND svc_st = 'BLOCKED'
                )
                """,
                Boolean.class,
                accountId,
                SERVICE_CODE);
        if (Boolean.TRUE.equals(blocked)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SERVICE_BLOCKED_MESSAGE);
        }
        revokeMatdagongAuthorizations(accountId);
    }

    /**
     * 탈퇴한 계정이 맛다공 client로 받은 로그인 기록(oauth2_authorization: 인가 코드·access token 기록)을 지웁니다.
     * 브라우저 공개 client라 refresh token이 없어 새 토큰을 더 받을 수 없게 되고, 다시 쓰려면 로그인과 가입부터 해야 합니다.
     * access token은 서명된 JWT라 이미 받은 것은 만료(기본 30분) 전까지 형식상 유효하며, 이 브라우저의 토큰은 화면이 로그아웃하며 지웁니다.
     * 다른 다공 서비스의 로그인 기록과 동의 기록(oauth2_authorization_consent)은 건드리지 않습니다.
     */
    private void revokeMatdagongAuthorizations(UUID accountId) {
        jdbc.update(
                """
                DELETE FROM oauth2_authorization
                WHERE principal_name = ?
                  AND registered_client_id IN (SELECT id FROM oauth2_registered_client WHERE client_id = ?)
                """,
                accountId.toString(),
                matdagongClientId);
    }

    /** 이미 발급된 토큰이라도 계정 전체가 차단·탈퇴됐다면 가입 확인·가입·가입 취소를 진행하지 않습니다. */
    private void requireActiveAccount(UUID accountId) {
        if (!activeAccountChecker.isActive(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다.");
        }
    }
}
