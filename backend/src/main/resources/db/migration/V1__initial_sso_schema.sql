-- 이메일은 선택 입력이며, 소셜 제공자의 고유 ID로 로그인 계정을 식별합니다.
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    email VARCHAR(320),
    email_verified_at TIMESTAMPTZ,
    phone_number VARCHAR(32),
    phone_verified_at TIMESTAMPTZ,
    identity_ci_hash VARCHAR(128),
    identity_verified_at TIMESTAMPTZ,
    display_name VARCHAR(80) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at TIMESTAMPTZ,
    deletion_requested_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    blocked_at TIMESTAMPTZ,
    blocked_until TIMESTAMPTZ,
    block_reason VARCHAR(500),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'DELETION_PENDING', 'DELETED')),
    CONSTRAINT ck_accounts_deletion_dates CHECK ((status <> 'DELETED') OR deleted_at IS NOT NULL)
);

-- 본인 확인을 도입할 때 중복 가입을 막도록 CI 해시와 인증된 전화번호만 선택적으로 고유하게 관리합니다.
CREATE UNIQUE INDEX uk_accounts_identity_ci_hash ON accounts (identity_ci_hash)
    WHERE identity_ci_hash IS NOT NULL;
CREATE UNIQUE INDEX uk_accounts_verified_phone ON accounts (phone_number)
    WHERE phone_number IS NOT NULL AND phone_verified_at IS NOT NULL;

CREATE TABLE social_identities (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    provider VARCHAR(32) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(320),
    provider_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_authenticated_at TIMESTAMPTZ,
    disconnected_at TIMESTAMPTZ
);
CREATE INDEX idx_social_account ON social_identities(account_id);
CREATE UNIQUE INDEX uk_social_provider_active ON social_identities (provider, provider_user_id)
    WHERE disconnected_at IS NULL;

CREATE TABLE account_services (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    service_code VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    withdrawal_requested_at TIMESTAMPTZ,
    withdrawn_at TIMESTAMPTZ,
    last_accessed_at TIMESTAMPTZ,
    CONSTRAINT uk_account_services_account_service UNIQUE (account_id, service_code),
    CONSTRAINT ck_account_services_service CHECK (service_code IN ('MATDAGONG', 'YEODAGONG')),
    CONSTRAINT ck_account_services_status CHECK (
        status IN ('ACTIVE', 'WITHDRAWAL_PENDING', 'WITHDRAWN', 'BLOCKED')
    ),
    CONSTRAINT ck_account_services_withdrawal_dates CHECK (
        (status <> 'WITHDRAWN') OR withdrawn_at IS NOT NULL
    )
);

CREATE INDEX idx_account_services_service_status
    ON account_services (service_code, status, account_id);

CREATE TABLE account_admin_actions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id),
    actor_id VARCHAR(160) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    service_code VARCHAR(32),
    CONSTRAINT ck_account_admin_action CHECK (
        action IN ('BLOCK', 'UNBLOCK', 'REQUEST_DELETION', 'COMPLETE_DELETION', 'RESTORE')
    ),
    CONSTRAINT ck_account_admin_action_service CHECK (
        service_code IS NULL OR service_code IN ('MATDAGONG', 'YEODAGONG')
    ),
    CONSTRAINT fk_account_admin_action_service FOREIGN KEY (account_id, service_code)
        REFERENCES account_services (account_id, service_code)
);
CREATE INDEX idx_account_admin_actions_account_created
    ON account_admin_actions (account_id, created_at DESC);

