# 08. 웹 API 계약 (C-03 v0.1.0)

기준은 기획서 v1.32다. [계약 파일](../contracts/web-api.openapi.yaml)은 실제 컨트롤러의 40개 경로·55개 작업을 포함한다.

**공용 계약 검토 중: [SPEC PR #47](https://github.com/nerd-nam/SPEC-nerdnam/pull/47)** (2026-10-23 올림). 이 PR 에 C-03 파일과 C-02 화면별 API 목록의 엔드포인트·공통 결정(인증·오류 형식·AI 작업 폴링·S01 동의 순서·S08 분석 기록과 삭제·재등록 중 근거·S09 `content` 연결·다시 만들기 피드백·S04 재구성 중 삭제)이 함께 들어 있다. 세 팀 검토 대상이라 임의로 머지하지 않는다. 합의되면 SPEC 의 `contracts/web-api.openapi.yaml` 을 기준으로 FE 클라이언트를 만든다.

## FE 검토 반영 (SPEC PR #47, 2026-10-23)

FE가 C-03 v0.1.0을 보고 요청한 3가지를 모두 고쳤고, 확인받은 뒤 `path`까지 뺐다. 계약은 **v0.1.2**다.

| FE 요청 | 고친 것 |
| --- | --- |
| 작성 방향 탭 이름이 요청 경로(`motive`)와 응답(`motivation`)으로 갈린다 | `GuideTab.path()`에 `@JsonValue`를 붙여 **응답도 `motive`·`intro`·`portfolio`·`career`·`image`**로 나간다. 받을 때는 둘 다 받는다(`@JsonCreator` = `parse()`). AI 계약(C-04)과도 같은 값이다 |
| 꼭 있는 값도 모두 null 가능이다 | 응답은 **`@Nullable`(jspecify)로 표시한 필드만** null이 될 수 있다. 표시는 main 코드의 record 선언에 있어 읽기만 해도 알 수 있다. `id`·이름·`status`·목록·`requestedAt` 같은 값은 이제 null이 아니다 |
| 스키마 이름이 Java 전체 클래스 이름이다 | **짧은 이름**(`FitReportView`)으로 내보낸다. 어느 Java 타입인지는 `x-java-class` 확장에 남긴다 — 계약 테스트가 이 값으로 실제 enum·null 여부를 대조한다. 웹의 타입 생성기는 `x-`를 무시한다 |

응답은 값이 null이어도 **키를 남긴다**. 그래서 계약은 응답 필드를 모두 `required`로 적고, 그중 null이 될 수 있는 것만 `anyOf: [..., null]`로 표시한다. 키가 아예 없는 것과 값이 null인 것을 구분하지 않아도 된다.

**`path`는 뺐다** (v0.1.2). `tab`과 값이 같아 어느 쪽을 쓸지 헷갈리고, 웹은 아직 연결 전이라 쓰는 곳이 없었다 — FE가 "`tab`만 쓰겠다"고 확인해 줬다(SPEC PR #47). 응답은 `Guide.tab`·`TabStatus.tab` 하나로만 탭을 알려 준다. 요청 경로(`/guides/{tab}`)와 같은 값이다.

### 고정한 테스트

| 테스트 | 무엇을 막나 |
| --- | --- |
| `WebApiContractTest.작성_방향_탭은_요청_경로와_응답이_같은_이름이다` | 응답 탭 이름이 다시 갈리는 것 |
| `WebApiContractTest.응답의_필수_필드는_표시한_것만_null이_된다` | `@Nullable` 표시와 계약의 null 허용이 어긋나는 것(계약 손수정 포함) |
| `WebApiContractTest.스키마_이름은_짧고_자바_타입을_확장으로_남긴다` | 이름이 FQCN으로 돌아가는 것, `x-java-class`가 빠지는 것 |
| `WritingGuideApiTest` | 실제 응답의 `tab`이 `motive`이고 `path`가 없는지 |
| `JsonValueCaseTest.분석하지_않은_공고도_키는_모두_주고_없는_값만_null이다` | 실제 응답에서 "키는 있고 값만 null"이 깨지는 것 |

## 예시 응답과 웹 목업 (FE-03, 기획서 v1.40)

**웹 목업 서버(Prism)가 이 계약의 예시를 그대로 응답한다.** 그래서 예시는 FE 의 개발용 데이터이고, 실제 서버가 주지 않는 조합이면 화면이 막힌다.

**SPEC 복사본 맞추기 (2026-10-26 추가):** C-03 은 BE 가 원본이고 SPEC 이 복사본입니다(SPEC README 협업 규칙). 복사 PR 을 잊으면 **웹 목업(Prism)이 옛 계약으로 응답**합니다 — 실제로 SPEC 이 `v0.1.2` 에 머물러 있어 고친 예시가 FE 에 전달되지 않고 있었습니다(v0.1.4 동기화: SPEC PR #74). 이제 옆에 SPEC 체크아웃이 있으면 `WebApiContractTest.옆에_SPEC이_있으면_복사본이_뒤처졌는지_알려_준다` 가 버전과 함께 알려 줍니다. SPEC 이 없는 CI 에서는 건너뜁니다. **주의:** Gradle 은 SPEC 파일을 입력으로 추적하지 않으므로, 레포에 바뀐 것이 없으면 테스트 작업을 건너뜁니다 — SPEC 만 바뀐 상황을 확인하려면 `--rerun` 을 붙입니다.

**v0.1.4 (2026-10-26):** 탭 다시 만들기의 피드백 필수 조건을 "완료된 결과가 있을 때"로 바꿨다 (BE #23). 설명만 바뀌고 경로·필드는 그대로다.

실제로 어긋나 있던 것 (2026-10-26에 고침, v0.1.3):

| 예시 | 문제 | FE 에 생기던 일 |
| --- | --- | --- |
| `FitReportView` | `status: in_progress` 인데 한 줄 진단·판정이 채워짐 | S08 이 "분석 중" 로딩에서 멈춘 채 리포트를 못 보여 준다 |
| `PostingSummary` | `status: in_progress` 인데 충족 수가 채워짐 | S10 카드가 전부 "분석 중"으로 보인다 |
| `Guide` | `status: generating` 인데 `content` 가 채워짐 | S09 탭이 "만드는 중"에서 멈춘다 |
| `Review` | `status: draft` 인데 `episodes` 가 채워짐 | 초안이면 실제로는 사례가 비어 있다 |

원인은 예시 생성기가 **enum 의 첫 값**을 쓴 것이다(`in_progress`·`generating`·`draft`가 첫 값). 몸통이 채워진 예시는 끝난 상태로 맞추도록 `WebApiContractDocument.EXAMPLE_STATUS` 에 적어 두었고, `completedAt` 도 그에 맞춰 시각이 들어간다.

| 테스트 | 막는 것 |
| --- | --- |
| `WebApiContractTest.계약_예시의_상태와_몸통이_서로_맞는다` | 상태와 몸통이 다시 어긋나는 것 (새 상태 필드가 생겨도 잡는다) |
| `WebApiContractTest.본문이_있는_모든_응답에_예시가_있다` | 목업이 응답할 예시가 빠지는 것 |

예시 값은 가상 데이터다(회원 id 1, "예시 회사"). E2E 는 `fixtures/` 를 웹 계약 모양으로 바꿔 쓴다 (FE-17).

## 계약 갱신과 테스트

```bash
./gradlew exportWebApiContract
./gradlew test
```

`WebApiContractTest`는 현재 컨트롤러와 저장한 계약의 일치, 경로 누락·잘못된 참조·인증 주입값 노출, 실제 JSON 매퍼의 enum 값을 검사한다. 변경할 때 생성된 diff를 검토하고 SPEC에도 같은 파일을 올린다. 계약 테스트 자체가 모든 API의 동작을 검증하는 것은 아니다. 각 도메인·API 테스트가 실제 상태 변화와 오류를 확인한다.

문서 생성에 쓰는 [springdoc](https://springdoc.org/)은 테스트 의존성이다. 운영 서버에 문서용 엔드포인트를 추가하지 않는다. 예시는 형식 확인용 가상 데이터다. 임시 스크립트로 검증을 대체하지 않고 저장소의 테스트로 확인한다.

## 로그인과 동의

1. 웹에서 현재 개인정보 처리방침·약관을 보여주고 직접 체크하게 한다.
2. `/oauth2/authorization/google` 또는 `/oauth2/authorization/kakao`로 이동한다.
3. 돌아오면 `GET /api/me`로 `consentRequired`와 `termsVersion`을 확인한다.
4. `GET /api/auth/csrf`의 `headerName`·`token`을 받고, 동의한 사용자만 본문 없이 `POST /api/me/consents`를 보낸다. 쿠키도 함께 보낸다.
5. 현재 버전 동의 전에는 서비스 API가 `403 CONSENT_REQUIRED`를 준다. 내 정보 조회·동의·탈퇴·로그아웃·CSRF 준비는 가능하다. 이전 약관에 동의한 회원도 현재 버전 재동의가 필요하다.

인증은 `SESSION` 쿠키다. 웹 요청은 `credentials: include`, 변경 요청은 `X-XSRF-TOKEN` 헤더를 사용한다. 로그인·로그아웃 뒤에는 CSRF 토큰을 다시 받는다. `X-Member-Id`는 로컬 개발 전용이다.

## FE 연결 규칙

| 상황 | 응답과 처리 |
| --- | --- |
| AI 요청 접수 | `202 + jobId` → `GET /api/ai-jobs/{jobId}` 폴링. 완료 후 대상 조회 |
| 같은 대상 작업 중 | `409 AI_JOB_RUNNING + runningJobId`. 기존 작업을 조회 |
| 값 표기 | 필드는 camelCase, enum 값은 소문자. 업무 오류의 `title`만 대문자 |
| 사용 중인 기술·경력 삭제 | `409 IN_USE + usages[{type,id,name}]`. 사용처를 보여주고 먼저 연결 해제 |
| 분석 기록 | `GET /api/job-postings/{postingId}/analyses`, 최신순. 고른 `analysisId`는 `/api/fit-analyses/{analysisId}`로 조회 |
| 재등록 중 근거 | `reregistering=true`이면 S06 프로젝트 카드로 이동 |
| 삭제한 근거 | `deleted=true`, `projectId=null`. 이름과 "삭제된 경험"만 표시하고 원본 이동을 숨김. 리포트의 `snippet`도 null |
| 프로젝트 삭제 | 모든 상태에서 가능. 해당 프로젝트 재구성 중이면 409. 이전 분석·작성 방향은 유지 |
| 미래 월 | 경력·프로젝트의 시작·종료 월 모두 한국 시간의 이번 달까지. 미래 월은 400 |
| 탭 다시 만들기 | **완료된 결과가 있을 때만** 공백 아닌 `feedback` 필수(없으면 400). 첫 생성과 **실패 뒤 다시 시도**는 생략 가능 — 실패는 서버·AI 쪽 문제라 방향을 바꿀 이유가 없다 (BE #23) |

S09의 `content`는 C-04 탭별 내용을 보존한다. `sources`의 청크 id를 같은 응답의 `citations[].chunkId`와 연결하면 이름·원본 id·삭제 여부를 얻는다. `coverage.requirement_id`는 S08의 `matches[].requirementId`와 연결하면 요건 문구·필수/우대 구분을 얻는다. `content` 안에 원본 정보를 중복 삽입하지 않는다.

## 이번 이슈 검토 결과

| BE 이슈 | 처리 |
| --- | --- |
| #4 | C-03 계약 생성·대조 테스트. 공고 목록 상태·분석 실패 코드와 검색 조건의 소문자 값 정리 |
| #5 | PR #10으로 이미 정리됨. 기본 브랜치 `main`, PR #13 구현 포함 확인 |
| #6 | 근거에 현재 재등록 여부 제공. 이전 분석에도 반영 |
| #7 | 분석 기록 API는 이미 구현됨. 계약에 명시하고 다른 회원 조회 404 검증 추가 |
| #8 | 빈 피드백 거부는 이미 구현됨. 공백만 보낸 요청도 400 검증 추가 |
| #9 | 현재 동의 전 서비스 접근 제한과 CSRF 준비 API. 실제 쿠키·헤더 및 재동의 테스트 |
| #12 | AI 504 → TIMEOUT, 재시도 없음 |
| #14 | 프로젝트 삭제, 청크 즉시 제거, 이전 근거 이름·삭제 표시 보존 |
| #15 | 미래 월을 생성·수정에서 거부 |
| #16 | 생성 모델 상태와 무관하게 embedding ready이면 대기 청크 재처리 |

전체 `./gradlew test` 255개 통과. 실제 AI 서버 연결·검색 가중치 합의(D-12)·결과 캐시는 다음 작업으로 남는다.
