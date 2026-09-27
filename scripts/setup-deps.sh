#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$SCRIPT_DIR"

MC_VERSION="${1:-26.3}"

echo "📥 Setting up VanillaWebMap dependencies for Minecraft ${MC_VERSION}..."
mkdir -p libs

if [ "$MC_VERSION" = "26.3" ]; then
    SERVER_URL="https://piston-data.mojang.com/v1/objects/33680f5f2ac32864d6d7cf5e56a705fdb3e05f4c/server.jar"
    LOADER_VER="0.19.5"
    FAPI_VER="0.161.0+26.3"
elif [ "$MC_VERSION" = "26.2" ]; then
    SERVER_URL="https://piston-data.mojang.com/v1/objects/823e2250d24b3ddac457a60c92a6a941943fcd6a/server.jar"
    LOADER_VER="0.19.3"
    FAPI_VER="0.158.0+26.2"
else
    echo "❌ Unsupported version preset: $MC_VERSION (Supported: 26.2, 26.3)"
    exit 1
fi

# 1. Download official Mojang server jar
if [ ! -f "libs/server-${MC_VERSION}.jar" ] || [ ! -d "libs/mc-libs" ]; then
    echo "⬇️ Downloading Mojang server jar for ${MC_VERSION}..."
    curl -sSL "$SERVER_URL" -o libs/minecraft-server.jar
    echo "📦 Extracting Mojang server libraries..."
    (cd libs && jar -xf minecraft-server.jar "META-INF/versions/${MC_VERSION}/server-${MC_VERSION}.jar" META-INF/libraries)
    mv "libs/META-INF/versions/${MC_VERSION}/server-${MC_VERSION}.jar" libs/ 2>/dev/null || true
    mkdir -p libs/mc-libs
    find libs/META-INF/libraries -name "*.jar" -exec mv {} libs/mc-libs/ \;
    rm -rf libs/META-INF
fi

# 2. Download Fabric Loader
if ! ls libs/fabric-loader-*.jar >/dev/null 2>&1; then
    echo "⬇️ Downloading Fabric Loader ${LOADER_VER}..."
    curl -sSL "https://maven.fabricmc.net/net/fabricmc/fabric-loader/${LOADER_VER}/fabric-loader-${LOADER_VER}.jar" -o "libs/fabric-loader-${LOADER_VER}.jar"
fi

# 3. Download Fabric API
if ! ls libs/fabric-api-*.jar >/dev/null 2>&1 || [ ! -d "libs/fabric-modules" ]; then
    echo "⬇️ Downloading Fabric API ${FAPI_VER}..."
    curl -sSL "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FAPI_VER}/fabric-api-${FAPI_VER}.jar" -o "libs/fabric-api-${FAPI_VER}.jar"
    mkdir -p libs/fabric-modules
    (cd libs && jar -xf "fabric-api-${FAPI_VER}.jar" META-INF/jars)
    mv libs/META-INF/jars/*.jar libs/fabric-modules/ 2>/dev/null || true
    rm -rf libs/META-INF
fi

echo "✅ Dependencies setup complete in libs/ for Minecraft ${MC_VERSION}."