COMMENT ON TABLE accounts IS '맛다공과 여다공이 함께 사용하는 공통 사용자 계정. 소셜 제공자별 로그인 정보는 social_identities에 보관한다.';
COMMENT ON COLUMN accounts.id IS '공통 사용자 고유 UUID. 각 서비스에서 같은 사용자를 식별할 때 사용한다.';
COMMENT ON COLUMN accounts.email IS '사용자가 선택적으로 등록한 연락 이메일. 로그인 식별자가 아니므로 여러 계정에서 같은 이메일을 사용할 수 있다.';
COMMENT ON COLUMN accounts.display_name IS '공통 계정의 표시 이름. 서비스별 별칭은 각 서비스에서 관리한다.';
COMMENT ON COLUMN accounts.status IS '계정 전체 상태: ACTIVE(정상), BLOCKED(전체 서비스 차단), DELETION_PENDING(탈퇴 처리 중), DELETED(탈퇴 완료).';
COMMENT ON COLUMN accounts.created_at IS '공통 계정 생성 시각.';
COMMENT ON COLUMN accounts.updated_at IS '공통 계정 정보의 마지막 수정 시각.';
COMMENT ON COLUMN accounts.email_verified_at IS '이메일을 확인한 시각. 미확인 또는 미등록이면 NULL.';
COMMENT ON COLUMN accounts.phone_number IS '향후 휴대폰 확인 연동을 위한 전화번호. 현재 인증 절차는 활성화되어 있지 않다.';
COMMENT ON COLUMN accounts.phone_verified_at IS '전화번호 소유 확인 시각. NULL이면 확인되지 않은 번호다.';
COMMENT ON COLUMN accounts.identity_ci_hash IS '향후 본인 확인 연동을 위한 CI의 비밀키 기반 해시. CI 원문을 저장하지 않는다.';
COMMENT ON COLUMN accounts.identity_verified_at IS '본인 확인 서비스를 통해 신원을 확인한 시각.';
COMMENT ON COLUMN accounts.last_login_at IS '공통 계정의 가장 최근 로그인 성공 시각.';
COMMENT ON COLUMN accounts.deletion_requested_at IS '사용자가 계정 탈퇴를 요청한 시각.';
COMMENT ON COLUMN accounts.deleted_at IS '계정 탈퇴 처리를 완료한 시각.';
COMMENT ON COLUMN accounts.blocked_at IS '계정 전체 차단을 시작한 시각. 서비스 하나만 차단할 때는 account_services.status를 사용한다.';
COMMENT ON COLUMN accounts.blocked_until IS '계정 전체 차단 해제 예정 시각. 영구 차단이면 NULL.';
COMMENT ON COLUMN accounts.block_reason IS '계정 전체 차단 사유. 반복된 관리자 조치 이력은 account_admin_actions에 기록한다.';

COMMENT ON TABLE social_identities IS '외부 소셜 로그인 계정과 공통 계정의 연결. 제공자마다 발급한 고유 사용자 ID로 로그인 사용자를 식별한다.';
COMMENT ON COLUMN social_identities.id IS '소셜 계정 연결 레코드의 고유 UUID.';
COMMENT ON COLUMN social_identities.account_id IS '이 소셜 계정이 연결된 공통 사용자 ID.';
COMMENT ON COLUMN social_identities.provider IS '소셜 제공자 코드. 예: KAKAO, NAVER, APPLE.';
COMMENT ON COLUMN social_identities.provider_user_id IS '해당 제공자가 발급한 사용자 고유 ID. 같은 제공자 안에서 로그인 사용자를 식별한다.';
COMMENT ON COLUMN social_identities.created_at IS '소셜 계정을 공통 계정에 처음 연결한 시각.';
COMMENT ON COLUMN social_identities.provider_email IS '소셜 제공자가 전달한 이메일. 계정의 대표 이메일과 별개이며 제공되지 않을 수 있다.';
COMMENT ON COLUMN social_identities.provider_email_verified IS '제공자가 이 이메일을 확인된 주소라고 표시했는지 여부.';
COMMENT ON COLUMN social_identities.last_authenticated_at IS '이 소셜 계정으로 가장 최근 인증에 성공한 시각.';
COMMENT ON COLUMN social_identities.updated_at IS '소셜 계정 연결 정보의 마지막 수정 시각.';
COMMENT ON COLUMN social_identities.disconnected_at IS '연결 해제 시각. NULL인 연결만 로그인에 사용할 수 있다.';

