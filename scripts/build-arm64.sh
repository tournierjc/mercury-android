#!/usr/bin/env bash
# Build + tests sur un hôte aarch64 (DGX Spark, VM arm64…).
#
# Google ne publie aapt2 qu'en x86_64 : sur arm64, tout build Android échoue en
# "Exec format error". Ce script construit une image avec l'aapt2 officiel
# exécuté sous qemu-user, puis lance Gradle dedans.
#
#   ./scripts/build-arm64.sh                      # tests unitaires + APK debug
#   ./scripts/build-arm64.sh :app:assembleRelease # tâche Gradle arbitraire
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
IMAGE="mercury-android-builder:local"
GRADLE_CACHE="${GRADLE_CACHE:-$HOME/.gradle-android}"

docker build -f "$ROOT/docker/Dockerfile.builder" -t "$IMAGE" "$ROOT"

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
    TASKS=(:core:test :app:assembleDebug)
fi

mkdir -p "$GRADLE_CACHE"
docker run --rm \
    -u "$(id -u):$(id -g)" \
    -v "$ROOT:/w" \
    -v "$GRADLE_CACHE:/g" \
    -w /w \
    -e GRADLE_USER_HOME=/g \
    -e HOME=/tmp \
    -e ANDROID_HOME=/opt/android-sdk-linux \
    "$IMAGE" \
    ./gradlew --no-daemon --console=plain \
        -Pandroid.aapt2FromMavenOverride=/opt/aapt2-qemu/bin/aapt2 \
        "${TASKS[@]}"
