#!/usr/bin/env bash
# AI 작업 몰림 재현(k6)용 시험 데이터를 **API 로** 만든다 (BE #60, SPEC ADR-0008, INFRA #19).
#
# 만드는 것: 확정 프로젝트 PROJECTS 개(임베딩 완료까지) → 공고 POSTINGS 개(요건 REQUIREMENTS 개 안팎)
#            → 공고마다 분석 1번 완료. 마지막 줄에 k6 의 POSTING_IDS 로 쓸 공고 id 목록을 출력한다.
# DB 에 직접 넣지 않는다 — 확정해야 청크가 생기고 임베딩도 AI 를 거쳐야 READY 가 된다.
# 실제 AI 를 부르므로 Bedrock 비용이 든다(사례 나누기 PROJECTS 번, 분석 POSTINGS 번).
#
# 운영:   BE_URL=https://api.example.com SESSION_COOKIE=… SEED_CONFIRM=1 \
#         MANAGEMENT_URL=http://localhost:8081 bash scripts/loadtest-seed.sh
#         - SESSION_COOKIE: 시험 계정으로 웹에서 로그인·동의한 뒤 브라우저의 SESSION 쿠키 값. 출력하지 않는다.
#         - MANAGEMENT_URL: 관리 포트(예: kubectl port-forward 8081). nerdnam_chunk_pending 이 0 이 될 때까지
#           기다린다. 없으면 EMBED_WAIT 초만 기다린다(분석 근거가 비어 있을 수 있다).
# 로컬:   BE_URL=http://localhost:8080 MEMBER_ID=1 bash scripts/loadtest-seed.sh   (header 인증, localhost 만)
set -euo pipefail

BE_URL=${BE_URL:?BE_URL 을 주세요}
PROJECTS=${PROJECTS:-3}
POSTINGS=${POSTINGS:-10}
REQUIREMENTS=${REQUIREMENTS:-20}
JOB_TIMEOUT=${JOB_TIMEOUT:-900}
EMBED_WAIT=${EMBED_WAIT:-120}
for n in "$PROJECTS" "$POSTINGS" "$REQUIREMENTS" "$JOB_TIMEOUT" "$EMBED_WAIT"; do
  [[ "$n" =~ ^[1-9][0-9]*$ ]] || { echo "숫자 설정이 잘못됐어요: $n" >&2; exit 1; }
done
(( REQUIREMENTS <= 30 )) || { echo 'REQUIREMENTS 는 30 이하로 주세요 (요건 문구 30개)' >&2; exit 1; }
command -v curl >/dev/null && command -v jq >/dev/null

local_url='^http://(localhost|127\.0\.0\.1):[0-9]+$'
if [[ -n "${MEMBER_ID:-}" ]]; then
  [[ "$BE_URL" =~ $local_url ]] || { echo 'MEMBER_ID(헤더 인증)는 localhost 에서만 씁니다' >&2; exit 1; }
elif [[ -z "${SESSION_COOKIE:-}" ]]; then
  echo 'SESSION_COOKIE(운영) 또는 MEMBER_ID(로컬)를 주세요' >&2; exit 1
elif [[ ! "$BE_URL" =~ $local_url && ${SEED_CONFIRM:-} != 1 ]]; then
  echo "$BE_URL 에 시험 데이터를 만듭니다. 맞으면 SEED_CONFIRM=1 을 붙여 다시 실행해 주세요." >&2; exit 1
fi

jar=$(mktemp); trap 'rm -f "$jar"' EXIT
csrf_header=()
if [[ -z "${MEMBER_ID:-}" ]]; then
  host=$(sed -E 's#^https?://([^/:]+).*#\1#' <<<"$BE_URL")
  printf '%s\tFALSE\t/\t%s\t0\tSESSION\t%s\n' "$host" "$([[ $BE_URL == https* ]] && echo TRUE || echo FALSE)" \
    "$SESSION_COOKIE" > "$jar"
  # 쓰기 요청에는 CSRF 토큰이 필요하다: 쿠키(XSRF-TOKEN)는 jar 에, 값은 헤더로
  csrf=$(curl -fsS -b "$jar" -c "$jar" "$BE_URL/api/auth/csrf")
  csrf_header=(-H "$(jq -r '.headerName' <<<"$csrf"): $(jq -r '.token' <<<"$csrf")")
fi

