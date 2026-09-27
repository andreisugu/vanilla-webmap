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

# Compile Java sources
echo "📦 Compiling Java sources..."
javac -cp "libs/server-26.2.jar:libs/fabric-loader-0.19.3.jar:libs/fabric-modules/*:libs/mc-libs/*" \
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
