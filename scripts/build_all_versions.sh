#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEFAULT_JAVA_HOME="/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home"
TASKS=("$@")

if [ "${#TASKS[@]}" -eq 0 ]; then
  TASKS=(clean build)
fi

if [ -d "${SKYLOGISTICS_JAVA_HOME:-$DEFAULT_JAVA_HOME}" ]; then
  export JAVA_HOME="${SKYLOGISTICS_JAVA_HOME:-$DEFAULT_JAVA_HOME}"
  export PATH="$JAVA_HOME/bin:/usr/bin:/bin:/usr/sbin:/sbin"
fi

echo "==> Building 1.21.1"
(
  cd "$ROOT_DIR/versions/1.21.1"
  ./gradlew --no-daemon "${TASKS[@]}"
)

echo "==> Building 1.20.1"
(
  cd "$ROOT_DIR/versions/1.20.1"
  args=(--no-daemon)

  offline_repo="${SKYLOGISTICS_OFFLINE_REPO:-/private/tmp/skylogistics-offline-maven}"
  if [ -d "$offline_repo" ]; then
    args+=(--offline "-Dskylogistics.offlineRepo=$offline_repo")
  fi

  jade_api="${SKYLOGISTICS_JADE_API_JAR:-/private/tmp/Jade-1.20.1-Forge-11.13.2.jar}"
  if [ -f "$jade_api" ]; then
    args+=("-Dskylogistics.jadeApiJar=$jade_api")
  fi

  ./gradlew "${args[@]}" "${TASKS[@]}"
)
