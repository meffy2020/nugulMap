#!/usr/bin/env bash
set -euo pipefail

# Compile the unchanged release variant using the existing LOCAL DEBUG certificate.
# Outputs stay in a private temporary copy and are NEVER upload candidates.
ANDROID_PROJECT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/nugulmap-NOT-FOR-UPLOAD.XXXXXX")"
chmod 700 "$VERIFY_ROOT"
DEBUG_CERTIFICATE="${HOME}/.android/debug.keystore"
if [[ ! -f "$DEBUG_CERTIFICATE" ]]; then
  echo "Existing debug keystore missing; no upload or temporary key will be generated." >&2
  exit 1
fi
python3 - "$ANDROID_PROJECT" "$VERIFY_ROOT" <<'PY'
import shutil, sys
from pathlib import Path
source, target = map(Path, sys.argv[1:])
shutil.copytree(source, target / "project", ignore=shutil.ignore_patterns(
    "build", ".gradle", ".kotlin", ".DS_Store", "store-assets", "docs", "__pycache__"
))
(target / "NOT-FOR-UPLOAD.txt").write_text(
    "LOCAL VERIFICATION ONLY. Release variant signed with existing debug certificate. "
    "Not a Play upload artifact. Shipping project signing configuration is unchanged.\n"
)
PY
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"
export ANDROID_HOME="${ANDROID_HOME:-${HOME}/Library/Android/sdk}"
ANDROID_KEYSTORE_PATH="$DEBUG_CERTIFICATE" \
ANDROID_KEYSTORE_PASSWORD=android ANDROID_KEY_ALIAS=androiddebugkey ANDROID_KEY_PASSWORD=android \
  "$VERIFY_ROOT/project/gradlew" -p "$VERIFY_ROOT/project" \
    :app:assembleRelease :app:bundleRelease --console=plain > "$VERIFY_ROOT/build.log" 2>&1 || {
      tail -n 60 "$VERIFY_ROOT/build.log" >&2
      echo "Isolated verification failed: $VERIFY_ROOT" >&2
      exit 1
    }
printf 'Isolated release build succeeded, NOT FOR UPLOAD: %s\n' "$VERIFY_ROOT"
