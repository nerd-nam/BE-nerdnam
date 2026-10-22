-- local 프로필 전용 시드 (application-local.yml 의 spring.flyway.locations 에만 들어 있다)
-- 로그인(BE-03) 전 개발용 회원. 요청 헤더 X-Member-Id: 1 로 쓴다.
-- R__ (repeatable) 마이그레이션이라 이 파일 내용이 바뀌면 다시 실행된다 → 여러 번 실행해도 안전하게 작성.
INSERT INTO app.member (id, email, display_name)
VALUES (1, 'dev@nerdnam.local', '개발자')
ON CONFLICT (id) DO NOTHING;

-- id 를 직접 넣었으므로 identity 시퀀스를 그 다음 값으로 맞춘다 (안 하면 다음 회원 가입 때 id 1 이 겹친다)
SELECT setval(pg_get_serial_sequence('app.member', 'id'), GREATEST((SELECT max(id) FROM app.member), 1));
