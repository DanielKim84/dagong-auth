-- 관리자와 슈퍼 맛객 권한은 공통 계정에서 관리하고 access token에 서명해 전달합니다.
CREATE TABLE account_roles (
    user_id UUID NOT NULL REFERENCES accounts(user_id) ON DELETE CASCADE,
    role_cd VARCHAR(24) NOT NULL,
    role_st VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
    granted_by UUID REFERENCES accounts(user_id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ,
    PRIMARY KEY (user_id, role_cd),
    CONSTRAINT ck_account_roles_role CHECK (role_cd IN ('ADMIN', 'SUPER_TASTER')),
    CONSTRAINT ck_account_roles_status CHECK (role_st IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_account_roles_revoked_at CHECK ((role_st <> 'REVOKED') OR revoked_at IS NOT NULL)
);

CREATE INDEX idx_account_roles_active_user
    ON account_roles (user_id, role_cd) WHERE role_st = 'ACTIVE';

COMMENT ON TABLE account_roles IS '공통 계정의 관리자·슈퍼 맛객 권한과 부여·회수 이력.';
COMMENT ON COLUMN account_roles.user_id IS '권한을 부여받은 공통 계정 UUID.';
COMMENT ON COLUMN account_roles.role_cd IS '권한 종류: ADMIN 또는 SUPER_TASTER.';
COMMENT ON COLUMN account_roles.role_st IS '권한 상태: ACTIVE 또는 REVOKED.';
COMMENT ON COLUMN account_roles.granted_by IS '권한을 부여한 관리자 계정 UUID.';
COMMENT ON COLUMN account_roles.granted_at IS '권한을 부여한 시각.';
COMMENT ON COLUMN account_roles.revoked_at IS '권한을 회수한 시각.';