COMMENT ON TABLE account_services IS '공통 계정의 맛다공·여다공별 가입과 이용 상태. 계정당 서비스별 행 하나를 보관한다.';
COMMENT ON COLUMN account_services.id IS '서비스 가입 레코드의 고유 UUID.';
COMMENT ON COLUMN account_services.account_id IS '서비스에 가입한 공통 사용자 ID.';
COMMENT ON COLUMN account_services.service_code IS '이용 서비스 코드: MATDAGONG 또는 YEODAGONG.';
COMMENT ON COLUMN account_services.status IS '해당 서비스에서의 상태: ACTIVE, WITHDRAWAL_PENDING, WITHDRAWN, BLOCKED.';
COMMENT ON COLUMN account_services.joined_at IS '해당 서비스 가입 시각.';
COMMENT ON COLUMN account_services.withdrawal_requested_at IS '해당 서비스에서 탈퇴를 요청한 시각.';
COMMENT ON COLUMN account_services.withdrawn_at IS '해당 서비스 탈퇴 처리를 완료한 시각.';
COMMENT ON COLUMN account_services.last_accessed_at IS '해당 서비스에 마지막으로 접속한 시각.';

COMMENT ON TABLE account_admin_actions IS '관리자가 수행한 계정 및 서비스별 차단·해제·탈퇴 조치의 이력.';
COMMENT ON COLUMN account_admin_actions.id IS '관리자 조치 이력 레코드의 고유 UUID.';
COMMENT ON COLUMN account_admin_actions.account_id IS '관리자 조치 대상 공통 사용자 ID.';
COMMENT ON COLUMN account_admin_actions.actor_id IS '조치를 수행한 관리자 식별자.';
COMMENT ON COLUMN account_admin_actions.action IS '수행한 조치: BLOCK, UNBLOCK, REQUEST_DELETION, COMPLETE_DELETION, RESTORE.';
COMMENT ON COLUMN account_admin_actions.reason IS '조치 사유. 운영 및 이의 제기 확인에 사용한다.';
COMMENT ON COLUMN account_admin_actions.created_at IS '관리자 조치가 기록된 시각.';
COMMENT ON COLUMN account_admin_actions.service_code IS '조치 대상 서비스 코드. NULL이면 계정 전체 대상이며, 값이 있으면 해당 서비스만 대상이다.';

CREATE TABLE oauth2_registered_client (
    id VARCHAR(100) PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL UNIQUE,
    client_id_issued_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    client_secret VARCHAR(200),
    client_secret_expires_at TIMESTAMPTZ,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000),
    post_logout_redirect_uris VARCHAR(1000),
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL
);

CREATE TABLE oauth2_authorization (
    id VARCHAR(100) PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL REFERENCES oauth2_registered_client(id),
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000),
    attributes TEXT,
    state VARCHAR(500),
    authorization_code_value TEXT,
    authorization_code_issued_at TIMESTAMPTZ,
    authorization_code_expires_at TIMESTAMPTZ,
    authorization_code_metadata TEXT,
    access_token_value TEXT,
    access_token_issued_at TIMESTAMPTZ,
    access_token_expires_at TIMESTAMPTZ,
    access_token_metadata TEXT,
    access_token_type VARCHAR(100),
    access_token_scopes VARCHAR(1000),
    oidc_id_token_value TEXT,
    oidc_id_token_issued_at TIMESTAMPTZ,
    oidc_id_token_expires_at TIMESTAMPTZ,
    oidc_id_token_metadata TEXT,
    refresh_token_value TEXT,
    refresh_token_issued_at TIMESTAMPTZ,
    refresh_token_expires_at TIMESTAMPTZ,
    refresh_token_metadata TEXT,
    user_code_value TEXT,
    user_code_issued_at TIMESTAMPTZ,
    user_code_expires_at TIMESTAMPTZ,
    user_code_metadata TEXT,
    device_code_value TEXT,
    device_code_issued_at TIMESTAMPTZ,
    device_code_expires_at TIMESTAMPTZ,
    device_code_metadata TEXT
);

