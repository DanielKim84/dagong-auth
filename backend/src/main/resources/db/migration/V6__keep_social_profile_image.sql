-- 카카오·네이버 프로필 사진 주소를 남깁니다. 사용자가 사진 제공에 동의했을 때만 받고, 제공자 기본 사진이면 비워 둡니다.
-- 로그인할 때마다 그때 받은 값으로 바꾸므로 동의를 거두면 다음 로그인에 지워집니다. 서비스(맛다공)는 가입할 때 이 주소의
-- 사진을 한 번 내려받아 자기 프로필 사진으로 저장하고, 이 주소를 계속 쓰지 않습니다.
ALTER TABLE social_identities ADD COLUMN profile_img_url VARCHAR(1000);

COMMENT ON COLUMN social_identities.profile_img_url IS
    '마지막 로그인 때 소셜 제공자가 준 프로필 사진 주소(https). 사진 제공에 동의하지 않았거나 기본 사진이면 NULL.';
