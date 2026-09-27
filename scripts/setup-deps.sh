#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SCRIPT_DIR"

echo "📥 Setting up VanillaWebMap dependencies..."
mkdir -p libs

# 1. Download official Mojang server jar (26.2)
if [ ! -f "libs/minecraft-server.jar" ]; then
    echo "⬇️ Downloading Mojang server jar..."
    curl -sSL "https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar" -o libs/minecraft-server.jar
fi

# Extract version jar and libraries from Mojang bundler if not already present
if [ ! -f "libs/server-26.2.jar" ] || [ ! -d "libs/mc-libs" ]; then
    echo "📦 Extracting Mojang server libraries..."
    (cd libs && jar -xf minecraft-server.jar META-INF/versions/26.2/server-26.2.jar META-INF/libraries)
    mv libs/META-INF/versions/26.2/server-26.2.jar libs/ 2>/dev/null || true
    mkdir -p libs/mc-libs
    find libs/META-INF/libraries -name "*.jar" -exec mv {} libs/mc-libs/ \;
    rm -rf libs/META-INF
fi

# 2. Download Fabric Loader
if [ ! -f "libs/fabric-loader-0.19.3.jar" ]; then
    echo "⬇️ Downloading Fabric Loader 0.19.3..."
    curl -sSL "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar" -o libs/fabric-loader-0.19.3.jar
fi

# 3. Download Fabric API
if [ ! -f "libs/fabric-api-0.158.0+26.2.jar" ] || [ ! -d "libs/fabric-modules" ]; then
    echo "⬇️ Downloading Fabric API 0.158.0+26.2..."
    curl -sSL "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/0.158.0+26.2/fabric-api-0.158.0+26.2.jar" -o libs/fabric-api-0.158.0+26.2.jar
    mkdir -p libs/fabric-modules
    (cd libs && jar -xf fabric-api-0.158.0+26.2.jar META-INF/jars)
    mv libs/META-INF/jars/*.jar libs/fabric-modules/ 2>/dev/null || true
    rm -rf libs/META-INF
fi

echo "✅ Dependencies setup complete in libs/."
