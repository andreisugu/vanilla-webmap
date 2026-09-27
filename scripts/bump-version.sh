#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

MOD_JSON="src/main/resources/fabric.mod.json"
README="README.md"

if [ ! -f "$MOD_JSON" ]; then
    echo "❌ Error: $MOD_JSON not found!"
    exit 1
fi

CURRENT_VERSION=$(grep '"version"' "$MOD_JSON" | head -n 1 | sed -E 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')

if [ -z "$CURRENT_VERSION" ]; then
    echo "❌ Error: Could not determine current version from $MOD_JSON"
    exit 1
fi

ACTION="${1:-patch}"

IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION"
MAJOR="${MAJOR:-0}"
MINOR="${MINOR:-0}"
PATCH="${PATCH:-0}"

case "$ACTION" in
    patch)
        NEW_PATCH=$((PATCH + 1))
        NEW_VERSION="${MAJOR}.${MINOR}.${NEW_PATCH}"
        ;;
    minor)
        NEW_MINOR=$((MINOR + 1))
        NEW_VERSION="${MAJOR}.${NEW_MINOR}.0"
        ;;
    major)
        NEW_MAJOR=$((MAJOR + 1))
        NEW_VERSION="${NEW_MAJOR}.0.0"
        ;;
    *)
        # Directly specified version
        NEW_VERSION="$ACTION"
        ;;
esac

echo "🔄 Bumping version: ${CURRENT_VERSION} -> ${NEW_VERSION}"

# Update fabric.mod.json
sed -i -E "s/\"version\"[[:space:]]*:[[:space:]]*\"[^\"]+\"/\"version\": \"${NEW_VERSION}\"/" "$MOD_JSON"

# Update README.md jar reference
if [ -f "$README" ]; then
    sed -i -E "s/vanilla-webmap-[0-9]+\.[0-9]+\.[0-9]+\.jar/vanilla-webmap-${NEW_VERSION}.jar/g" "$README"
fi

# Rebuild mod jar
./build.sh

# Copy to Desktop if Desktop exists
DESKTOP_DIR="/home/restless/Desktop"
if [ -d "$DESKTOP_DIR" ]; then
    cp "vanilla-webmap-${NEW_VERSION}.jar" "$DESKTOP_DIR/"
    echo "📋 Copied vanilla-webmap-${NEW_VERSION}.jar to Desktop."
fi

echo ""
echo "✅ Successfully updated to v${NEW_VERSION}!"
echo "💡 To commit and publish release:"
echo "   git commit -am \"Release: v${NEW_VERSION}\""
echo "   git tag \"v${NEW_VERSION}\""
echo "   git push origin main --tags"
