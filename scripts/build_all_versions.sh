#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JDK_17="${SKYLOGISTICS_JAVA_17_HOME:-/Users/bytedance/.gradle/jdks/eclipse_adoptium-17-aarch64-os_x/jdk-17.0.19+10/Contents/Home}"
JDK_21="${SKYLOGISTICS_JAVA_21_HOME:-/Users/bytedance/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.9+10/Contents/Home}"
JDK_25="${SKYLOGISTICS_JAVA_25_HOME:-/Users/bytedance/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x/jdk-25.0.3+9/Contents/Home}"
TASKS=("$@")

if [ "${#TASKS[@]}" -eq 0 ]; then
  TASKS=(clean build)
fi

echo "==> Building 1.21.1"
(
  cd "$ROOT_DIR/versions/1.21.1"
  env JAVA_HOME="$JDK_21" PATH="$JDK_21/bin:/usr/bin:/bin:/usr/sbin:/sbin" \
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

  env JAVA_HOME="$JDK_17" PATH="$JDK_17/bin:/usr/bin:/bin:/usr/sbin:/sbin" \
    ./gradlew "${args[@]}" "${TASKS[@]}"
)

echo "==> Building 26.1.2"
(
  cd "$ROOT_DIR/versions/26.1.2"
  env JAVA_HOME="$JDK_25" PATH="$JDK_25/bin:/usr/bin:/bin:/usr/sbin:/sbin" \
    ./gradlew --no-daemon "${TASKS[@]}"
)