CREATE INDEX idx_oauth2_authorization_client_principal
    ON oauth2_authorization (registered_client_id, principal_name);
CREATE INDEX idx_oauth2_authorization_state ON oauth2_authorization (state);
CREATE INDEX idx_oauth2_authorization_code ON oauth2_authorization (authorization_code_value);
CREATE INDEX idx_oauth2_access_token ON oauth2_authorization (access_token_value);
CREATE INDEX idx_oauth2_refresh_token ON oauth2_authorization (refresh_token_value);
CREATE INDEX idx_oauth2_id_token ON oauth2_authorization (oidc_id_token_value);
CREATE INDEX idx_oauth2_user_code ON oauth2_authorization (user_code_value);
CREATE INDEX idx_oauth2_device_code ON oauth2_authorization (device_code_value);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL REFERENCES oauth2_registered_client(id),
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

COMMENT ON TABLE oauth2_registered_client IS '공통 인증 서버를 사용하는 서비스 클라이언트의 OAuth2 설정과 허용된 redirect URI.';
COMMENT ON COLUMN oauth2_registered_client.id IS '클라이언트 등록 레코드 내부 고유 ID.';
COMMENT ON COLUMN oauth2_registered_client.client_id IS '맛다공·여다공 등 서비스가 인증 요청에 사용하는 공개 클라이언트 ID.';
COMMENT ON COLUMN oauth2_registered_client.client_id_issued_at IS '클라이언트 ID 발급 시각.';
COMMENT ON COLUMN oauth2_registered_client.client_secret IS '기밀 클라이언트의 해시된 비밀값. 공개 웹·앱 클라이언트는 NULL일 수 있다.';
COMMENT ON COLUMN oauth2_registered_client.client_secret_expires_at IS '클라이언트 비밀값 만료 시각.';
COMMENT ON COLUMN oauth2_registered_client.client_name IS '사용자 동의 화면 등에 표시할 서비스 이름.';
COMMENT ON COLUMN oauth2_registered_client.client_authentication_methods IS '클라이언트 인증 방식 목록.';
COMMENT ON COLUMN oauth2_registered_client.authorization_grant_types IS '허용할 OAuth2 인가 방식 목록.';
COMMENT ON COLUMN oauth2_registered_client.redirect_uris IS '인가 후 허용되는 서비스 callback 주소 목록.';
COMMENT ON COLUMN oauth2_registered_client.post_logout_redirect_uris IS '로그아웃 후 허용되는 이동 주소 목록.';
COMMENT ON COLUMN oauth2_registered_client.scopes IS '클라이언트가 요청할 수 있는 권한 범위 목록.';
COMMENT ON COLUMN oauth2_registered_client.client_settings IS 'PKCE·사용자 동의 등 클라이언트 보안 설정.';
COMMENT ON COLUMN oauth2_registered_client.token_settings IS 'Access·Refresh·ID 토큰 만료 시간 등 발급 설정.';

