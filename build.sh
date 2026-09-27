#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🔨 Building VanillaWebMap..."

# Verify dependencies directory exists
if [ ! -d "libs" ]; then
    echo "❌ Error: libs/ directory not found. Please provide required Minecraft/Fabric dependencies in libs/."
    exit 1
fi

# Clean output directory
rm -rf bin
mkdir -p bin

SERVER_JAR=$(ls libs/server-*.jar 2>/dev/null | head -n 1 || echo "")
LOADER_JAR=$(ls libs/fabric-loader-*.jar 2>/dev/null | head -n 1 || echo "")

if [ -z "$SERVER_JAR" ] || [ -z "$LOADER_JAR" ]; then
    echo "❌ Error: Could not find server-*.jar or fabric-loader-*.jar in libs/."
    echo "💡 Run ./scripts/setup-deps.sh to download dependencies."
    exit 1
fi

echo "📦 Compiling Java sources with ${SERVER_JAR}..."
javac -cp "${SERVER_JAR}:${LOADER_JAR}:libs/fabric-modules/*:libs/mc-libs/*" \
      -d bin src/main/java/xyz/chaonius/webmap/*.java

# Copy mod metadata and assets
echo "📄 Packaging resources..."
cp -r src/main/resources/* bin/

# Extract version from fabric.mod.json
VERSION=$(grep '"version"' src/main/resources/fabric.mod.json | head -n 1 | sed -E 's/.*"version"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')
JAR_NAME="vanilla-webmap-${VERSION}.jar"

# Package into mod jar
echo "📦 Creating ${JAR_NAME}..."
rm -f vanilla-webmap-*.jar
(cd bin && jar -cf "../${JAR_NAME}" *)

echo "✅ Build successful: ${JAR_NAME} ($(du -h "${JAR_NAME}" | cut -f1))"
