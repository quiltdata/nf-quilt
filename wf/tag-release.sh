#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(grep "^version" build.gradle | head -1 | awk -F"'" '{ print $2 }')"
if [[ -z "$VERSION" ]]; then
    echo "Could not parse version from build.gradle" >&2
    exit 1
fi

if git rev-parse "$VERSION" >/dev/null 2>&1; then
    echo "Tag $VERSION already exists" >&2
    exit 1
fi

# Extract the CHANGELOG section for this version (between its header and the next ## header).
NOTES="$(awk -v v="$VERSION" '
    $0 ~ "^## \\[" v "\\]" { found=1; next }
    found && /^## \[/ { exit }
    found { print }
' CHANGELOG.md)"

if [[ -z "${NOTES// }" ]]; then
    echo "No CHANGELOG section found for [$VERSION]" >&2
    exit 1
fi

git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"

gh release create "$VERSION" --title "Version $VERSION" --notes "$NOTES"
echo "Tagged and released $VERSION"
