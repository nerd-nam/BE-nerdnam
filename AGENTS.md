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

## 현재 상태 (2026-10-26, 기획서 v1.54 까지 대조)

- **기획서 v1.53 (S09) 대조 완료:** BE 영향은 #23 하나이고 이미 고쳤다. 웹이 분기 조건으로 쓰는 `GET /guides` 의 **404(분석한 적 없음)·422(분석이 안 끝남)** 와 탭별 `status` null·`runningJobId`, 경력기술서 `rewrites` 전달, `citations` 는 모두 이미 그대로다. 404·422 는 v1.53 부터 화면 흐름이 의존하므로 `WritingGuideApiTest` 로 고정했다.
- **SPEC v1.42~v1.52 대조 완료 (2026-10-26).** 모두 "계약 영향 없음"인 화면 흐름 결정이고, **BE 코드 변경은 없다.** 전제하는 응답·동작을 하나씩 확인했다: S06 `summaryLine`·`stats`(v1.47), S08 리포트의 `runningJobId`·근거 `deleted`(v1.52), S07 검증은 **분석할 때만** 400 + `violations`(v1.51), S05 AI 작업 중 변경 금지 409(v1.50), S03 필수 5개(회사명·부서·직무·고용형태·시작 월)와 경력 삭제 거부 시 `usages`에 연결 프로젝트 id·제목(v1.46), S02 기술 삭제 거부 409 `IN_USE`(v1.42), 401·로그인 복귀는 FE 몫(v1.45). 디자인·버튼(v1.43·v1.44)은 FE 전용이다.
- **D-12·D-13 이 결정으로 확정됐다 (기획서 v1.49, SPEC 이슈 #58 처리).** 검색 후보 몫(기술 5·경력 3·프로젝트 4·사례 8)과 약어 확장 사용 범위는 이제 "제안"이 아니다. BE 구현은 PR #21 에 있다. AI 쪽은 `p3_v3` 를 채택하지 않고 `p3_v2` + 온도 0 을 쓴다.
- **BE #23 처리 (2026-10-26):** 탭 다시 만들기의 피드백 필수 조건을 "같은 분석에서 두 번째 생성부터"에서 **"이 분석·이 탭에 완료된 결과가 있을 때"** 로 바꿨다. 첫 생성이 실패한 뒤 "다시 시도"가 400 이 나던 문제다 — 실패는 서버·AI 쪽 문제라 사용자가 방향을 바꿀 이유가 없다. 생성 회차는 시도마다 올라간다(`uk_writing_guide` 가 회차를 다시 쓰지 못하게 한다). **C-03 v0.1.4** — 설명만 바뀌고 경로·필드는 그대로다.
- **AI 레포 최신(`c2f88ff`)으로 재연동 확인 (2026-10-26):** 계약·의존성 변경이 없고(판정 온도 0, `reason` 의 내부 청크 id 정리, 평가 하네스) **BE 코드 변경도 없다.** `scripts/local-smoke.sh --flow` 가 양쪽 오류 0건으로 통과했고, **실제 서버의 `GET /openapi.json` 이 BE 계약 복사본과 완전히 같은 것**을 확인했다(0.3.0). 이 버전 대조를 스크립트에 넣었다. 리포트는 가짜 임베딩이라 "미충족·근거 0개"가 정상이다. 겪은 함정(남아 있던 스텁 서버가 만든 벡터가 근거로 잡혔던 일)은 [10 로컬 연결 테스트](docs/10-로컬-연결-테스트.md).
- **실제 AI 서버 HTTP 연결 확인 (2026-10-26):** AI 레포를 `AI_MODE=stub` 으로 띄우고 BE 를 `nerdnam.ai.stub=false` 로 붙여 `bash scripts/local-smoke.sh --flow` 가 통과했다 — 프로젝트 등록 → 나누기 → 확인 질문 답하기 → 확정 → 공고 → 분석 → 리포트 → 지원동기 탭. 5개 생성 엔드포인트와 `/embed` 가 AI 서버 로그에 모두 찍혔다. **계약 불일치 1건을 찾아 고쳤다**: 계약에 기본값이 있는 필드(`Episode.role` 등)에 null 을 보내 실제 서버가 422 로 거절했다 — 스텁은 받아 주던 자리다. `infra.ai` 의 보내는 record 가 계약 기본값으로 채우도록 바꾸고, 계약 파일에서 기본값 필드를 읽어 검사하는 테스트로 고정했다. 품질·속도는 `AI_MODE=real` + GPU 가 필요해 아직이다. 기록은 [10 로컬 연결 테스트](docs/10-로컬-연결-테스트.md).

- **Compose에 API 추가:** Java 21 멀티 스테이지 빌드, DB·Redis 준비 후 기동, 세션 인증·AI 스텁 기본값, `.env` OAuth 키 전달, 콜백 주소 고정. `docker compose up`만으로 로컬 서버 묶음을 준비한다. [11 Docker 실행](docs/11-Docker-Compose-실행.md). Docker 엔진 접근 제한으로 실제 컨테이너 빌드·기동은 미검증이며, Compose 구성 검증·실행 jar 빌드·기존 테스트 성공은 확인했다.

- **위 작업은 모두 `main` 에 들어갔다 (PR #24, 2026-10-26).** 그 사이 PR #21·#22 가 먼저 머지돼 있어서, 통합 브랜치를 `main` 위로 리베이스해 중복 커밋 3개를 걸러낸 뒤 머지했다(git 이 "patch contents already upstream" 으로 떨어뜨렸다). **머지된 원격 브랜치는 지웠고 지금 원격 브랜치는 `main` 하나다.** 열린 BE PR·이슈는 0건이다.
- **주요 기능 구현 완료, 실제 서비스 연결 검증은 남음:** 기술·경력·프로젝트, 사례 검토·확정·재등록·삭제, RAG 등록, 공고·분석 기록·리포트, 작성 방향 5개 탭, 로그인·동의·탈퇴, 라이브러리·AI 작업 조회. AI 품질·속도는 스텁 테스트로 증명하지 않는다.
- **C-04 v0.3.0 / SPEC PR #52:** 요건 `expansions`를 임베딩 질의와 사전 기반 키워드 검색에 연결. 확장어는 원문·연차 계산·판정의 기술 목록을 바꾸지 않는다. **D-12 후보 몫(5/3/4/8)과 D-13 BE 답은 기획서 v1.49 에서 결정으로 확정됐다** (SPEC 이슈 #58 처리).
- **C-03 v0.1.4 (SPEC 동기화 완료):** SPEC PR #47 머지 확인. 계약 예시 상태·몸통 수정(v0.1.3)과 피드백 필수 조건 변경(v0.1.4)이 들어 있다. **SPEC 복사본이 v0.1.2 에 머물러 Prism 목업(v1.40)이 옛 예시로 응답하고 있었다 → [SPEC PR #74](https://github.com/nerd-nam/SPEC-nerdnam/pull/74) 머지 완료(기획서 v1.54).** SPEC `main` 의 복사본이 BE 가 내보낸 계약과 같은 것을 확인했다(고친 예시도 들어갔다). BE·FE 가 같은 작업을 거의 동시에 올려서 BE 의 #73 은 닫았다. **이제 옆에 SPEC 체크아웃이 있으면 복사본이 뒤처졌는지 `WebApiContractTest` 가 알려 준다** — 이번 일이 아무도 모르게 지나간 것이 문제였다.
- **v1.41:** 로그인 복귀용 `nextStep`·`reviewingProjectId`, 동의 전 403, 재동의, 실패 복귀 주소는 기존 구현과 일치. 브라우저 탭의 동의 기억·S05 이동은 FE 책임이다. ADR-0006 B10 문구는 AI 판정 기준이므로 BE 강제 판정 규칙을 추가하지 않았다.
- **검증:** 전체 테스트 285개 통과. 272개에 C-04 HTTP 응답 해석·추출 결과 순서·재동의 중복 방지 3개(v1.41 통합)와, 계약 기본값 필드를 계약 파일에서 읽어 검사하는 8개(실서버 연결)를 더했다.
- Redis는 현재 세션 저장에 사용한다. **AI 결과 캐시는 미구현**이다. 실제 AI 서버 HTTP 왕복은 `AI_MODE=stub` 으로 확인했고(위), **모델 품질·GPU 성능은 미검증**이다.
- 자세한 변경점·완료 범위·남은 작업은 [09 최신 SPEC와 백엔드 현황](docs/09-최신-SPEC와-백엔드-현황.md)을 본다. 2026-10-26 기준 **열린 BE 이슈·PR 은 0건**이다 (#20 은 C-04 v0.3.0 머지로, #23 은 PR #24 로 닫혔다).

## 다음 할 일 (우선순위 순)

1. **`AI_MODE=real` + GPU 에서 품질·성능 측정.** `AI_MODE=stub` 왕복은 2026-10-26 에 확인했다(위 "현재 상태"). real 에서만 검색 근거·판정 품질·응답 시간을 볼 수 있고, 가짜 응답 모드에서는 청크가 임베딩 대기로 남아 근거가 비는 것이 정상이다. 목표는 재구성 15초·리포트 20초·방향성 60초, 함께 **동시 호출·실패 복구·D-12 검색 품질**도 본다.
2. **FE 실제 연결:** Google·카카오 OAuth 왕복, 쿠키·CSRF·동의, 검토 복귀와 리포트·작성 방향까지. 목업이나 MockMvc 성공을 브라우저 연결 완료로 표시하지 않는다.
3. **BE-17:** 암호화할 저장 필드·키 관리 방식을 정하고 구현한다. AI로 보내는 자유 입력의 개인정보 제거와 로그 노출도 점검한다.
4. **Redis AI 단계 캐시:** `/embed` 질의·요건 추출의 범위, TTL, 모델·프롬프트 변경 시 무효화를 정한다. 분석 기록은 항상 새로 쌓는다. **아직 "진행해"를 받지 않았다.**
5. compose `api` 컨테이너 빌드·기동 검증, EKS 배포 연결.

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
