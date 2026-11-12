-- 소셜 제공자가 별명을 주지 않으면(동의하지 않음 등) 표시 이름을 비워 둡니다. 예전에는 "카카오 사용자"처럼 채웠는데,
-- 그러면 서비스 가입 화면 닉네임 칸에 그 글자가 들어가 사용자가 지우고 다시 써야 했습니다. 서비스 가입(MatdagongMembershipService.join)이
-- 사용자가 정한 닉네임으로 채웁니다. 개발 DB에 그 기본 이름으로 남은 계정은 없어 데이터는 옮기지 않습니다.
ALTER TABLE accounts ALTER COLUMN user_nm DROP NOT NULL;

COMMENT ON COLUMN accounts.user_nm IS '공통 계정의 표시 이름. 처음 로그인 때 소셜 별명으로 채우고(없으면 NULL), 서비스 가입 때 사용자가 정한 닉네임으로 바꾼다.';
