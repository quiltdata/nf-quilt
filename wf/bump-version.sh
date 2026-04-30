#!/usr/bin/env bash
set -euo pipefail

LEVEL="${1:-patch}"
case "$LEVEL" in
    patch|minor|major) ;;
    *) echo "Usage: $0 [patch|minor|major]" >&2; exit 1 ;;
esac

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="$ROOT/build.gradle"

OLD="$(grep "^version" "$FILE" | head -1 | awk -F"'" '{ print $2 }')"
if [[ ! "$OLD" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    echo "Cannot parse semver from '$OLD' in $FILE" >&2
    exit 1
fi
MAJOR="${BASH_REMATCH[1]}"
MINOR="${BASH_REMATCH[2]}"
PATCH="${BASH_REMATCH[3]}"

case "$LEVEL" in
    patch) PATCH=$((PATCH + 1)) ;;
    minor) MINOR=$((MINOR + 1)); PATCH=0 ;;
    major) MAJOR=$((MAJOR + 1)); MINOR=0; PATCH=0 ;;
esac
NEW="${MAJOR}.${MINOR}.${PATCH}"

sed -i.bak "s/^version = '${OLD//./\\.}'\$/version = '${NEW}'/" "$FILE"
rm "$FILE.bak"

cd "$ROOT"
git add build.gradle
git commit -m "Bump version: ${OLD} -> ${NEW}"

echo "Bumped $OLD -> $NEW and committed."
