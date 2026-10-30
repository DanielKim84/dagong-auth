package com.dagong.auth.account;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.stereotype.Component;

/**
 * 계정이 지금 서비스를 이용할 수 있는 상태인지 확인합니다.
 * Kakao 로그인 시점뿐 아니라 SSO 세션으로 인가 코드나 토큰을 다시 받을 때도 같은 기준을 적용합니다.
 */
@Component
public class ActiveAccountChecker {
    private final JdbcOperations jdbc;

    public ActiveAccountChecker(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isActive(UUID accountId) {
        Boolean active = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM accounts WHERE user_id = ? AND user_st = ?)",
                Boolean.class, accountId, AccountStatus.ACTIVE.name());
        return Boolean.TRUE.equals(active);
    }

    /** 계정 UUID가 아닌 이름은 이용 가능한 계정으로 보지 않습니다. */
    public boolean isActive(String accountId) {
        try {
            return isActive(UUID.fromString(accountId));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return false;
        }
    }
}
