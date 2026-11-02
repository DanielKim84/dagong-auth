package com.dagong.auth.account;

import com.dagong.auth.account.MembershipModels.JoinRequest;
import com.dagong.auth.account.MembershipModels.JoinResult;
import com.dagong.auth.account.MembershipModels.MembershipStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 공통 계정의 맛다공 가입 여부를 확인하고 서비스 가입을 마칩니다. */
@Service
public class MatdagongMembershipService {
    /** account_services.svc_cd에 저장하는 맛다공 서비스 코드입니다. */
    private static final String SERVICE_CODE = "MATDAGONG";

    private static final String SERVICE_BLOCKED_MESSAGE = "맛다공 이용이 제한된 계정입니다.";

    private final JdbcTemplate jdbc;
    private final ActiveAccountChecker activeAccountChecker;

    public MatdagongMembershipService(JdbcTemplate jdbc, ActiveAccountChecker activeAccountChecker) {
        this.jdbc = jdbc;
        this.activeAccountChecker = activeAccountChecker;
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
                            rs.getString("user_nm"), rs.getString("email"), status, status == ServiceStatus.ACTIVE);
                },
                SERVICE_CODE,
                accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공통 계정을 찾을 수 없습니다.");

        MembershipStatus current = rows.get(0);
        if (current.status() == ServiceStatus.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, SERVICE_BLOCKED_MESSAGE);
        }
        return current;
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

    /** 이미 발급된 토큰이라도 계정 전체가 차단·탈퇴됐다면 가입 확인과 가입을 진행하지 않습니다. */
    private void requireActiveAccount(UUID accountId) {
        if (!activeAccountChecker.isActive(accountId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "이용이 제한된 계정입니다.");
        }
    }
}
