#!/bin/bash
# GitHub Actions가 SSM RunShellScript로 EC2(moment-prod-api)에서 실행하는 배포 스크립트.
# 환경변수 TAG(ECR 이미지 태그 = 커밋 해시) 필수. 문서 15장 표준 절차의 스크립트화.
set -euo pipefail

REGISTRY=151170151164.dkr.ecr.ap-northeast-2.amazonaws.com
IMAGE=$REGISTRY/moment-server:$TAG

aws ecr get-login-password --region ap-northeast-2 \
  | docker login --username AWS --password-stdin "$REGISTRY"
docker pull "$IMAGE"
docker rm -f moment-api || true
docker run -d --name moment-api \
  --env-file /etc/moment/moment.env \
  --publish 127.0.0.1:8080:8080 \
  --log-opt max-size=10m --log-opt max-file=3 \
  "$IMAGE"

# 기동 대기 후 health 확인 (최대 90초). 실패 시 로그를 남기고 배포 실패 처리.
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1:8080/actuator/health | grep -q '"UP"'; then
    # docker rm 후 새 컨테이너는 재시작 정책이 초기화되므로 매번 재적용한다
    docker update --restart unless-stopped moment-api
    docker image prune -f > /dev/null
    echo "DEPLOY_OK $TAG"
    exit 0
  fi
  sleep 3
done

echo "DEPLOY_FAILED: health check timeout" >&2
docker logs --tail 50 moment-api >&2 || true
exit 1
