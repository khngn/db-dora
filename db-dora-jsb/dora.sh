#!/bin/bash
# dora.sh
thisDir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app="$(basename "$thisDir")"
# ###########################################################################################
up() {(
  set -euo pipefail
  # #############################################################
  cd "$thisDir"/docker
  docker compose -f dora.docker-compose.yml up -d
)}
# ###########################################################################################
down() {(
  set -euo pipefail
  # #############################################################
  cd "$thisDir"/docker
  docker compose -f dora.docker-compose.yml down
)}
# ###########################################################################################
restart() {(
  down
  up
)}
# ###########################################################################################
reset() {(
  set -euo pipefail
  # #############################################################
  cd "$thisDir"/docker
  docker compose -f dora.docker-compose.yml down -v
  up  
)}
# ###########################################################################################
migrate_cbs(){(
  set -euo pipefail
  # #############################################################
  cd "C:/Developer/repo/cbs-common/cbs-database-service"
  npm run apply:migration:local
)}
# ###########################################################################################
# sync lambda zip to s3
lambda() {(
  set -euo pipefail
  # #############################################################
  cd "$thisDir"
  mvn clean package -DskipTests
  # #############################################################
  cd "$thisDir/target" && pwd
  mkdir -pv "dist"
  # Rename to zip to avoid being blocked by HA proxy
  cp -v "db-dora-jsb.jar" "dist/lambda.zip"
  # cp "db-dora-jsb-openapi.json" "dist/openapi.json"
  jq . "db-dora-jsb-openapi.json" > "C:/Developer/repo/atlas-engine/sample-project/infrastructure/CMDB/devkn/segments/CBS/kndd/config/kndd-api-private/kndd-openapi.json"
  ls -lhF "dist/lambda.zip"
  # #############################################################
  aws_load_profile
  aws s3 sync "dist/" "s3://$KN_CBS_BUCKET/apps/$app/dist/" --delete
)}
# ###########################################################################################
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  func="${1:-lambda}"
  shift
  "$func" "$@"
fi