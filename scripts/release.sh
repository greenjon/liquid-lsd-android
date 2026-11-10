#!/usr/bin/env bash
# Builds, signs (local key, key.properties) and publishes a release on GitHub.
#   scripts/release.sh 0.1.1 [notes-file]
# Bumps versionName and versionCode in app/build.gradle.kts, runs the tests, builds the signed APK, checks the
# signature, commits, tags vVERSION, pushes, and creates the GitHub release with the APK. 0.x releases are marked
# pre-release. The tag's Release workflow then only re-checks the build (no signing secrets in CI).
set -euo pipefail
cd "$(dirname "$0")/.."
ver="${1:?usage: scripts/release.sh VERSION [notes-file]}"
notes="${2:-}"
[[ "$ver" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 0.1.1" >&2; exit 1; }
[ -f key.properties ] || { echo "no key.properties: the release key is needed to sign" >&2; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "working tree not clean" >&2; exit 1; }
git rev-parse "v$ver" >/dev/null 2>&1 && { echo "tag v$ver exists" >&2; exit 1; }

code=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
cur=$(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts)
if [ "$cur" != "$ver" ]; then
  code=$((code + 1))
  sed -i -E "s/versionCode = [0-9]+/versionCode = $code/; s/versionName = \"[^\"]+\"/versionName = \"$ver\"/" app/build.gradle.kts
fi

./gradlew --console=plain -q testDebugUnitTest assembleRelease
apk=app/build/outputs/apk/release/app-release.apk
store=$(grep -oP '^storeFile=\K.*' key.properties)
want=$(keytool -list -v -keystore "$store" -storepass "$(grep -oP '^storePassword=\K.*' key.properties)" | grep -m1 'SHA256:' | awk '{print $2}' | tr -d ':' | tr 'A-F' 'a-f')
apksigner=$(ls "${ANDROID_HOME:-$HOME/Android/Sdk}"/build-tools/*/apksigner | sort -V | tail -1)
got=$("$apksigner" verify --print-certs "$apk" | grep -m1 'SHA-256 digest' | awk '{print $NF}')
[ "$want" = "$got" ] || { echo "APK is not signed with the release key ($got)" >&2; exit 1; }

out="liquid-lsd-v$ver.apk"
cp "$apk" "/tmp/$out"
if [ "$cur" != "$ver" ]; then
  git add app/build.gradle.kts
  git commit -q -m "Release $ver"
fi
git tag -a "v$ver" -m "Liquid LSD for Android $ver"
git push -q origin HEAD "v$ver"
pre=""; [[ "$ver" == 0.* ]] && pre="--prerelease"
if [ -n "$notes" ]; then
  gh release create "v$ver" "/tmp/$out" --title "Liquid LSD $ver" --notes-file "$notes" --verify-tag $pre
else
  gh release create "v$ver" "/tmp/$out" --title "Liquid LSD $ver" --generate-notes --verify-tag $pre
fi
echo "released v$ver (versionCode $code): $(gh release view "v$ver" --json url -q .url)"
