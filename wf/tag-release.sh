#!/usr/bin/env bash
set -euo pipefail

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then
    DRY_RUN=1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="$(grep "^version" build.gradle | head -1 | awk -F"'" '{ print $2 }')"
if [[ -z "$VERSION" ]]; then
    echo "Could not parse version from build.gradle" >&2
    exit 1
fi

# Refuse to tag from anywhere but main at origin/main's HEAD.
git fetch origin main --quiet
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
HEAD_SHA="$(git rev-parse HEAD)"
MAIN_SHA="$(git rev-parse origin/main)"
if [[ "$BRANCH" != "main" || "$HEAD_SHA" != "$MAIN_SHA" ]]; then
    echo "Refusing to tag: must be on main at origin/main (currently $BRANCH @ ${HEAD_SHA:0:7}, origin/main @ ${MAIN_SHA:0:7})" >&2
    [[ $DRY_RUN -eq 1 ]] || exit 1
    echo "(dry-run: continuing anyway)" >&2
fi

if git rev-parse "refs/tags/$VERSION" >/dev/null 2>&1; then
    echo "Tag $VERSION already exists" >&2
    [[ $DRY_RUN -eq 1 ]] || exit 1
fi

# Escape dots so awk treats the version literally (1.0.1 must not match 1X0Y1).
ESCAPED_VERSION="${VERSION//./\\.}"
NOTES="$(awk -v v="$ESCAPED_VERSION" '
    $0 ~ "^## \\[" v "\\]" { found=1; next }
    found && /^## \[/ { exit }
    found { print }
' CHANGELOG.md)"

if [[ -z "$(printf %s "$NOTES" | tr -d '[:space:]')" ]]; then
    echo "No CHANGELOG section found for [$VERSION]" >&2
    exit 1
fi

if [[ $DRY_RUN -eq 1 ]]; then
    echo "=== DRY RUN ==="
    echo "Version: $VERSION"
    echo "HEAD:    ${HEAD_SHA:0:7} ($BRANCH)"
    echo "--- Release notes ---"
    echo "$NOTES"
    echo "--- Would run ---"
    echo "git tag -a $VERSION -m 'Release $VERSION'"
    echo "git push origin $VERSION"
    echo "gh release create $VERSION --title 'Version $VERSION' --notes <notes above>"
    exit 0
fi

git tag -a "$VERSION" -m "Release $VERSION"
git push origin "$VERSION"

# Roll back the tag if release creation fails so we don't leave a half-released state.
if ! gh release create "$VERSION" --title "Version $VERSION" --notes "$NOTES"; then
    echo "gh release create failed; rolling back tag $VERSION" >&2
    git push origin --delete "$VERSION" || true
    git tag -d "$VERSION" || true
    exit 1
fi
echo "Tagged and released $VERSION"
