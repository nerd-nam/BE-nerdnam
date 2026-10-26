#!/usr/bin/env bash
# 로컬 서버 진단. --flow는 테스트 프로젝트·공고를 새로 만들고 결과를 남긴다.
# 준비: local 헤더 인증, nerdnam.ai.stub=false, 별도 FastAPI 서버.
set -euo pipefail

BE_URL=${BE_URL:-http://localhost:8099}
AI_URL=${AI_URL:-http://localhost:8000}
FE_URL=${FE_URL:-http://localhost:3000}
MEMBER_ID=${MEMBER_ID:-1}
JOB_TIMEOUT=${JOB_TIMEOUT:-900}
for url in "$BE_URL" "$AI_URL" "$FE_URL"; do
  if [[ ! "$url" =~ ^http://(localhost|127\.0\.0\.1):[0-9]+$ ]]; then
    echo '이 스크립트는 localhost의 HTTP 서버에서만 실행해 주세요.' >&2
    exit 1
  fi
done
[[ "$MEMBER_ID" =~ ^[0-9]+$ && "$JOB_TIMEOUT" =~ ^[1-9][0-9]*$ ]] || exit 1
if [[ ${1:-} != '' && ${1:-} != --flow ]]; then
  echo '사용법: bash scripts/local-smoke.sh [--flow]' >&2
  exit 1
fi
command -v curl >/dev/null
command -v jq >/dev/null

request() {
  local method=$1 path=$2 body=${3:-} response status
  local args=(-sS --connect-timeout 3 --max-time 30 -X "$method"
    -H "X-Member-Id: $MEMBER_ID" -H 'Content-Type: application/json')
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
  local id=$1 label=$2 job state deadline=$((SECONDS + JOB_TIMEOUT))
  while (( SECONDS < deadline )); do
    job=$(request GET "/api/ai-jobs/$id")
    state=$(jq -er '.status' <<<"$job")
    case "$state" in
      completed) printf '통과: %s (작업 %s)\n' "$label" "$id"; return 0 ;;
      failed) printf '실패: %s\n%s\n' "$label" "$job" >&2; return 1 ;;
      in_progress) sleep 2 ;;
      *) printf '알 수 없는 작업 상태: %s\n' "$job" >&2; return 1 ;;
    esac
  done
  printf '대기 시간 초과: %s (작업 %s, 취소하지 않음)\n' "$label" "$id" >&2
  return 1
}

echo 'AI 상태:'
health=$(curl -fsS --connect-timeout 3 --max-time 5 "$AI_URL/health")
jq . <<<"$health"
# 떠 있는 AI 서버의 계약 버전이 BE 가 가진 복사본과 같은지 먼저 본다.
# 다르면 아래 흐름이 통과해도 "지금 계약대로 동작한다"는 증명이 못 된다 (복사본 어긋남은 조용히 지나간다).
ai_version=$(jq -er '.info.version' < <(curl -fsS --connect-timeout 3 --max-time 10 "$AI_URL/openapi.json"))
copy_version=$(sed -n 's/^  version: *//p' src/test/resources/contracts/api-ai.openapi.yaml | head -1)
printf 'AI 계약: 서버 %s · BE 복사본 %s\n' "$ai_version" "$copy_version"
if [[ "$ai_version" != "$copy_version" ]]; then
  echo "계약 버전이 다릅니다. SPEC 에서 api-ai.openapi.yaml 을 다시 복사하고 ApiAiContractTest 를 돌려 주세요." >&2
  echo "일부러 다른 버전에 붙여 보는 중이면 ALLOW_CONTRACT_MISMATCH=1 을 붙여 주세요." >&2
  [[ ${ALLOW_CONTRACT_MISMATCH:-} == 1 ]] || exit 1
fi

echo 'BE 상태:'
curl -fsS --connect-timeout 3 --max-time 5 "$BE_URL/actuator/health" | jq -e '.status == "UP"'
echo 'FE 응답:'
if ! curl -fsS --connect-timeout 3 --max-time 60 -o /dev/null "$FE_URL/"; then
  echo 'FE 미기동 또는 화면 오류. BE 흐름과 별도로 확인해 주세요.' >&2
fi
echo '로컬 개발 회원 인증:'
request GET /api/me | jq '{id, consentRequired}'

if [[ ${1:-} != --flow ]]; then
  echo '진단 끝. 새 테스트 데이터를 만드는 흐름 검증은 --flow로 실행해 주세요.'
  exit 0
fi
# 확인 질문을 자동으로 빼는 흐름이므로 가짜 응답 모드에서만 실행한다.
jq -e '.models.llm == "stub" and .models.embedding == "stub"' <<<"$health" >/dev/null || {
  echo '--flow는 AI_MODE=stub 전용입니다. 실제 모델은 생성 결과를 직접 검토해 주세요.' >&2
  exit 1
}
stamp=$(date '+%Y%m%d-%H%M%S')-$$
skill=$(request POST /api/me/skills '{"name":"Redis"}' | jq -er '.id')
project=$(request POST /api/projects "$(jq -n --arg title "로컬 연결 테스트 $stamp" --argjson skill "$skill" \
  '{title:$title,rawText:"결제 API 응답이 1.2초였다. Redis 캐시를 직접 구현해 0.3초로 줄였다.",userSkillIds:[$skill]}')" | jq -er '.id')
printf '생성한 테스트 프로젝트: %s\n' "$project"
job=$(request POST "/api/projects/$project/split" | jq -er '.jobId')
wait_job "$job" '사례 재구성'
review=$(request GET "/api/projects/$project/review")
jq -e '.episodes | length > 0' <<<"$review" >/dev/null
# 확인 질문 하나는 "답하기"로 처리한다 (mode=episode). 사례를 AI 서버로 되돌려 보내는 경로라
# 계약이 어긋나면 여기서만 드러난다 — 2026-10-26 에 role=null 로 422 가 났던 자리다.
answer_target=$(jq -r 'first(.episodes[] | select(.clarifications | length > 0) | [.id, (.clarifications[0].key | @uri), .clarifications[0].question] | @tsv) // empty' <<<"$review")
if [[ -n "$answer_target" ]]; then
  IFS=$'\t' read -r episode key question <<<"$answer_target"
  printf '확인 질문에 답하기: 사례 %s (%s)\n' "$episode" "$question"
  job=$(request POST "/api/projects/$project/episodes/$episode/clarifications/$key/answer" \
    '{"answer":"응답 시간을 1.2초에서 0.3초로 줄였어요."}' | jq -er '.jobId')
  wait_job "$job" '확인 질문 답하기'
  review=$(request GET "/api/projects/$project/review")
fi
while IFS=$'\t' read -r episode key; do
  request POST "/api/projects/$project/episodes/$episode/clarifications/$key/drop" >/dev/null
done < <(jq -r '.episodes[] | .id as $id | .clarifications[] | [$id, (.key | @uri)] | @tsv' <<<"$review")
while read -r episode; do
  request POST "/api/projects/$project/episodes/$episode/confirm" >/dev/null
done < <(jq -r '.episodes[].id' <<<"$review")
request POST "/api/projects/$project/confirm" | jq -e '.status == "confirmed"' >/dev/null
posting=$(request POST /api/job-postings "$(jq -n --arg company "로컬 테스트 $stamp" \
  '{companyName:$company,positionName:"백엔드 개발자",required:["Redis 캐시 개발 경험"],tasks:["결제 API 개발"]}')" | jq -er '.id')
printf '생성한 테스트 공고: %s\n' "$posting"
job=$(request POST "/api/job-postings/$posting/analyses" | jq -er '.jobId')
wait_job "$job" '적합도 분석'
request GET "/api/job-postings/$posting/report" | jq -e '.status == "completed" and (.matches | length > 0)' >/dev/null
job=$(request POST "/api/job-postings/$posting/guides/motive" | jq -er '.jobId')
wait_job "$job" '지원동기 작성 방향'
request GET "/api/job-postings/$posting/guides/motive" | jq -e '.status == "completed" and .content != null' >/dev/null
printf 'BE 흐름 완료. 프로젝트=%s, 공고=%s (테스트 데이터는 남겨 둠)\n' "$project" "$posting"
echo 'BE의 nerdnam.ai.stub=false 설정과 FastAPI 접근 로그를 함께 확인해야 실제 서버 왕복을 증명할 수 있습니다.'
echo 'AI_MODE=stub 결과로 검색 품질·모델 성능·FE 전체 연동을 판단하지 않습니다.'
