#!/usr/bin/env bash
# Air cloud startup script for teamcity-s3-storage.
#
# Prepares the toolchain the build needs (Temurin JDK 17 for Gradle 8.14,
# yarn for the s3-artifact-storage-ui module) and, on warmup runs, primes
# the Gradle and yarn caches by building the plugin.
#
# Gradle resolves TeamCity SNAPSHOT artifacts from packages.jetbrains.team,
# which requires the personal Space credentials provided via the
# ORG_GRADLE_PROJECT_spaceUsername / ORG_GRADLE_PROJECT_spacePassword
# environment variables (declared as secrets in the environment config).
set -euo pipefail

log() { echo "[air-startup] $(date '+%H:%M:%S') $*"; }

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
JDK_DIR="$HOME/.air-jdk"
NPM_PREFIX="$HOME/.npm-global"
ENV_FILE="$HOME/.air-env.sh"

if [ "${AIR_STARTUP_MODE:-}" = warmup ]; then WARMUP=1; else WARMUP=; fi
log "mode=${AIR_STARTUP_MODE:-unset} repo=$REPO_ROOT"

# --- JDK 17 (Gradle 8.14 cannot run on the preinstalled JDK 25) ---
if [ ! -x "$JDK_DIR/bin/java" ]; then
  log "installing Temurin JDK 17 into $JDK_DIR"
  mkdir -p "$JDK_DIR"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o /tmp/air-jdk17.tar.gz
  tar -xzf /tmp/air-jdk17.tar.gz -C "$JDK_DIR" --strip-components=1
  rm -f /tmp/air-jdk17.tar.gz
  log "JDK installed: $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
else
  log "JDK already present: $("$JDK_DIR/bin/java" -version 2>&1 | head -1)"
fi

# --- yarn (the UI module builds with yarn; global npm prefix is not writable) ---
if [ ! -x "$NPM_PREFIX/bin/yarn" ]; then
  log "installing yarn into $NPM_PREFIX"
  npm install -g --prefix "$NPM_PREFIX" yarn
fi

# --- make JAVA_HOME/PATH available to the agent's shells ---
# Java ignores http(s)_proxy env vars, so JAVA_TOOL_OPTIONS must carry the
# egress proxy for Gradle/wrapper downloads. Derived at source time so a
# changed proxy address after a snapshot restore still works.
cat > "$ENV_FILE" <<EOF
export JAVA_HOME="$JDK_DIR"
export PATH="$JDK_DIR/bin:$NPM_PREFIX/bin:\$PATH"
if [ -z "\${JAVA_TOOL_OPTIONS:-}" ] && [ -n "\${https_proxy:-\${HTTPS_PROXY:-}}" ]; then
  _p="\${https_proxy:-\$HTTPS_PROXY}"; _p="\${_p#*://}"; _p="\${_p%/}"
  _h="\${_p%%:*}"; _o="\${_p##*:}"
  export JAVA_TOOL_OPTIONS="-Dhttp.proxyHost=\$_h -Dhttp.proxyPort=\$_o -Dhttps.proxyHost=\$_h -Dhttps.proxyPort=\$_o -Dhttp.nonProxyHosts=localhost|127.0.0.1"
  unset _p _h _o
fi
EOF

SOURCE_LINE="[ -f \"$ENV_FILE\" ] && . \"$ENV_FILE\" # air-env"
add_hook() {
  local file="$1"
  if ! grep -qs '# air-env' "$file"; then
    echo "$SOURCE_LINE" >> "$file"
    log "hooked $file"
  fi
}
login_profile="$HOME/.profile"
for f in "$HOME/.bash_profile" "$HOME/.bash_login" "$HOME/.profile"; do
  if [ -f "$f" ]; then login_profile="$f"; break; fi
done
add_hook "$login_profile"
add_hook "$HOME/.bashrc"

# shellcheck disable=SC1090
. "$ENV_FILE"

# --- warm the build caches (snapshot-baking warmup run only) ---
warm_build() {
  cd "$REPO_ROOT"
  log "priming Gradle cache: ./gradlew assemble"
  ./gradlew --no-daemon assemble
  # The committed yarn.lock lags package.json, so a frozen install fails;
  # install like the Gradle build does, then restore the lockfile so the
  # workspace stays clean.
  log "priming UI dependencies: yarn install"
  (cd s3-artifact-storage-ui && yarn install && git checkout -- yarn.lock)
  log "building UI bundle: yarn build"
  (cd s3-artifact-storage-ui && yarn build)
}

# --- healthcheck: assert the environment works the way a real task needs ---
healthcheck() {
  log "healthcheck: verifying JDK"
  "$JDK_DIR/bin/java" -version 2>&1 | grep -q 'version "17' || { log "healthcheck FAILED: JDK 17 not usable"; return 1; }
  log "healthcheck: verifying yarn"
  "$NPM_PREFIX/bin/yarn" --version >/dev/null || { log "healthcheck FAILED: yarn not usable"; return 1; }
  cd "$REPO_ROOT"
  log "healthcheck: verifying Gradle build (compileJava smoke)"
  ./gradlew --no-daemon compileJava || { log "healthcheck FAILED: Gradle compile smoke failed"; return 1; }
  log "healthcheck: verifying plugin artifact was assembled"
  ls s3-artifact-storage-server/build/distributions/s3-artifact-storage.zip >/dev/null \
    || { log "healthcheck FAILED: plugin zip missing"; return 1; }
  log "healthcheck OK"
}

if [ -n "$WARMUP" ]; then
  warm_build
  healthcheck
fi

log "startup done"
