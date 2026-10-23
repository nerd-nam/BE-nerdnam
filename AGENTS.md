# AGENTS.md — nerdnam-api (BE-nerdnam)

Codex 가 이 레포에서 일할 때 먼저 읽는 안내입니다. 사람도 같이 읽습니다.

## 사용자와 일하는 방식

- 사용자는 Java·Spring 을 처음 쓰는 주니어 백엔드 개발자입니다. **한국어로, 쉬운 말로** 설명하고, 전문 용어는 풀어 씁니다.
- 코드 주석·문서·커밋 메시지는 한국어. 문장은 짧게, 화면에 보여줄 오류 메시지는 존댓말("~해 주세요", "~예요").
- 규칙을 새로 정하면 `docs/02-도메인-모델.md` 7-1 표에 남기고, 테스트로 고정합니다.
- 기획서·계약·디자인은 별도 레포 **SPEC-nerdnam**(`nerdnam-docs`)에 있습니다. 기획서와 다르게 만들 때는 SPEC 에도 PR 을 올립니다 (변경 이력 버전 번호가 열린 PR 과 겹치지 않게).

## 작업 순서 (매번 지킬 것)

**1. 시작 전 — SPEC-nerdnam 변경사항 확인.** 기획이 자주 바뀌고 여러 담당자가 PR 로 머지합니다. 코드를 건드리기 전에:

```bash
cd ../SPEC-nerdnam && git pull --ff-only && git log --oneline -15 && head -25 기획서.md
```

- **`fetch` 가 아니라 `pull` 입니다.** `fetch` 는 원격 정보만 받아 두고 작업 트리는 그대로 둡니다. 그러면 바로 뒤의 `git log` 와 `기획서.md` 는 **옛 로컬 상태**를 보여 줘서 "변경 없음"으로 잘못 읽습니다. 기획서·계약·디자인 파일을 실제로 읽어야 하므로 받아 와야 합니다.
- `--ff-only` 를 붙입니다. 그냥 `git pull` 은 로컬이 갈라져 있으면 머지 커밋을 만들어 공용 레포 이력을 더럽힙니다 (SPEC 의 `8400f80` 이 그렇게 생긴 커밋입니다). `--ff-only` 는 빨리 감기가 안 되면 그냥 멈춥니다.
- 멈추면(로컬 커밋이 있거나 갈라짐) 합치지 말고 원격만 들여다봅니다: `git fetch && git log --oneline HEAD..origin/main && git show origin/main:기획서.md | head -25`. 이때 SPEC 을 정리하는 일은 사용자에게 알립니다.
- `기획서.md` 변경 이력 맨 위 버전과 아래 "현재 상태"에 적힌 **반영 완료 버전**을 비교합니다.
- 차이가 있으면 그 커밋의 diff(`git show <해시>`)를 읽고 BE 할 일을 먼저 추립니다. 문서만 바뀐 변경(FE·디자인)도 BE 영향이 있는지 확인합니다 — 화면 규칙이 응답 필드로 내려오는 경우가 많습니다.
- 반영을 끝내면 이 파일의 "현재 상태" 버전을 올립니다.
- 한 세션 중에도 새 PR 이 머지될 수 있습니다. 2026-10-23 에 작업 중 v1.20 이 들어와 하마터면 놓쳤습니다.

**2. 작업 후 — 커밋을 남깁니다.** 변경을 작업 트리에 쌓아 두지 않습니다.

- `./gradlew test` 를 돌려 통과한 뒤 커밋합니다 (테스트 수를 커밋 메시지에 적습니다).
- 메시지는 한국어, 한 줄 요약 + 무엇을 왜 바꿨는지. 기획서 버전·SPEC PR 번호를 함께 적습니다 (예: `feat: 읽기 모드 (기획서 v1.17)`).
- 코드와 문서를 같은 커밋에, 성격이 다른 변경은 커밋을 나눕니다.
- `main` 에서 작업하게 됐으면 먼저 브랜치를 만듭니다. 푸시·PR 은 사용자가 요청할 때만 합니다.

## 현재 상태 (2026-10-23)

