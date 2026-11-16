#!/usr/bin/env bash
# Runs the Android integration or performance tests against a local test backend.
#
#   testing/run-android-tests.sh integration
#   testing/run-android-tests.sh performance
#
# Needs: .NET 10 SDK, Docker running, adb, one emulator or phone connected,
# and the ImmatureBackend repo next to this one (or BACKEND_ROOT=/path).
# See testing/README.md.
set -euo pipefail

SUITE="${1:?usage: $0 integration|performance}"
case "$SUITE" in integration|performance) ;; *) echo "suite must be integration or performance" >&2; exit 2 ;; esac
PORT="${PORT:-8000}"
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
BACKEND_ROOT="$(cd "${BACKEND_ROOT:-$REPO/../ImmatureBackend}" && pwd)/"
BUILD="$HERE/build"; RESULTS="$BUILD/results"; SESSION="$BUILD/local-api.json"
mkdir -p "$RESULTS"; rm -f "$SESSION"

ADB="$(command -v adb || echo "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb")"
command -v dotnet >/dev/null || { echo ".NET 10 SDK not found" >&2; exit 1; }
docker info >/dev/null 2>&1 || { echo "Docker is not running" >&2; exit 1; }
[ "$("$ADB" devices | grep -c $'\tdevice$')" = 1 ] || { echo "Connect exactly one emulator or phone" >&2; exit 1; }

ANDROID_COMMIT="$(git -C "$REPO" rev-parse --short HEAD)"
BACKEND_COMMIT="$(git -C "$BACKEND_ROOT" rev-parse --short HEAD)"
BACKEND_BRANCH="$(git -C "$BACKEND_ROOT" rev-parse --abbrev-ref HEAD)"
DEVICE="$("$ADB" shell getprop ro.product.model | tr -d '\r'), Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r')"

echo "Building local test backend (backend $BACKEND_BRANCH@$BACKEND_COMMIT)..."
dotnet build "$HERE/local-api-host/LocalApiHost.csproj" -c Release -p:BackendRoot="$BACKEND_ROOT" -o "$BUILD/host" > "$RESULTS/host-build.log"

dotnet "$BUILD/host/LocalApiHost.dll" --port "$PORT" --out "$SESSION" > "$RESULTS/host-$SUITE.log" 2>&1 &
HOST_PID=$!
cleanup() {
  "$ADB" reverse --remove "tcp:$PORT" >/dev/null 2>&1 || true
  kill "$HOST_PID" 2>/dev/null || true
  rm -f "$SESSION"
}
trap cleanup EXIT

echo "Waiting for the local backend (first run pulls postgres:17-alpine)..."
for _ in $(seq 150); do
  [ -f "$SESSION" ] && break
  kill -0 "$HOST_PID" 2>/dev/null || { echo "Local backend exited, see $RESULTS/host-$SUITE.log" >&2; exit 1; }
  sleep 2
done
[ -f "$SESSION" ] || { echo "Local backend did not start within 5 minutes" >&2; exit 1; }

json_field() { sed -E "s/.*\"$1\":\"([^\"]*)\".*/\1/" "$SESSION"; }
BASE_URL="$(json_field baseUrl)"

"$ADB" reverse "tcp:$PORT" "tcp:$PORT" >/dev/null
"$ADB" logcat -c

PACKAGE="com.grainmvp.android.$SUITE"
STARTED="$(date '+%Y-%m-%d %H:%M')"
set +e
(cd "$REPO" && ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package="$PACKAGE" \
  -Pandroid.testInstrumentationRunnerArguments.baseUrl="$BASE_URL" \
  -Pandroid.testInstrumentationRunnerArguments.captureToken="$(json_field captureToken)" \
  -Pandroid.testInstrumentationRunnerArguments.dashboardToken="$(json_field dashboardToken)")
EXIT_CODE=$?
set -e

LOG="$RESULTS/$SUITE-logcat.txt"
"$ADB" logcat -d -v time -s GrainTest:I GrainPerf:I GrainPerf:W TestRunner:I > "$LOG"

{
  echo "# Android $SUITE tests"
  echo
  echo "- Run: $STARTED"
  echo "- Build: android@$ANDROID_COMMIT, backend $BACKEND_BRANCH@$BACKEND_COMMIT"
  echo "- Environment: local test backend ($BASE_URL, real API + PostgreSQL in Docker, local image store instead of Cloudinary) + $DEVICE"
  echo "- Command: testing/run-android-tests.sh $SUITE"
  echo "- Gradle result: $([ $EXIT_CODE = 0 ] && echo 'all tests passed' || echo "failures (exit $EXIT_CODE)")"
  echo "- JUnit report: app/build/reports/androidTests/connected/debug/index.html"
  echo
  echo '## Evidence'
  echo
  echo '```'
  grep -E 'GrainTest|GrainPerf' "$LOG" || true
  echo '```'
} > "$RESULTS/$SUITE-summary.md"
echo "Summary: $RESULTS/$SUITE-summary.md"
exit $EXIT_CODE
