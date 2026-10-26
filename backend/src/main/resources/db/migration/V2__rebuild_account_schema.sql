-- 개발 DB의 기존 로그인 테스트 데이터를 비우고 간결한 공통 계정 스키마를 다시 만듭니다.
DELETE FROM oauth2_authorization;
DELETE FROM oauth2_authorization_consent;
DROP TABLE IF EXISTS account_admin_actions;
DROP TABLE IF EXISTS account_services;
DROP TABLE IF EXISTS social_identities;
DROP TABLE IF EXISTS accounts;
-- 공통 계정은 소셜 ID로 식별하고, 이메일과 본인 확인 정보는 선택적으로 연결합니다.
CREATE TABLE accounts (
    user_id UUID PRIMARY KEY,
    email VARCHAR(320),
    email_vrf_dt TIMESTAMPTZ,
    phone_no VARCHAR(32),
    phone_vrf_dt TIMESTAMPTZ,
    ci_hash VARCHAR(128),
    ci_vrf_dt TIMESTAMPTZ,
    user_nm VARCHAR(80) NOT NULL,
    user_st VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    reg_dt TIMESTAMPTZ NOT NULL DEFAULT now(),
    upd_dt TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_dt TIMESTAMPTZ,
    del_req_dt TIMESTAMPTZ,
    del_dt TIMESTAMPTZ,
    block_dt TIMESTAMPTZ,
    block_end_dt TIMESTAMPTZ,
    block_rsn VARCHAR(500),
    CONSTRAINT ck_accounts_user_st CHECK (user_st IN ('ACTIVE', 'BLOCKED', 'DELETION_PENDING', 'DELETED')),
    CONSTRAINT ck_accounts_del_dt CHECK ((user_st <> 'DELETED') OR del_dt IS NOT NULL)
);

-- 본인 확인 정보는 향후 휴대폰 인증을 도입할 때만 채우며, CI 원문은 보관하지 않습니다.
CREATE UNIQUE INDEX uk_accounts_ci_hash ON accounts (ci_hash) WHERE ci_hash IS NOT NULL;
CREATE UNIQUE INDEX uk_accounts_verified_phone ON accounts (phone_no)
    WHERE phone_no IS NOT NULL AND phone_vrf_dt IS NOT NULL;

-- 소셜 이메일은 계정 연락처로 사용하지 않으므로 보관하지 않습니다.
CREATE TABLE social_identities (
    social_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES accounts(user_id),
    provider_cd VARCHAR(32) NOT NULL,
    provider_uid VARCHAR(255) NOT NULL,
    reg_dt TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_auth_dt TIMESTAMPTZ,
    unlink_dt TIMESTAMPTZ
);
CREATE INDEX idx_social_user ON social_identities(user_id);
CREATE UNIQUE INDEX uk_social_provider_active ON social_identities (provider_cd, provider_uid)
    WHERE unlink_dt IS NULL;

-- 서비스별 가입 상태를 분리해 공통 계정에서 맛다공·여다공 이용 현황을 확인합니다.
CREATE TABLE account_services (
    membership_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES accounts(user_id),
    svc_cd VARCHAR(32) NOT NULL,
    svc_st VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    join_dt TIMESTAMPTZ NOT NULL DEFAULT now(),
    del_req_dt TIMESTAMPTZ,
    del_dt TIMESTAMPTZ,
    last_access_dt TIMESTAMPTZ,
    CONSTRAINT uk_account_services_user_svc UNIQUE (user_id, svc_cd),
    CONSTRAINT ck_account_services_svc_cd CHECK (svc_cd IN ('MATDAGONG', 'YEODAGONG')),
    CONSTRAINT ck_account_services_svc_st CHECK (
        svc_st IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'WITHDRAWN', 'BLOCKED')
    ),
    CONSTRAINT ck_account_services_del_dt CHECK (
        (svc_st <> 'WITHDRAWN') OR del_dt IS NOT NULL
    )
);

CREATE INDEX idx_account_services_svc_st ON account_services (svc_cd, svc_st, user_id);

-- 관리자 조치 이력은 현재 상태와 별도로 보관해 차단·탈퇴 이력을 추적합니다.
CREATE TABLE account_admin_actions (
    action_id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES accounts(user_id),
    admin_id VARCHAR(160) NOT NULL,
    action_cd VARCHAR(32) NOT NULL,
    rsn VARCHAR(500),
    action_dt TIMESTAMPTZ NOT NULL DEFAULT now(),
    svc_cd VARCHAR(32),
    CONSTRAINT ck_account_admin_action_cd CHECK (
        action_cd IN ('BLOCK', 'UNBLOCK', 'REQUEST_DELETION', 'COMPLETE_DELETION', 'RESTORE')
    ),
    CONSTRAINT ck_account_admin_action_svc CHECK (
        svc_cd IS NULL OR svc_cd IN ('MATDAGONG', 'YEODAGONG')
    ),
    CONSTRAINT fk_account_admin_action_svc FOREIGN KEY (user_id, svc_cd)
        REFERENCES account_services (user_id, svc_cd)
);
CREATE INDEX idx_account_admin_actions_user_dt ON account_admin_actions (user_id, action_dt DESC);

