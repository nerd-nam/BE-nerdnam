# AI 계약 테스트 자원

계약은 2026-10-26에 SPEC PR #52·#54 (C-04 **v0.3.0**, 태그 `contracts-v0.3.0`)에서 복사했다. `/requirements/extract` 응답에 `expansions`(필수, 약어 확장어)가 추가됐다. `guide.json`은 같은 시점 SPEC 예시이고 `prompt_version`이 `p4_v2`·`p5_v2`로 올랐다.

| 파일 | 원본 |
| --- | --- |
| `api-ai.openapi.yaml` | `SPEC-nerdnam/contracts/api-ai.openapi.yaml` |
| `guide.json` | `SPEC-nerdnam/fixtures/guide.json` |

계약이나 공통 예시가 바뀌면 함께 다시 복사하고 출처·버전·날짜를 갱신한다.
`ApiAiContractTest`는 실제 Jackson 직렬화로 필드 이름을 비교하고, 필수 목록은 합의한 목록과 비교한다.
enum 값은 Java enum·상수에서 가져온다. 탭별 `content`는 공통 예시가 그대로 왕복하는지 확인한다.
JSON Schema 전체 검증기나 실제 AI 서버 통합 테스트를 대신하지 않는다.

옆에 SPEC 체크아웃이 있으면 원본과도 비교한다. 없는 CI에서는 원본 비교 2개만 건너뛴다.
계약 갱신 절차와 검증 결과는 `docs/07-다음-작업-지시서.md` 작업 A에 있다.
