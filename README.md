# nerdnam-api (BE-nerdnam)

nerdnam(AI 맞춤형 지원서 코치)의 API 서버입니다. 기획서 5장의 `nerdnam-api`에 해당합니다.
인증, 도메인 로직, 데이터 저장(PostgreSQL + pgvector), AI 서버 호출 조율을 맡습니다.

- 기준 문서: `nerdnam-docs`(SPEC-nerdnam)의 `기획서.md` v1.31, `작업목록.md`, ADR
- 계약: **C-04 `contracts-v0.3.0`** 을 따르고 [AI 계약 테스트](docs/06-AI-계약-초안.md)로 대조한다. **C-03 v0.1.2** 는 [웹 계약 파일](contracts/web-api.openapi.yaml)과 [연결 안내](docs/08-웹-API-계약.md)에 있다 (SPEC PR #47 로 머지).

## 빠른 시작

```bash
docker compose up -d     # PostgreSQL(pgvector) + Redis
./gradlew test           # 테스트 (Docker 필요. 없으면 NERDNAM_TEST_USE_LOCAL_DB=true → 05 문서)
./gradlew bootRun        # http://localhost:8080/actuator/health
```

처음이라면 [05 로컬 환경 셋업](docs/05-로컬-환경-셋업.md)부터 보세요.

## 기술 구성

| 영역 | 선택 |
| --- | --- |
| 언어·런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1 (Web MVC, Validation, Actuator, Security + OAuth2 Client) |
| 데이터 | Spring Data JPA (Hibernate 7), Flyway, PostgreSQL 17 + pgvector |
| 세션·캐시 | Redis 7 (Spring Session — 세션 저장소. 결과 캐시는 아직) |
| 빌드 | Gradle 9.8.1 (Kotlin DSL) |
| 테스트 | JUnit 5, AssertJ, Mockito, Testcontainers 2, Spring Security Test |

## 인증

| 방식 | 언제 | 어떻게 |
| --- | --- | --- |
| 소셜 로그인 쿠키 세션 (기본) | 배포·통합 | 웹이 `/oauth2/authorization/google`(또는 `kakao`)로 이동 → 성공하면 `nerdnam.auth.login-success-url`. 상태를 바꾸는 요청은 쿠키 `XSRF-TOKEN` 값을 헤더 `X-XSRF-TOKEN`으로. 로그아웃 `POST /api/auth/logout`. **세션은 Redis 에 저장**(7일, 키 `nerdnam:session:sessions:{id}`)해서 앱을 재시작해도 로그인이 유지된다 |
| `X-Member-Id` 헤더 | local 프로필만 | 로그인 없이 회원을 고른다. 로컬 DB 에 1번 회원이 있다. local 이 아니면 앱이 뜨지 않는다 |

## API

오류는 모두 `ProblemDetail`입니다: `{"title": "IN_USE", "status": 409, "detail": "화면에 보여줄 문장", ...}`.
**응답 JSON 의 값(판정·상태·유형)은 소문자**입니다 — `met`, `draft`, `in_progress` (D-10). 요청은 대·소문자 모두 받습니다. 오류 코드(`title`)만 대문자입니다.
AI 를 부르는 요청은 `202 {"jobId"}`를 바로 돌려주고, 웹은 `GET /api/ai-jobs/{jobId}`로 끝났는지 본 뒤 결과를 다시 읽습니다. 같은 대상에 진행 중인 작업이 있으면 `409 AI_JOB_RUNNING` + `runningJobId`.

| 메서드·경로 | 화면 | 내용 |
| --- | --- | --- |
| **공통** | | |
| `GET /api/auth/csrf` | 공통 | 변경 요청에 쓸 CSRF 토큰·쿠키 준비 |
| `GET /api/me` | 머리글 | 내 정보 + `consentRequired`(지금 약관에 동의 전) |
| `POST /api/me/consents` | S01 | 이용약관·개인정보 수집 동의 기록 |
| `DELETE /api/me` | 계정 메뉴 | 탈퇴 — 원본·벡터 즉시 삭제, 세션 종료 |
| `GET /api/me/onboarding` | 공통 | 진행 상태 + `nextStep`(SKILLS/CAREER/PROJECT/LIBRARY) + `reviewingProjectId` |
| `GET /api/me/library` | S06 | 통계·프로젝트 카드(확정 사례 한 줄 요약, `status`·`reregistering` 으로 상태별 카드)·경력·기술 한 번에 |
| `GET /api/ai-jobs/{id}` | 공통 | AI 작업 상태 (`IN_PROGRESS/COMPLETED/FAILED`, `failureCode`, `resultId`) |
| **기술스택·경력** | | |
| `GET /api/skills?query=&category=` | S02 | 표준 기술 사전 자동완성 (별칭 포함) |
| `GET/POST /api/me/skills`, `PATCH/DELETE /api/me/skills/{id}` | S02 | 기술스택. 삭제는 쓰는 곳이 있으면 409 + `usages[]` |
| `GET/POST /api/me/careers`, `PUT/DELETE /api/me/careers/{id}` | S03 | 경력. 수정은 `version` 필수, 삭제는 프로젝트가 연결돼 있으면 409 |
| **프로젝트 (S04·S05)** | | |
| `GET/POST /api/projects`, `GET/PUT /api/projects/{id}` | S04 | 목록·빈 초안 만들기·상세·자동 임시 저장(`version`, 초안일 때만) |
| `DELETE /api/projects/{id}` | S04 | 모든 상태 삭제. 재구성 중에는 409. 청크 즉시 삭제, 이전 분석은 이름·삭제 표시 유지 |
| `POST /api/projects/{id}/skills` | S04 | 기술스택에 없는 기술 추가 + 연결 (`{"name","category"}`) |
| `POST /api/projects/{id}/review/discard` | S04·S05 | 확정 전 검토 결과 버리기 (여러 번 불러도 안전) |
| `POST /api/projects/{id}/split` | S04 | 재구성하기 → 202 |
| `GET /api/projects/{id}/review` | S05·S06 | 원문·답변·사례(모든 버전 포함)·진행 중인 작업. 확정한 프로젝트면 **읽기 모드**(`status = CONFIRMED` + 확정한 사례만, 아래 변경 API 는 422) |
| `POST /api/projects/{id}/resplit` | S05 | 다시 나누기 (`{"feedback"}` 필수) → 202 |
| `POST /api/projects/{id}/episodes/{e}/revision` | S05 | 수정 요청 (`{"feedback"}`) → 202 |
| `POST /api/projects/{id}/episodes/{e}/clarifications/{key}/answer` | S05 | 확인 질문 답변 (`{"answer"}`) → 202 |
| `POST /api/projects/{id}/episodes/{e}/clarifications/{key}/drop` | S05 | 빼고 진행 |
| `PUT /api/projects/{id}/episodes/{e}` / `POST /api/projects/{id}/episodes` | S05 | 직접 편집 / 사례 직접 추가 |
| `POST /api/projects/{id}/episodes/{e}/restore` | S05 | 이 버전으로 바꾸기 (`{"versionNo"}`) |
| `POST /api/projects/{id}/episodes/{e}/confirm`·`exclude`·`undo` | S05 | 사례 확정·제외·다시 검토하기 |
| `POST /api/projects/{id}/confirm` | S05 | 프로젝트 확정 → RAG 등록 |
| `POST /api/projects/{id}/reopen` | S05·S06 | 재등록 시작 — 읽기 모드·라이브러리의 "수정"(웹이 안내창을 띄운 뒤). 초안으로 돌아가고 이전 확정본은 다시 확정할 때까지 검색에 사용 |
| `GET/PUT /api/projects/{id}/rating` | S05 | 사례 생성 만족도 평가 (`{"score":1~5,"comment"}`, 없으면 GET 204) |
| **공고·적합도 (S07·S08·S10)** | | |
| `GET /api/job-postings` | S10 | 공고 목록 (상태: DRAFT/IN_PROGRESS/COMPLETED/FAILED, 충족 수) |
| `POST /api/job-postings`, `GET/PUT/DELETE /api/job-postings/{id}` | S07 | 첫 임시 저장(id 발급)·조회·자동 저장(`version`)·삭제 |
| `POST /api/job-postings/{id}/analyses` | S07·S08 | 적합도 분석하기 / 다시 시도 → 202. 입력 오류면 400 + `violations[]` |
| `GET /api/job-postings/{id}/report` | S08 | 최근 분석 리포트: 충족 수, 한 줄 진단, 요건별 판정·근거 카드(사례 근거는 `projectId` + `sourceId`로 S05 읽기 모드에서 펼침)·갭 가이드 |
| `GET /api/job-postings/{id}/analyses` | S08 | 분석 기록 (분석 id·상태·요청·완료 시각, 최신순) |
| `GET /api/fit-analyses/{id}` | S08 | 특정 분석 리포트 (기록에서 고른 분석) |
| **작성 방향 (S09)** | | |
| `GET /api/job-postings/{id}/guides` | S09 | 탭 5개 상태 |
| `GET/POST /api/job-postings/{id}/guides/{tab}` | S09 | 탭 결과·만들기/다시 만들기 → 202. tab = motive·intro·portfolio·career·image. **다시 만들 때는 `{"feedback"}` 필수**(처음 만들 때는 없어도 됨) |
| `GET/PUT /api/job-postings/{id}/guides/rating` | S09 | 방향 제시 만족도 평가 |

```bash
curl -H 'X-Member-Id: 1' localhost:8080/api/me/onboarding
curl -X POST -H 'X-Member-Id: 1' -H 'Content-Type: application/json' -d '{"name":"리액트"}' localhost:8080/api/me/skills
```

JSON 값은 소문자입니다(`met`, `language`, `in_progress`) — D-10 결정. 오류 코드(`ProblemDetail.title`)만 대문자입니다. `JsonValueCaseTest` 가 고정합니다.

## 설계 문서

| 문서 | 내용 |
| --- | --- |
| [01 개발 방향](docs/01-개발-방향.md) | 모듈러 모놀리스, DDD 적용 범위, 패키지·의존 규칙, JPA 규칙, AI 호출과 트랜잭션, 테스트 전략 |
| [02 도메인 모델](docs/02-도메인-모델.md) | 용어집, 애그리거트, Project·FitAnalysis 상세(불변식·상태 전이·코드 스케치), 이벤트, 결정 필요 항목 |
| [03 테이블 설계](docs/03-테이블-설계.md) | ERD, 테이블 목록, 설계 결정과 이유, 하이브리드 검색 SQL |
| [04 개발 계획](docs/04-개발-계획.md) | 주차별 계획, 0주차 학습, 기능 구현 순서, PR 체크리스트, 위험 |
| [05 로컬 환경 셋업](docs/05-로컬-환경-셋업.md) | 설치, 실행, IntelliJ, DB 접속, 문제 해결, Docker 없이 테스트, 소셜 로그인 켜기 |
| [06 AI 계약 초안](docs/06-AI-계약-초안.md) | API ↔ AI 요청·응답 모양 (C-04 전), 실패 구분, API 가 결과를 다듬는 규칙 |

## 모듈

| 패키지 | 역할 |
| --- | --- |
| `member` | 회원·로그인·동의·탈퇴 |
| `skill` | 표준 기술 사전, 기술명 정규화 |
| `profile` | 기술스택, 경력 |
| `project` | 프로젝트·사례(에피소드) — 핵심 도메인 |
| `jd` | 채용공고 입력·검증 |
| `matching` | 적합도 분석·리포트 — 핵심 도메인 |
| `guide` | 작성 방향, 이미지 프롬프트 |
| `rag` | 청크·벡터 저장, 하이브리드 검색 |
| `aijob` | AI 작업 상태(웹이 조회), 같은 대상 동시 1건 |
| `feedback` | 만족도 평가 |
| `library` | 라이브러리(S06)·온보딩 진행 상태 조회 |
| `infra.ai` | AI 서버 클라이언트 |
| `common` | 공통 기반 코드 |
