-- 맛다공 로그인(인가 기록)마다 어느 기기에서 했는지 남깁니다. 같은 종류(휴대폰·PC)의 기기는 계정마다 하나만 로그인합니다.
-- oauth2_authorization 행은 Spring Authorization Server가 관리하므로 외래 키를 두지 않고, 활성 여부는 조회할 때 이어 봅니다.
CREATE TABLE matdagong_device_logins (
    authorization_id VARCHAR(100) PRIMARY KEY,
    user_id UUID NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    device_type VARCHAR(10) NOT NULL,
    reg_dt TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_matdagong_device_logins_type CHECK (device_type IN ('MOBILE', 'PC'))
);

CREATE INDEX idx_matdagong_device_logins_user ON matdagong_device_logins (user_id, device_type);
CREATE INDEX idx_matdagong_device_logins_device ON matdagong_device_logins (device_id);

COMMENT ON TABLE matdagong_device_logins IS '맛다공 로그인(oauth2_authorization 행)을 한 기기. 같은 종류의 다른 기기에서 로그인하면 이전 기기 행과 인가 기록을 지운다.';
COMMENT ON COLUMN matdagong_device_logins.authorization_id IS 'oauth2_authorization.id. 인가 기록이 지워지거나 만료되면 그 기기는 로그인 중이 아니다.';
COMMENT ON COLUMN matdagong_device_logins.user_id IS '로그인한 공통 계정 ID(accounts.user_id).';
COMMENT ON COLUMN matdagong_device_logins.device_id IS '인증 서버가 브라우저에 심은 기기 쿠키(dagong_device) 값. 같은 브라우저의 여러 탭은 같은 기기다.';
COMMENT ON COLUMN matdagong_device_logins.device_type IS '로그인 때 브라우저 정보로 나눈 기기 종류: MOBILE(휴대폰·태블릿), PC.';

-- 다른 기기의 로그인 때문에 로그아웃된 기기입니다. 그 기기가 다음에 로그인 화면을 열 때 한 번 안내하고 지웁니다.
CREATE TABLE matdagong_device_signouts (
    device_id VARCHAR(64) PRIMARY KEY,
    signed_out_dt TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE matdagong_device_signouts IS '다른 기기 로그인으로 로그아웃된 기기. 로그인 화면이 "다른 기기에서 로그인되어 로그아웃됐어요"를 한 번 보이고 지운다.';
COMMENT ON COLUMN matdagong_device_signouts.device_id IS '로그아웃된 기기의 기기 쿠키 값.';