COMMENT ON TABLE oauth2_authorization IS 'OAuth2 인가 요청과 발급된 authorization code 및 토큰의 상태·수명 정보.';
COMMENT ON COLUMN oauth2_authorization.id IS '인가 레코드 고유 ID.';
COMMENT ON COLUMN oauth2_authorization.registered_client_id IS '인가를 요청한 등록 클라이언트의 내부 ID.';
COMMENT ON COLUMN oauth2_authorization.principal_name IS '인가를 승인한 공통 사용자 식별자.';
COMMENT ON COLUMN oauth2_authorization.authorization_grant_type IS '인가에 사용한 OAuth2 grant 유형.';
COMMENT ON COLUMN oauth2_authorization.authorized_scopes IS '사용자가 허용한 scope 목록.';
COMMENT ON COLUMN oauth2_authorization.attributes IS '인가 요청 과정에서 유지해야 하는 추가 속성.';
COMMENT ON COLUMN oauth2_authorization.state IS '인가 요청과 응답을 연결하고 위조를 방지하는 state 값.';
COMMENT ON COLUMN oauth2_authorization.authorization_code_value IS '발급된 일회용 authorization code 값.';
COMMENT ON COLUMN oauth2_authorization.authorization_code_issued_at IS 'authorization code 발급 시각.';
COMMENT ON COLUMN oauth2_authorization.authorization_code_expires_at IS 'authorization code 만료 시각.';
COMMENT ON COLUMN oauth2_authorization.authorization_code_metadata IS 'authorization code 사용·폐기 상태 메타데이터.';
COMMENT ON COLUMN oauth2_authorization.access_token_value IS '발급한 access token 값.';
COMMENT ON COLUMN oauth2_authorization.access_token_issued_at IS 'access token 발급 시각.';
COMMENT ON COLUMN oauth2_authorization.access_token_expires_at IS 'access token 만료 시각.';
COMMENT ON COLUMN oauth2_authorization.access_token_metadata IS 'access token 사용·폐기 상태 메타데이터.';
COMMENT ON COLUMN oauth2_authorization.access_token_type IS 'access token 유형.';
COMMENT ON COLUMN oauth2_authorization.access_token_scopes IS 'access token에 포함된 권한 범위.';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_value IS '발급한 OpenID Connect ID token 값.';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_issued_at IS 'ID token 발급 시각.';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_expires_at IS 'ID token 만료 시각.';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_metadata IS 'ID token 사용·폐기 상태 메타데이터.';
COMMENT ON COLUMN oauth2_authorization.refresh_token_value IS '발급한 refresh token 값.';
COMMENT ON COLUMN oauth2_authorization.refresh_token_issued_at IS 'refresh token 발급 시각.';
COMMENT ON COLUMN oauth2_authorization.refresh_token_expires_at IS 'refresh token 만료 시각.';
COMMENT ON COLUMN oauth2_authorization.refresh_token_metadata IS 'refresh token 사용·폐기 상태 메타데이터.';
COMMENT ON COLUMN oauth2_authorization.user_code_value IS 'Device Authorization Grant에서 발급하는 사용자 코드.';
COMMENT ON COLUMN oauth2_authorization.user_code_issued_at IS '사용자 코드 발급 시각.';
COMMENT ON COLUMN oauth2_authorization.user_code_expires_at IS '사용자 코드 만료 시각.';
COMMENT ON COLUMN oauth2_authorization.user_code_metadata IS '사용자 코드 상태 메타데이터.';
COMMENT ON COLUMN oauth2_authorization.device_code_value IS 'Device Authorization Grant에서 발급하는 장치 코드.';
COMMENT ON COLUMN oauth2_authorization.device_code_issued_at IS '장치 코드 발급 시각.';
COMMENT ON COLUMN oauth2_authorization.device_code_expires_at IS '장치 코드 만료 시각.';
COMMENT ON COLUMN oauth2_authorization.device_code_metadata IS '장치 코드 상태 메타데이터.';

COMMENT ON TABLE oauth2_authorization_consent IS '사용자가 서비스 클라이언트에 허용한 scope와 권한 동의 내역.';
COMMENT ON COLUMN oauth2_authorization_consent.registered_client_id IS '동의를 받은 서비스 클라이언트 내부 ID.';
COMMENT ON COLUMN oauth2_authorization_consent.principal_name IS '동의한 공통 사용자 식별자.';
COMMENT ON COLUMN oauth2_authorization_consent.authorities IS '사용자가 해당 서비스에 허용한 권한 목록.';
