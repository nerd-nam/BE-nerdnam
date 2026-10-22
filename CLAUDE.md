# CLAUDE.md — nerdnam-api (BE-nerdnam)

Claude Code 가 이 레포에서 일할 때 먼저 읽는 안내입니다. 사람도 같이 읽습니다.

## 사용자와 일하는 방식

- 사용자는 Java·Spring 을 처음 쓰는 주니어 백엔드 개발자입니다. **한국어로, 쉬운 말로** 설명하고, 전문 용어는 풀어 씁니다.
- 코드 주석·문서·커밋 메시지는 한국어. 문장은 짧게, 화면에 보여줄 오류 메시지는 존댓말("~해 주세요", "~예요").
- 규칙을 새로 정하면 `docs/02-도메인-모델.md` 7-1 표에 남기고, 테스트로 고정합니다.
- 기획서·계약·디자인은 별도 레포 **SPEC-nerdnam**(`nerdnam-docs`)에 있습니다. 기획서와 다르게 만들 때는 SPEC 에도 PR 을 올립니다 (변경 이력 버전 번호가 열린 PR 과 겹치지 않게).

## 현재 상태 (2026-10-23)

- `docs/04-개발-계획.md`의 묶음 1~4 구현 완료. AI 는 스텁으로만 확인했고 실제 AI 서버 연결은 아직.
- PR 이 쌓여 있습니다: **#1** `chore/project-setup` → main, **#2** `feat/profile-skill-catalog` → #1, **#3** `feat/project-restructure-rag` → #2. 이 순서로 머지. 머지 후 GitHub 기본 브랜치를 `main`으로 바꿔야 함(지금 `chore/project-setup`).
- SPEC 쪽 열린 PR: #14(FE, v1.17 확정 프로젝트 읽기 모드), #15(AI, v1.18 AI 서버 공통 규칙), #16(BE, v1.19 확정·제외 사례는 다시 검토하기 후 수정).

## 다음 할 일 (우선순위 순)

1. **SPEC PR #15 의 BE 요청 4가지 반영** (합의되면): `/embed` 요청을 `{"purpose", "items": [{"text", "title"}]}`로, 다시 시도는 연결 실패·503 만(읽기 시간 초과는 안 함), 엔드포인트별 읽기 시간 제한(`/guide` 120초 등), `embedding_model == "stub"`이면 벡터 저장 안 함. 고칠 곳: `infra/ai/HttpAiServerClient`, `AiServerProperties`, `rag/application/ChunkEmbedder`, `docs/06-AI-계약-초안.md`.
2. 실제 AI 서버 연결 후 흐름 확인, 20초 목표 측정, 검색 가중치 조정.
3. Redis: 세션 저장소(지금은 서버 메모리라 재시작하면 다시 로그인), 결과 캐시.
4. 이메일 로그인(SPEC C-02 S01 확인할 것 1), 계약 테스트 CI(BE-16·17), JSON 값 대·소문자(C-03).

## 실행

```bash
docker compose up -d                       # PostgreSQL 17 + pgvector, Redis
./gradlew test                             # 전체 테스트 (Testcontainers → Docker 필요)
NERDNAM_TEST_USE_LOCAL_DB=true ./gradlew test   # Docker 없이: localhost:5432/nerdnam (pgvector 필요)
./gradlew bootRun                          # local 프로필: X-Member-Id 헤더 인증, AI 스텁
curl -H 'X-Member-Id: 1' localhost:8080/api/me/onboarding
```

## 구조와 규칙 (자세한 것은 docs/01~06)

- 모듈러 모놀리스: `member skill profile project jd matching guide rag aijob feedback library` + `infra.ai`, `common`. 모듈 안은 `api / application / domain / infrastructure`.
- **다른 모듈의 엔티티·리포지토리·infrastructure 를 쓰지 않는다.** 공개 서비스·조회 결과(record)·이벤트만. 순환 의존 금지. → `architecture/ModuleBoundaryTest`가 검사.
- 엔티티: `@Setter`/`@Data` 금지, 정적 팩토리, 규칙은 애그리거트 루트 메서드 안에. 다른 애그리거트는 id 로만. 컬렉션 getter 는 수정 불가 뷰.
- 서비스는 트랜잭션 안에서 엔티티를 조회 결과(record)로 바꿔 돌려준다 (`open-in-view: false`).
- 모든 조회는 `findByIdAndMemberId` 처럼 소유권 확인. 없음·남의 것 모두 404.
- **AI 호출은 `aijob.application.AiJobs.start(JobRequest, JobWork.of(prepare, call, apply))`** 로만. prepare·apply 는 짧은 트랜잭션, call 은 트랜잭션 밖. 이런 서비스에는 `@Transactional`을 붙이지 않는다. 응답은 202 + `jobId`, 같은 대상 진행 중이면 409 + `runningJobId`.
- RAG: 원본 변경과 같은 트랜잭션에서 청크 텍스트만 맞추고(`PENDING`), 커밋 뒤 임베딩(`READY`). 검색 SQL 은 `rag/application/ChunkSearch`에만, `memberId` 필수.
- 오류는 `BusinessException(ErrorCode, "화면 문장", properties)` → ProblemDetail.
- Flyway: 적용된 V 파일은 고치지 않는다. 새 `V4__...sql`. 테이블 수가 바뀌면 `NerdnamApplicationTests`의 개수도.
- 인증: `nerdnam.auth.mode` = `session`(기본, OAuth2 + 쿠키 세션 + CSRF) / `header`(local 전용).

## 테스트 쓰는 법

- 도메인 규칙은 Spring 없이 단위 테스트 (`project/domain/ProjectTest` 참고, 사례 id 는 `ProjectFixture.assignIds`).
- API 흐름은 `support/ApiTestSupport` 상속: AI 작업이 요청 스레드에서 바로 끝나므로 202 다음 줄에서 결과를 확인할 수 있다. 테스트마다 `newMember()`로 새 회원.
- AI 스텁 규칙(`infra/ai/StubAiServerClient` 주석): 빈 줄로 나뉜 문단 = 사례, 숫자 없는 문단 = 확인 질문 `result-metric`, 후보 텍스트에 요건 기술명이 있으면 충족.
- 한글 테스트 이름 때문에 리눅스에서는 `LANG=C.UTF-8`이 필요할 수 있다.
