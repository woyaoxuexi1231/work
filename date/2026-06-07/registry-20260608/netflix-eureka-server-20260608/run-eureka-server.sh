#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKERFILE_NAME="${DOCKERFILE_NAME:-Dockerfile}"
IMAGE_NAME="${IMAGE_NAME:-netflix-eureka-server:latest}"
CONTAINER_NAME="${CONTAINER_NAME:-netflix-eureka-server}"
HOST_PORT="${HOST_PORT:-10001}"
CONTAINER_PORT="${CONTAINER_PORT:-10001}"
RESTART_POLICY="${RESTART_POLICY:-unless-stopped}"
TZ="${TZ:-Asia/Shanghai}"

if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] docker is not installed or not in PATH"
  exit 1
fi

if [[ ! -f "${SCRIPT_DIR}/${DOCKERFILE_NAME}" ]]; then
  echo "[ERROR] Dockerfile not found: ${SCRIPT_DIR}/${DOCKERFILE_NAME}"
  exit 1
fi

resolve_jar_file() {
  if [[ -n "${JAR_FILE:-}" ]]; then
    if [[ ! -f "${SCRIPT_DIR}/${JAR_FILE}" ]]; then
      echo "[ERROR] JAR_FILE does not exist: ${SCRIPT_DIR}/${JAR_FILE}"
      exit 1
    fi
    printf '%s\n' "${JAR_FILE}"
    return
  fi

  mapfile -t jars < <(
    find "${SCRIPT_DIR}" -maxdepth 2 -type f -name "*.jar" \
      ! -name "original-*.jar" \
      ! -name "*-sources.jar" \
      ! -name "*-javadoc.jar" \
      -printf "%P\n"
  )

  if [[ ${#jars[@]} -eq 0 ]]; then
    echo "[ERROR] No runnable jar found in ${SCRIPT_DIR} (or depth<=2)"
    echo "        Put jar next to this script, or specify JAR_FILE explicitly."
    echo "        Example: JAR_FILE=target/netflix-eureka-server.jar bash run-eureka-server.sh"
    exit 1
  fi

  if [[ ${#jars[@]} -gt 1 ]]; then
    echo "[ERROR] Multiple jars found; please specify JAR_FILE explicitly:"
    printf '        %s\n' "${jars[@]}"
    exit 1
  fi

  printf '%s\n' "${jars[0]}"
}

JAR_FILE_RELATIVE="$(resolve_jar_file)"

echo "[INFO] Dockerfile: ${SCRIPT_DIR}/${DOCKERFILE_NAME}"
echo "[INFO] Jar file: ${SCRIPT_DIR}/${JAR_FILE_RELATIVE}"
echo "[INFO] Building image: ${IMAGE_NAME}"
echo "[INFO] Timezone: ${TZ}"

docker build \
  -f "${SCRIPT_DIR}/${DOCKERFILE_NAME}" \
  --build-arg "JAR_FILE=${JAR_FILE_RELATIVE}" \
  -t "${IMAGE_NAME}" \
  "${SCRIPT_DIR}"

if docker ps -a --format '{{.Names}}' | grep -Fxq "${CONTAINER_NAME}"; then
  echo "[INFO] Removing existing container: ${CONTAINER_NAME}"
  docker rm -f "${CONTAINER_NAME}"
fi

echo "[INFO] Starting container: ${CONTAINER_NAME}"
docker run -d \
  --name "${CONTAINER_NAME}" \
  --restart "${RESTART_POLICY}" \
  -e "TZ=${TZ}" \
  -p "${HOST_PORT}:${CONTAINER_PORT}" \
  "${IMAGE_NAME}"

echo "[INFO] Container started successfully"
echo "[INFO] Port mapping: ${HOST_PORT} -> ${CONTAINER_PORT}"
echo "[INFO] Logs: docker logs -f ${CONTAINER_NAME}"
echo "[INFO] Stop: docker stop ${CONTAINER_NAME}"

