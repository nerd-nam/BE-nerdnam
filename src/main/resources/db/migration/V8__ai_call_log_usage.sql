-- AI 호출 1건이 쓴 LLM 토큰 기록 (BE-20, 이슈 #39, 계약 C-04 v0.4.0)
--
-- AI 서버가 응답(과 오류 본문)에 usage 를 담아 준다. 요청 1건 안의 LLM 호출 합계다
-- (AI 내부 재요청, /match 의 요건별 호출 포함). BE 는 저장만 하고 금액은 계산하지 않는다 —
-- 단가가 바뀌므로 볼 때 ai_model 별 단가를 곱한다 (ADR-0007).
--
-- nullable 이다. 값이 없는 경우: 연결 실패, 읽기 시간 초과, 요청 형식 422, /embed,
-- 오류 본문을 못 읽은 경우. "안 썼다(0)"와 "모른다(null)"를 구분한다.
ALTER TABLE app.ai_call_log
    ADD COLUMN input_tokens  integer,
    ADD COLUMN output_tokens integer,
    ADD COLUMN llm_calls     integer;

COMMENT ON COLUMN app.ai_call_log.input_tokens IS 'LLM 입력 토큰 합계 (재시도 포함). 모르면 null';
COMMENT ON COLUMN app.ai_call_log.output_tokens IS 'LLM 출력 토큰 합계 (재시도 포함). 모르면 null';
COMMENT ON COLUMN app.ai_call_log.llm_calls IS '이 요청이 부른 LLM 호출 수 (재시도 포함). 모르면 null';