request() {
  local method=$1 path=$2 body=${3:-} response status
  local args=(-sS --connect-timeout 5 --max-time 60 -X "$method" -b "$jar" -c "$jar"
    -H 'Content-Type: application/json' ${csrf_header[@]+"${csrf_header[@]}"})
  if [[ -n "${MEMBER_ID:-}" ]]; then args+=(-H "X-Member-Id: $MEMBER_ID"); fi
  if [[ -n "$body" ]]; then args+=(--data "$body"); fi
  response=$(curl "${args[@]}" -w $'\n%{http_code}' "$BE_URL$path")
  status=${response##*$'\n'}
  response=${response%$'\n'*}
  if [[ ! "$status" =~ ^2[0-9][0-9]$ ]]; then
    printf '실패: %s %s → HTTP %s\n%s\n' "$method" "$path" "$status" "$response" >&2
    return 1
  fi
  printf '%s\n' "$response"
}

wait_job() {
  local id=$1 label=$2 job deadline=$((SECONDS + JOB_TIMEOUT))
  while (( SECONDS < deadline )); do
    job=$(request GET "/api/ai-jobs/$id")
    case $(jq -er '.status' <<<"$job") in
      completed) printf '  완료: %s (작업 %s)\n' "$label" "$id"; return 0 ;;
      failed) printf '실패: %s\n%s\n' "$label" "$job" >&2; return 1 ;;
      *) sleep 3 ;;
    esac
  done
  printf '대기 시간 초과: %s (작업 %s)\n' "$label" "$id" >&2; return 1
}

me=$(request GET /api/me)
if jq -e '.consentRequired == true' <<<"$me" >/dev/null; then
  echo '시험 계정이 아직 약관에 동의하지 않았어요. 웹에서 한 번 동의한 뒤 다시 실행해 주세요.' >&2; exit 1
fi
printf '시험 계정: 회원 %s\n' "$(jq -r '.id' <<<"$me")"
stamp=$(date '+%Y%m%d-%H%M')

# ---------------------------------------------------------------- 기술·프로젝트
# 이미 있는 기술이면 200 + 기존 항목이 온다. 숙련도 3 — 1(학습 중)이면 판정이 부분 충족으로 내려간다(ADR-0006 B10)
skill_ids=""
for name in Java Spring Redis PostgreSQL; do
  id=$(request POST /api/me/skills "$(jq -n --arg n "$name" '{name:$n, level:3}')" | jq -er '.id')
  skill_ids="$skill_ids${skill_ids:+,}$id"
done

