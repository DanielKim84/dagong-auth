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
        List<MembershipStatus> rows = jdbcTemplate.query(
                """
                SELECT a.display_name, a.email, s.status
                FROM accounts a
                LEFT JOIN account_services s
                  ON s.account_id = a.id AND s.service_code = 'MATDAGONG'
                WHERE a.id = ?
                """,
                (resultSet, rowNumber) -> new MembershipStatus(
                        resultSet.getString("display_name"),
                        resultSet.getString("email"),
                        resultSet.getString("status"),
                        "ACTIVE".equals(resultSet.getString("status"))),
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
                "UPDATE accounts SET display_name = ?, email = ?, updated_at = now() WHERE id = ?",
                displayName, email, accountId);
        int updated = jdbcTemplate.update(
                """
                INSERT INTO account_services (id, account_id, service_code, status, joined_at)
                VALUES (?, ?, 'MATDAGONG', 'ACTIVE', now())
                ON CONFLICT (account_id, service_code) DO UPDATE
                SET status = 'ACTIVE', joined_at = now(),
                    withdrawal_requested_at = NULL, withdrawn_at = NULL
                WHERE account_services.status IN ('WITHDRAWAL_PENDING', 'WITHDRAWN')
                """,
                UUID.randomUUID(), accountId);
        if (updated == 0) {
            // 이미 가입된 경우는 중복 제출로 보고 성공 처리합니다. BLOCKED 상태는 가입시킬 수 없습니다.
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM account_services WHERE account_id = ? AND service_code = 'MATDAGONG'",
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
