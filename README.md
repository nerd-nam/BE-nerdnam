# nerdnam-api (BE-nerdnam)

nerdnam(AI 맞춤형 지원서 코치)의 API 서버입니다. 기획서 5장의 `nerdnam-api`에 해당합니다.
인증, 도메인 로직, 데이터 저장(PostgreSQL + pgvector), AI 서버 호출 조율을 맡습니다.

- 기준 문서: `nerdnam-docs`(SPEC-nerdnam)의 `기획서.md` v1.8, `작업목록.md`
- 따르는 계약: `contracts-v0.1.0` (합의 후 기입)

## 빠른 시작

```bash
docker compose up -d     # PostgreSQL(pgvector) + Redis
./gradlew test           # 테스트 (Docker 필요)
./gradlew bootRun        # http://localhost:8080/actuator/health
```

처음이라면 [05 로컬 환경 셋업](docs/05-로컬-환경-셋업.md)부터 보세요.

## 기술 구성

| 영역 | 선택 |
| --- | --- |
| 언어·런타임 | Java 21 |
| 프레임워크 | Spring Boot 4.1.1 (Web MVC, Validation, Actuator) |
| 데이터 | Spring Data JPA (Hibernate 7), Flyway, PostgreSQL 17 + pgvector |
| 빌드 | Gradle 9.8.1 (Kotlin DSL) |
| 테스트 | JUnit 5, AssertJ, Testcontainers 2 |

## API (지금까지)

로그인(BE-03) 전이라 local 프로필에서는 `X-Member-Id` 헤더로 회원을 정합니다. 로컬 DB에는 1번 회원이 들어 있습니다.

| 메서드·경로 | 화면 | 내용 |
| --- | --- | --- |
| `GET /api/skills?query=&category=` | S02 | 표준 기술 사전 자동완성 (별칭 포함, 로그인 불필요) |
| `GET /api/me/skills` | S02 | 내 기술스택 |
| `POST /api/me/skills` | S02·S04 | 기술 추가 (`{"name", "category"?, "level"?}`. 사전에 없으면 category 필요) |
| `PATCH /api/me/skills/{id}` | S02 | 숙련도 변경 (`{"level": 1~4}`) |
| `DELETE /api/me/skills/{id}` | S02 | 삭제. 경력·프로젝트에서 쓰면 409 + `usages[]` |
| `GET /api/me/careers` | S03 | 내 경력 (최신순) |
| `POST /api/me/careers` | S03 | 경력 추가 (`startMonth`·`endMonth`는 `"2023-03"`) |
| `PUT /api/me/careers/{id}` | S03 | 경력 수정 (조회 때 받은 `version` 필수, 어긋나면 409) |
| `DELETE /api/me/careers/{id}` | S03 | 삭제. 프로젝트가 연결돼 있으면 409 + `usages[]` |

```bash
curl -H 'X-Member-Id: 1' localhost:8080/api/me/skills
curl -X POST -H 'X-Member-Id: 1' -H 'Content-Type: application/json' -d '{"name":"리액트"}' localhost:8080/api/me/skills
```

오류는 모두 `ProblemDetail` 형식입니다: `{"title": "IN_USE", "status": 409, "detail": "화면에 보여줄 문장", ...}`

## 설계 문서

| 문서 | 내용 |
| --- | --- |
| [01 개발 방향](docs/01-개발-방향.md) | 모듈러 모놀리스, DDD 적용 범위, 패키지·의존 규칙, JPA 규칙, AI 호출과 트랜잭션, 테스트 전략 |
| [02 도메인 모델](docs/02-도메인-모델.md) | 용어집, 애그리거트, Project·FitAnalysis 상세(불변식·상태 전이·코드 스케치), 이벤트, 결정 필요 항목 |
| [03 테이블 설계](docs/03-테이블-설계.md) | ERD, 테이블 목록, 설계 결정과 이유, 하이브리드 검색 SQL |
| [04 개발 계획](docs/04-개발-계획.md) | 주차별 계획, 0주차 학습, 기능 구현 순서, PR 체크리스트, 위험 |
| [05 로컬 환경 셋업](docs/05-로컬-환경-셋업.md) | 설치, 실행, IntelliJ, DB 접속, 문제 해결 |

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
| `infra.ai` | AI 서버 클라이언트 |
| `common` | 공통 기반 코드 |