texts=(
'결제 API 의 평균 응답 시간이 1.2초였다. 조회가 잦은 상품·가격 정보를 Redis 캐시로 옮기고 무효화 규칙을 정해 0.3초로 줄였다.

주문 테이블이 3천만 건을 넘자 집계 쿼리가 40초 걸렸다. PostgreSQL 파티셔닝과 부분 인덱스를 도입해 2초로 줄였다.

배포 때마다 5분씩 장애가 났다. 쿠버네티스 readiness probe 와 graceful shutdown 을 설정해 무중단 배포로 바꿨다.'
'정산 배치가 하루 6시간 걸려 아침 업무가 밀렸다. Spring Batch 청크 처리와 병렬 스텝으로 바꿔 40분으로 줄였다.

이벤트 유실로 포인트가 하루 200건씩 틀어졌다. Kafka 트랜잭션 아웃박스 패턴을 적용해 유실을 0건으로 만들었다.

신규 입사자 온보딩에 2주가 걸렸다. 로컬 개발 환경을 Docker Compose 로 묶고 문서를 정리해 3일로 줄였다.'
'검색 응답 p95 가 2.5초였다. 쿼리를 프로파일링해 N+1 을 없애고 커버링 인덱스를 추가해 400ms 로 낮췄다.

외부 결제사 장애가 전체 장애로 번졌다. 서킷 브레이커와 타임아웃을 넣어 결제사 장애 때도 주문 조회는 99.9% 유지했다.

모니터링이 없어 장애를 고객 문의로 알았다. Prometheus·Grafana 대시보드와 알림을 만들어 평균 감지 시간을 30분에서 2분으로 줄였다.'
)
for ((i = 0; i < PROJECTS; i++)); do
  text=${texts[$((i % ${#texts[@]}))]}
  body=$(jq -n --arg t "부하 시험 프로젝트 $stamp-$((i + 1))" --arg r "$text" \
    --argjson s "[$skill_ids]" '{title:$t, rawText:$r, userSkillIds:$s}')
  project=$(request POST /api/projects "$body" | jq -er '.id')
  wait_job "$(request POST "/api/projects/$project/split" | jq -er '.jobId')" "프로젝트 $project 사례 나누기"
  review=$(request GET "/api/projects/$project/review")
  # 확인 질문은 답하지 않고 뺀다 — 시험 데이터라 사례 내용보다 청크 수가 중요하다
  while IFS=$'\t' read -r episode key; do
    request POST "/api/projects/$project/episodes/$episode/clarifications/$key/drop" >/dev/null
  done < <(jq -r '.episodes[] | .id as $id | .clarifications[] | [$id, (.key | @uri)] | @tsv' <<<"$review")
  while read -r episode; do
    request POST "/api/projects/$project/episodes/$episode/confirm" >/dev/null
  done < <(jq -r '.episodes[].id' <<<"$review")
  request POST "/api/projects/$project/confirm" | jq -e '.status == "confirmed"' >/dev/null
  printf '  확정: 프로젝트 %s (사례 %s개)\n' "$project" "$(jq '.episodes | length' <<<"$review")"
done

# ---------------------------------------------------------------- 임베딩 완료 대기
if [[ -n "${MANAGEMENT_URL:-}" ]]; then
  deadline=$((SECONDS + JOB_TIMEOUT))
  until pending=$(curl -fsS "$MANAGEMENT_URL/actuator/prometheus" | awk '/^nerdnam_chunk_pending /{print int($2)}') \
      && [[ "$pending" == 0 ]]; do
    (( SECONDS < deadline )) || { echo "임베딩 대기 청크가 남아 있어요: $pending" >&2; exit 1; }
    printf '  임베딩 대기 청크 %s개…\n' "${pending:-?}"; sleep 10
  done
  echo '  임베딩 완료 (nerdnam_chunk_pending 0)'
else
  printf '  MANAGEMENT_URL 이 없어 임베딩을 %s초 기다립니다 (완료는 확인하지 못함)\n' "$EMBED_WAIT"; sleep "$EMBED_WAIT"
fi

# ---------------------------------------------------------------- 공고·분석
lines=(
'Java 또는 Kotlin 기반 백엔드 개발 경력 3년 이상' 'Spring Boot 로 REST API 를 설계·운영한 경험'
'RDBMS(PostgreSQL·MySQL) 스키마 설계와 쿼리 튜닝 경험' 'Redis 등 캐시를 활용한 성능 개선 경험'
'Kafka 등 메시지 큐 기반 비동기 처리 경험' '대용량 트래픽 서비스 운영 경험'
'Docker·Kubernetes 환경 배포 경험' 'CI/CD 파이프라인 구성 경험' '장애 대응과 원인 분석 경험'
'테스트 코드 작성과 리팩터링 습관' '결제·정산 도메인 이해' '배치 처리(Spring Batch) 경험'
'모니터링·알림(Prometheus·Grafana) 구성 경험' 'AWS 클라우드 운영 경험' 'MSA 설계·전환 경험'
'코드 리뷰 문화에 익숙한 분' 'JPA 성능 문제(N+1 등) 해결 경험' '분산 트랜잭션·아웃박스 패턴 이해'
'검색 엔진(Elasticsearch) 사용 경험' '보안(인증·인가, OWASP) 기본 지식' '컴퓨터공학 또는 관련 전공'
'기술 문서 작성 능력' '주니어 멘토링 경험' 'GraphQL 또는 gRPC 경험' '데이터 파이프라인 구축 경험'
'오픈소스 기여 경험' '애자일·스크럼 협업 경험' '영어 기술 문서 독해' '서비스 SLO 정의·운영 경험'
'온콜 운영 경험'
)
required_n=$(( (REQUIREMENTS * 3 + 4) / 5 ))   # 약 60% 필수, 나머지 우대
posting_ids=()
for ((p = 0; p < POSTINGS; p++)); do
  picked=()
  for ((k = 0; k < REQUIREMENTS; k++)); do picked+=("${lines[$(((p * 7 + k) % ${#lines[@]}))]}"); done
  body=$(printf '%s\n' "${picked[@]}" | jq -R . | jq -s --arg c "부하 시험 회사 $stamp-$((p + 1))" --argjson n "$required_n" \
    '{companyName:$c, positionName:"백엔드 개발자", required:.[:$n], preferred:.[$n:],
      tasks:["결제·주문 API 개발과 운영","대용량 데이터 처리 개선"]}')
  posting=$(request POST /api/job-postings "$body" | jq -er '.id')
  wait_job "$(request POST "/api/job-postings/$posting/analyses" | jq -er '.jobId')" "공고 $posting 분석"
  posting_ids+=("$posting")
done

echo
echo "만든 것: 프로젝트 $PROJECTS 개, 공고 $POSTINGS 개(요건 $REQUIREMENTS 개, 분석 완료). 시험 뒤 탈퇴하면 모두 지워집니다."
echo "POSTING_IDS=$(IFS=,; echo "${posting_ids[*]}")"
