package com.dagong.auth.account;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

/** 맛다공이 공통 계정의 가입 여부를 확인하고 서비스 가입을 마칠 수 있는 OAuth 보호 API입니다. */
@RestController
@RequestMapping("/api/v1/matdagong/membership")
public class MatdagongMembershipController {
    private final JdbcTemplate jdbcTemplate;
    private final String clientId;

    public MatdagongMembershipController(
            JdbcTemplate jdbcTemplate,
            @Value("${dagong.auth.matdagong-client-id:matdagong-web-dev}") String clientId) {
        this.jdbcTemplate = jdbcTemplate;
        this.clientId = clientId;
    }

    /** callback에서 공통 계정과 맛다공 서비스 가입 여부를 확인합니다. */
    @GetMapping
    public MembershipStatus status(@AuthenticationPrincipal Jwt jwt) {
        UUID accountId = accountId(jwt);
        // 관리자 화면에서 서비스별 최근 이용 시각을 볼 수 있도록 진입 시각을 갱신합니다.
        jdbcTemplate.update(
                "UPDATE account_services SET last_access_dt = now() WHERE user_id = ? AND svc_cd = 'MATDAGONG'",
                accountId);
        List<MembershipStatus> rows = jdbcTemplate.query(
                """
                SELECT a.user_nm, a.email, s.svc_st
                FROM accounts a
                LEFT JOIN account_services s
                  ON s.user_id = a.user_id AND s.svc_cd = 'MATDAGONG'
                WHERE a.user_id = ?
                """,
                (resultSet, rowNumber) -> new MembershipStatus(
                        resultSet.getString("user_nm"),
                        resultSet.getString("email"),
                        resultSet.getString("svc_st"),
                        "ACTIVE".equals(resultSet.getString("svc_st"))),
                accountId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "공통 계정을 찾을 수 없습니다.");

        MembershipStatus current = rows.get(0);
        if ("BLOCKED".equals(current.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "맛다공 이용이 제한된 계정입니다.");
        }
        return current;
    }

    /** 신규 사용자의 선택 입력을 저장하고 맛다공 서비스 가입을 완료합니다. */
    @PostMapping
    @Transactional
    public Map<String, Boolean> join(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody JoinRequest request) {
        UUID accountId = accountId(jwt);
        String displayName = request.displayName() == null ? "" : request.displayName().trim();
        String email = request.email() == null || request.email().isBlank() ? null : request.email().trim();
        if (displayName.isBlank() || displayName.length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임은 1~80자로 입력해 주세요.");
        }
        if (email != null && (email.length() > 320 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일 주소를 확인해 주세요.");
        }

        jdbcTemplate.update(
                "UPDATE accounts SET user_nm = ?, email = ?, upd_dt = now() WHERE user_id = ?",
                displayName, email, accountId);
        int updated = jdbcTemplate.update(
                """
                INSERT INTO account_services (membership_id, user_id, svc_cd, svc_st, join_dt)
                VALUES (?, ?, 'MATDAGONG', 'ACTIVE', now())
                ON CONFLICT (user_id, svc_cd) DO UPDATE
                SET svc_st = 'ACTIVE', join_dt = now(),
                    del_req_dt = NULL, del_dt = NULL
                WHERE account_services.svc_st IN ('WITHDRAWAL_PENDING', 'WITHDRAWN')
                """,
                UUID.randomUUID(), accountId);
        if (updated == 0) {
            // 이미 가입된 경우는 중복 제출로 보고 성공 처리합니다. BLOCKED 상태는 가입시킬 수 없습니다.
            String status = jdbcTemplate.queryForObject(
                    "SELECT svc_st FROM account_services WHERE user_id = ? AND svc_cd = 'MATDAGONG'",
                    String.class, accountId);
            if (!"ACTIVE".equals(status)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "맛다공 이용이 제한된 계정입니다.");
            }
        }
        return Map.of("joined", true);
    }

    private UUID accountId(Jwt jwt) {
        if (jwt == null || !jwt.getAudience().contains(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "맛다공 인증 토큰이 필요합니다.");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "계정 정보를 확인할 수 없습니다.");
        }
    }

    public record MembershipStatus(String displayName, String email, String status, boolean registered) {}

    public record JoinRequest(String displayName, String email) {}
}
