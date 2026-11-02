package com.dagong.auth.account;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** access token의 roles 클레임에 넣을 계정 권한(account_roles)을 읽습니다. */
@Service
public class AccountRoleService {
    private final JdbcTemplate jdbc;

    public AccountRoleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 활성 권한 코드(ADMIN, SUPER_TASTER 등)를 이름순으로 돌려줍니다. */
    @Transactional(readOnly = true)
    public List<String> activeRoles(UUID accountId) {
        return jdbc.query(
                "SELECT role_cd FROM account_roles WHERE user_id = ? AND role_st = 'ACTIVE' ORDER BY role_cd",
                (rs, row) -> rs.getString("role_cd"),
                accountId);
    }
}
