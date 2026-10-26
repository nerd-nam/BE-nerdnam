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

## 현재 상태 (2026-10-26)

- **실제 AI 서버 HTTP 연결 확인 (2026-10-26):** AI 레포를 `AI_MODE=stub` 으로 띄우고 BE 를 `nerdnam.ai.stub=false` 로 붙여 `bash scripts/local-smoke.sh --flow` 가 통과했다 — 프로젝트 등록 → 나누기 → 확인 질문 답하기 → 확정 → 공고 → 분석 → 리포트 → 지원동기 탭. 5개 생성 엔드포인트와 `/embed` 가 AI 서버 로그에 모두 찍혔다. **계약 불일치 1건을 찾아 고쳤다**: 계약에 기본값이 있는 필드(`Episode.role` 등)에 null 을 보내 실제 서버가 422 로 거절했다 — 스텁은 받아 주던 자리다. `infra.ai` 의 보내는 record 가 계약 기본값으로 채우도록 바꾸고, 계약 파일에서 기본값 필드를 읽어 검사하는 테스트로 고정했다. 품질·속도는 `AI_MODE=real` + GPU 가 필요해 아직이다. 기록은 [10 로컬 연결 테스트](docs/10-로컬-연결-테스트.md).

- **Compose에 API 추가:** Java 21 멀티 스테이지 빌드, DB·Redis 준비 후 기동, 세션 인증·AI 스텁 기본값, `.env` OAuth 키 전달, 콜백 주소 고정. `docker compose up`만으로 로컬 서버 묶음을 준비한다. [11 Docker 실행](docs/11-Docker-Compose-실행.md). Docker 엔진 접근 제한으로 실제 컨테이너 빌드·기동은 미검증이며, Compose 구성 검증·실행 jar 빌드·기존 테스트 성공은 확인했다.

- **로컬 SPEC `bbb835d`, 기획서 v1.41까지 대조.** 원격 `git pull --ff-only`는 이 실행 환경에서 GitHub DNS 실패로 실행되지 않았다. 따라서 원격 최신 여부와 열린 PR·이슈 현황은 재확인이 필요하다.
- `fix/spec-v141-integration`은 로컬 `main`에서 만들고, 기존 검색 브랜치의 `7875514`·`2105d77`과 계약 예시 수정 `823b50c`를 모았다. 원래 브랜치는 보존했다. **main 반영·푸시·PR은 하지 않았다.**
- **주요 기능 구현 완료, 실제 서비스 연결 검증은 남음:** 기술·경력·프로젝트, 사례 검토·확정·재등록·삭제, RAG 등록, 공고·분석 기록·리포트, 작성 방향 5개 탭, 로그인·동의·탈퇴, 라이브러리·AI 작업 조회. AI 품질·속도는 스텁 테스트로 증명하지 않는다.
- **C-04 v0.3.0 / SPEC PR #52:** 요건 `expansions`를 임베딩 질의와 사전 기반 키워드 검색에 연결. 확장어는 원문·연차 계산·판정의 기술 목록을 바꾸지 않는다. **D-12 후보 몫(5/3/4/8)과 D-13 BE 답은 구현됐지만 SPEC #58 합의 확인이 남음.**
- **C-03 v0.1.3:** SPEC PR #47은 로컬 이력에서 머지 확인. 계약 예시 상태와 몸통을 맞춘 수정도 통합했다. v1.40 Prism 목업 규칙과 일치한다.
- **v1.41:** 로그인 복귀용 `nextStep`·`reviewingProjectId`, 동의 전 403, 재동의, 실패 복귀 주소는 기존 구현과 일치. 브라우저 탭의 동의 기억·S05 이동은 FE 책임이다. ADR-0006 B10 문구는 AI 판정 기준이므로 BE 강제 판정 규칙을 추가하지 않았다.
- **검증:** 전체 테스트 283개 통과. 272개에 C-04 HTTP 응답 해석·추출 결과 순서·재동의 중복 방지 3개(v1.41 통합)와, 계약 기본값 필드를 계약 파일에서 읽어 검사하는 8개(실서버 연결)를 더했다.
- Redis는 현재 세션 저장에 사용한다. **AI 결과 캐시는 미구현**이다. 실제 AI 서버 HTTP 연동·모델 품질·GPU 성능 검증도 미완료다.
- 자세한 변경점·완료 범위·남은 작업은 [09 최신 SPEC와 백엔드 현황](docs/09-최신-SPEC와-백엔드-현황.md)을 본다. 과거 BE 이슈 10건 종료는 2026-10-23 기록이며, 현재 열린 이슈가 0건이라는 뜻은 아니다.

## 다음 할 일 (우선순위 순)

1. 네트워크가 가능한 환경에서 SPEC·BE 원격 이력을 갱신하고 통합 브랜치와 열린 PR의 중복을 확인한다. D-12·D-13은 SPEC #58 결론 확인 후 확정한다.
2. **`AI_MODE=real` + GPU 에서 품질·성능 측정.** `AI_MODE=stub` 왕복은 2026-10-26 에 확인했다(위 "현재 상태"). real 에서만 검색 근거·판정 품질·응답 시간을 볼 수 있고, 가짜 응답 모드에서는 청크가 임베딩 대기로 남아 근거가 비는 것이 정상이다.
3. **FE 실제 연결:** Google·카카오 OAuth 왕복, 쿠키·CSRF·동의, 검토 복귀와 리포트·작성 방향까지. 목업이나 MockMvc 성공을 브라우저 연결 완료로 표시하지 않는다.
4. **BE-17:** 암호화할 저장 필드·키 관리 방식을 정하고 구현한다. AI로 보내는 자유 입력의 개인정보 제거와 로그 노출도 점검한다.
5. **Redis AI 단계 캐시:** `/embed` 질의·요건 추출의 범위, TTL, 모델·프롬프트 변경 시 무효화를 정한다. 분석 기록은 항상 새로 쌓는다.
6. GPU 환경에서 재구성 15초·리포트 20초·방향성 60초 목표, 동시 호출·실패 복구, D-12 검색 품질을 측정한다. EKS 배포 연결도 남아 있다.

→ 실행 방법과 완료 기준은 [07 다음 작업 지시서](docs/07-다음-작업-지시서.md).

## 실행

```bash
docker compose up                         # API + PostgreSQL 17 + pgvector + Redis (세션 인증, AI 스텁)
# IDE에서 BE를 실행하려면 docker compose stop api 후 docker compose up -d postgres redis
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

- 코드를 작성하면 저장소에 테스트 코드를 함께 작성하고 통과시킨다. 사용자가 요청하지 않으면 임의의 Python 스크립트로 직접 검증하지 않는다.
- 도메인 규칙은 Spring 없이 단위 테스트 (`project/domain/ProjectTest` 참고, 사례 id 는 `ProjectFixture.assignIds`).
- API 흐름은 `support/ApiTestSupport` 상속: AI 작업이 요청 스레드에서 바로 끝나므로 202 다음 줄에서 결과를 확인할 수 있다. 테스트마다 `newMember()`로 새 회원.
- AI 스텁 규칙(`infra/ai/StubAiServerClient` 주석): 빈 줄로 나뉜 문단 = 사례, 숫자 없는 문단 = 확인 질문 `result-metric`, 후보 텍스트에 요건 기술명이 있으면 충족. 입력 규칙(v1.20)을 어기면 진짜 서버처럼 `AI_ERROR` 로 실패한다.
- 한글 테스트 이름 때문에 리눅스에서는 `LANG=C.UTF-8`이 필요할 수 있다.
