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
cp src/main/resources/fabric.mod.json bin/
mkdir -p bin/assets/vanilla-webmap/web
cp src/main/resources/assets/vanilla-webmap/web/index.html bin/assets/vanilla-webmap/web/

# Package into mod jar
echo "📦 Creating vanilla-webmap-1.0.0.jar..."
(cd bin && jar -cf ../vanilla-webmap-1.0.0.jar *)

echo "✅ Build successful: vanilla-webmap-1.0.0.jar ($(du -h vanilla-webmap-1.0.0.jar | cut -f1))"