COMMENT ON TABLE accounts IS '맛다공과 여다공이 함께 사용하는 공통 사용자 계정.';
COMMENT ON COLUMN accounts.user_id IS '서비스 간 공통 사용자를 식별하는 UUID.';
COMMENT ON COLUMN accounts.email IS '사용자가 선택적으로 등록하는 연락 이메일. 로그인 식별자로 쓰지 않는다.';
COMMENT ON COLUMN accounts.email_vrf_dt IS '사용자 이메일 확인 시각. 미확인 또는 미등록이면 NULL.';
COMMENT ON COLUMN accounts.phone_no IS '향후 휴대폰 본인 확인에 사용할 번호.';
COMMENT ON COLUMN accounts.phone_vrf_dt IS '휴대폰 번호 본인 확인 시각.';
COMMENT ON COLUMN accounts.ci_hash IS '향후 본인 확인에서 중복 가입을 막기 위한 CI 비밀키 해시. CI 원문은 저장하지 않는다.';
COMMENT ON COLUMN accounts.ci_vrf_dt IS 'CI 기반 본인 확인 시각.';
COMMENT ON COLUMN accounts.user_nm IS '공통 계정의 표시 이름.';
COMMENT ON COLUMN accounts.user_st IS '계정 상태: ACTIVE, BLOCKED, DELETION_PENDING, DELETED.';
COMMENT ON COLUMN accounts.reg_dt IS '계정 생성 시각.';
COMMENT ON COLUMN accounts.upd_dt IS '계정 정보 수정 시각.';
COMMENT ON COLUMN accounts.last_login_dt IS '가장 최근 공통 로그인 성공 시각.';
COMMENT ON COLUMN accounts.del_req_dt IS '계정 탈퇴 요청 시각.';
COMMENT ON COLUMN accounts.del_dt IS '계정 탈퇴 완료 시각.';
COMMENT ON COLUMN accounts.block_dt IS '계정 전체 차단 시작 시각.';
COMMENT ON COLUMN accounts.block_end_dt IS '계정 전체 차단 해제 예정 시각. 영구 차단이면 NULL.';
COMMENT ON COLUMN accounts.block_rsn IS '현재 계정 전체 차단 사유.';

COMMENT ON TABLE social_identities IS '소셜 제공자 계정과 공통 계정의 연결.';
COMMENT ON COLUMN social_identities.social_id IS '소셜 계정 연결 레코드 UUID.';
COMMENT ON COLUMN social_identities.user_id IS '연결된 공통 사용자 ID.';
COMMENT ON COLUMN social_identities.provider_cd IS '소셜 제공자 코드. 예: KAKAO, NAVER.';
COMMENT ON COLUMN social_identities.provider_uid IS '소셜 제공자가 발급한 사용자 고유 ID.';
COMMENT ON COLUMN social_identities.reg_dt IS '소셜 계정 연결 시각.';
COMMENT ON COLUMN social_identities.last_auth_dt IS '해당 소셜 계정의 최근 인증 성공 시각.';
COMMENT ON COLUMN social_identities.unlink_dt IS '소셜 연결 해제 시각. NULL인 연결만 로그인에 사용할 수 있다.';

COMMENT ON TABLE account_services IS '공통 계정의 서비스별 가입·탈퇴·이용 상태.';
COMMENT ON COLUMN account_services.membership_id IS '서비스 가입 레코드 UUID.';
COMMENT ON COLUMN account_services.user_id IS '서비스에 가입한 공통 사용자 ID.';
COMMENT ON COLUMN account_services.svc_cd IS '서비스 코드: MATDAGONG 또는 YEODAGONG.';
COMMENT ON COLUMN account_services.svc_st IS '서비스 내 상태: ACTIVE, WITHDRAWAL_PENDING, WITHDRAWN, BLOCKED.';
COMMENT ON COLUMN account_services.join_dt IS '서비스 가입 시각.';
COMMENT ON COLUMN account_services.del_req_dt IS '서비스 탈퇴 요청 시각.';
COMMENT ON COLUMN account_services.del_dt IS '서비스 탈퇴 완료 시각.';
COMMENT ON COLUMN account_services.last_access_dt IS '해당 서비스의 최근 접속 시각.';

COMMENT ON TABLE account_admin_actions IS '관리자가 수행한 계정 전체·서비스별 조치 이력.';
COMMENT ON COLUMN account_admin_actions.action_id IS '관리자 조치 이력 UUID.';
COMMENT ON COLUMN account_admin_actions.user_id IS '조치 대상 공통 사용자 ID.';
COMMENT ON COLUMN account_admin_actions.admin_id IS '조치를 수행한 관리자 ID.';
COMMENT ON COLUMN account_admin_actions.action_cd IS '조치 코드: BLOCK, UNBLOCK, REQUEST_DELETION, COMPLETE_DELETION, RESTORE.';
COMMENT ON COLUMN account_admin_actions.rsn IS '조치 사유.';
COMMENT ON COLUMN account_admin_actions.action_dt IS '조치가 기록된 시각.';
COMMENT ON COLUMN account_admin_actions.svc_cd IS '대상 서비스 코드. NULL이면 계정 전체 대상.';
