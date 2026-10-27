-- Flyway의 적용 기록도 DB 도구에서 역할을 바로 확인할 수 있게 설명합니다.
COMMENT ON TABLE flyway_schema_history IS 'Flyway 스키마 변경 적용 이력.';
COMMENT ON COLUMN flyway_schema_history.installed_rank IS '마이그레이션 적용 순서.';
COMMENT ON COLUMN flyway_schema_history.version IS '스키마 버전.';
COMMENT ON COLUMN flyway_schema_history.description IS '변경 내용.';
COMMENT ON COLUMN flyway_schema_history.type IS '마이그레이션 유형.';
COMMENT ON COLUMN flyway_schema_history.script IS '실행한 스크립트 이름.';
COMMENT ON COLUMN flyway_schema_history.checksum IS '스크립트 변경 확인용 체크섬.';
COMMENT ON COLUMN flyway_schema_history.installed_by IS '적용한 DB 계정.';
COMMENT ON COLUMN flyway_schema_history.installed_on IS '적용 시각.';
COMMENT ON COLUMN flyway_schema_history.execution_time IS '실행 시간(밀리초).';
COMMENT ON COLUMN flyway_schema_history.success IS '적용 성공 여부.';