- `docs/04-개발-계획.md`의 묶음 1~4 구현 완료. 테스트 175개 통과. AI 는 스텁으로만 확인했고 실제 AI 서버 연결은 아직.
- **세션 저장소를 Redis 로 옮겼습니다** (2026-10-23, 기획서 5.3). 키 `nerdnam:session:sessions:{id}`, 7일. 앱을 재시작해도 로그인이 유지됩니다. `docker compose up -d` 에 Redis 가 포함돼 있고, Redis 가 꺼져 있으면 세션을 쓰는 요청이 500 입니다(헤더 인증은 영향 없음).
- **SPEC 기획서 v1.17~v1.20 반영 완료** (SPEC main 의 PR #14~#18 머지분):
  - v1.17 **읽기 모드**: 확정한 프로젝트의 `GET /api/projects/{id}/review` 는 `status = CONFIRMED` + **확정한 사례만** 돌려준다(제외한 사례는 빠진다, `Project.visibleEpisodes()`). 변경 API 는 모두 422, "검토 결과 버리기"는 아무 일도 하지 않는다. 사례 생성 평가는 확정 뒤에도 보고 바꿀 수 있다. S06 상태별 카드·S08 사례 근거(`projectId` + 사례 id)는 이미 주던 값이라 문서만 맞춤.
  - v1.18 AI 서버 공통 규칙: `/embed` 요청 `items[]`(text·title), 읽기 시간 초과는 다시 시도 안 함, 엔드포인트별 읽기 시간 제한, `embedding_model == "stub"`이면 벡터 저장 안 함. 청크 제목 컬럼 `rag.chunk.title`(V4).
  - v1.19 확정·제외한 사례는 "다시 검토하기" 후에만 고친다 (`editableEpisode()`).
  - v1.20 **AI 서버 입력 규칙**(어기면 422): `/restructure` mode 를 `initial`(첫 나누기) / `split`(다시 나누기, 피드백 필수) / `episode` 로 나눠 보낸다, `/guide` tab 은 웹 주소와 같은 값(`motive`·`intro`·`portfolio`·`career`·`image`). 개발용 스텁도 같은 규칙을 검사해 `AI_ERROR` 로 실패시킨다.
  - 규칙은 `docs/06-AI-계약-초안.md`(AI 계약)와 `docs/02` 7-1·8장 표에.
- PR 이 쌓여 있습니다: **#1** `chore/project-setup` → main, **#2** `feat/profile-skill-catalog` → #1, **#3** `feat/project-restructure-rag` → #2. 이 순서로 머지. 머지 후 GitHub 기본 브랜치를 `main`으로 바꿔야 함(지금 `chore/project-setup`).
- **v1.21(PR #19)은 확인했고 BE 가 할 일은 없습니다** — S05 읽기 모드의 머리글을 온보딩 머리글 대신 앱 머리글("내 경험")로 쓰고, Next.js 라우트 그룹을 `(review)` 로 나누는 FE 전용 변경입니다. 머리글을 고르는 데 필요한 `status`·`reregistering` 은 이미 검토 화면 응답에 있습니다.
- SPEC 쪽 열린 PR 없음 (v1.21 까지 모두 머지됨). SPEC 을 다시 볼 때는 `기획서.md` 변경 이력 맨 위 버전이 v1.21 보다 높은지 확인.

## 다음 할 일 (우선순위 순)

1. 실제 AI 서버 연결 후 흐름 확인, 20초 목표 측정, 검색 가중치 조정. 연결하면 `nerdnam.ai.stub=false`, 읽기 시간 제한값도 실제 측정으로 조정.
2. Redis 결과 캐시 — **무엇을 캐시할지 먼저 정해야 합니다.** 화면에 보이는 결과는 이미 DB 에 있고, 재분석은 "새로 쌓는다"가 초안 답이어서 원래 계획과 부딪칩니다. BE 제안(AI 호출 단계만 입력 해시로 캐시, 실패 후 다시 시도에서 이득)은 `docs/04` 묶음 3 "결과 캐시는 무엇을 캐시할까"에.
3. 이메일 로그인(SPEC C-02 S01 확인할 것 1), 계약 테스트 CI(BE-16·17), JSON 값 대·소문자(C-03).
4. **다른 담당의 결정을 기다리는 것**은 담당 레포 이슈로 올려 뒀습니다 (AI #3~#7, FE #2~#7, SPEC #29). 목록과 막히는 BE 작업은 `docs/04` 4-1 표에. 레포는 셋이 아니라 **넷**입니다 — `AI-nerdnam` 도 `../AI-nerdnam` 에 있습니다.

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
- AI 서버 호출 규칙(기획서 5.5)은 `docs/06-AI-계약-초안.md`에: 엔드포인트는 `infra/ai/AiEndpoint`, 읽기 시간 제한은 `nerdnam.ai.read-timeouts.*`, 읽기 시간 초과는 다시 시도하지 않는다, `embedding_model == "stub"`이면 벡터를 저장하지 않는다. **입력 규칙(v1.20)은 AI 를 부르기 전에 막는다** — mode·tab 값, `/gap-guide` 는 미충족·부분 충족만, 목록 입력은 1개 이상.
- 오류는 `BusinessException(ErrorCode, "화면 문장", properties)` → ProblemDetail.
- Flyway: 적용된 V 파일은 고치지 않는다. 새 `V4__...sql`. 테이블 수가 바뀌면 `NerdnamApplicationTests`의 개수도.
- 인증: `nerdnam.auth.mode` = `session`(기본, OAuth2 + 쿠키 세션 + CSRF) / `header`(local 전용).

## 테스트 쓰는 법

- 도메인 규칙은 Spring 없이 단위 테스트 (`project/domain/ProjectTest` 참고, 사례 id 는 `ProjectFixture.assignIds`).
- API 흐름은 `support/ApiTestSupport` 상속: AI 작업이 요청 스레드에서 바로 끝나므로 202 다음 줄에서 결과를 확인할 수 있다. 테스트마다 `newMember()`로 새 회원.
- AI 스텁 규칙(`infra/ai/StubAiServerClient` 주석): 빈 줄로 나뉜 문단 = 사례, 숫자 없는 문단 = 확인 질문 `result-metric`, 후보 텍스트에 요건 기술명이 있으면 충족. 입력 규칙(v1.20)을 어기면 진짜 서버처럼 `AI_ERROR` 로 실패한다.
- 한글 테스트 이름 때문에 리눅스에서는 `LANG=C.UTF-8`이 필요할 수 있다.
