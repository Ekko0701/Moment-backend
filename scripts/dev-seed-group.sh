#!/bin/bash
# 로컬 dev 서버(H2, 기본 http://localhost:8080)에 그룹 스페이스 확인용 데이터를 만든다.
# 운영 서버에는 쓰지 않는다. 계정은 로컬 테스트 전용 값이다.
#
#   방장  owner@moment.local   / devpass123  — 1:1(파트너)과 그룹 "주말 산책"의 방장
#   파트너 partner@moment.local / devpass123  — 방장과 1:1
#   멤버  member@moment.local  / devpass123  — 그룹 멤버
#
# 사용: scripts/dev-seed-group.sh [baseUrl]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
PASS="devpass123"

json() { python3 -c "import sys,json; d=json.load(sys.stdin); print(eval('d' + sys.argv[1]))" "$1"; }

token() {
  local email="$1" nick="$2" res
  res=$(curl -s -X POST "$BASE/v1/auth/email/signup" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"password\":\"$PASS\",\"nickname\":\"$nick\"}")
  if ! echo "$res" | grep -q '"success":true'; then
    res=$(curl -s -X POST "$BASE/v1/auth/email/login" -H 'Content-Type: application/json' \
      -d "{\"email\":\"$email\",\"password\":\"$PASS\"}")
  fi
  echo "$res" | json "['data']['accessToken']"
}

call() { # method path token [body]
  if [ $# -ge 4 ]; then
    curl -s -X "$1" "$BASE$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json' -d "$4"
  else
    curl -s -X "$1" "$BASE$2" -H "Authorization: Bearer $3" -H 'Content-Type: application/json'
  fi
}

OWNER=$(token owner@moment.local "방장")
PARTNER=$(token partner@moment.local "지은")
MEMBER=$(token member@moment.local "민수")

# 1:1 연결: 방장이 코드 발급 → 파트너가 요청 → 방장이 수락
CODE=$(call POST /v1/invite-codes "$OWNER" | json "['data']['code']")
call POST /v1/invitations "$PARTNER" "{\"toUserId\":null,\"code\":\"$CODE\"}" > /dev/null
INV=$(call GET "/v1/invitations?received=true" "$OWNER" | json "['data'][0]['id']")
call PATCH "/v1/invitations/$INV" "$OWNER" '{"action":"accept"}' > /dev/null
PAIR=$(call GET /v1/spaces/me "$OWNER" | json "['data'][0]['id']")

# 그룹: 방장이 만들고 멤버·파트너가 참여
CREATED=$(call POST /v1/groups "$OWNER" '{"name":"주말 산책"}')
GROUP=$(echo "$CREATED" | json "['data']['space']['id']")
GCODE=$(echo "$CREATED" | json "['data']['inviteCode']")
call POST /v1/groups/join "$MEMBER" "{\"code\":\"$GCODE\"}" > /dev/null
call POST /v1/groups/join "$PARTNER" "{\"code\":\"$GCODE\"}" > /dev/null

# 모먼트
call POST /v1/moments "$PARTNER" "{\"spaceId\":\"$PAIR\",\"imageUrl\":null,\"text\":\"오늘 저녁에 산책 어때? 🌙\"}" > /dev/null
call POST /v1/moments "$MEMBER" "{\"spaceId\":\"$GROUP\",\"imageUrl\":null,\"text\":\"토요일 10시 한강 어때요\"}" > /dev/null
call POST /v1/moments "$PARTNER" "{\"spaceId\":\"$GROUP\",\"imageUrl\":null,\"text\":\"좋아요! 커피 챙겨갈게요 ☕\"}" > /dev/null

echo "seeded: pair=$PAIR group=$GROUP groupCode=$GCODE"
