#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Ensure build is fresh
./build.sh

VERSION=$(grep -o '"version": "[^"]*"' src/main/resources/fabric.mod.json | cut -d'"' -f4)
TAG="v${VERSION}"
JAR="vanilla-webmap-${VERSION}.jar"

echo "🚀 Preparing release for ${TAG} (${JAR})..."

# Determine gh command
GH_CMD="gh"
if ! command -v gh >/dev/null 2>&1; then
    if command -v nix-shell >/dev/null 2>&1; then
        GH_CMD="nix-shell -p gh --run gh"
    else
        echo "❌ Error: GitHub CLI (gh) not found."
        exit 1
    fi
fi

# Ensure tag exists locally
if ! git rev-parse "$TAG" >/dev/null 2>&1; then
    echo "🏷️ Creating tag $TAG..."
    git tag -a "$TAG" -m "VanillaWebMap $TAG Release"
fi

# Ensure tag is pushed to remote
if ! git ls-remote --tags origin | grep -q "refs/tags/$TAG"; then
    echo "📤 Pushing tag $TAG to origin..."
    git push origin "$TAG"
fi

echo "📦 Publishing release to GitHub..."
$GH_CMD release create "$TAG" "$JAR" \
    --title "VanillaWebMap $TAG - High-Speed Zero-Lag Fabric Web Map" \
    --generate-notes

echo "🎉 Successfully published GitHub Release $TAG!"
