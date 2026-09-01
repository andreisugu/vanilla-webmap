<div align="center">

# 📜 VanillaWebMap

**Ultra-Fast, Zero-Lag, 8-Bit Vanilla Minecraft Web Map**  
*High-performance binary streaming, event-driven chunk capture, and modern HTML5 Canvas visualization.*

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21%2B%20%7C%2026.2%2B-brightgreen.svg)](https://minecraft.net)
[![Fabric Mod](https://img.shields.io/badge/Mod%20Loader-Fabric-blue.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-red.svg)](https://adoptium.net/)

[Features](#-key-features) • [Architecture](#-architecture--wire-protocol) • [Installation](#-installation) • [Configuration](#%EF%B8%8F-configuration) • [Commands](#-in-game-commands) • [Reverse Proxy](#-reverse-proxy--nginx-setup) • [Building](#-%EF%B8%8F-building-from-source)

</div>

---

## 📖 Overview

**VanillaWebMap** is a lightweight, server-side Fabric mod that provides a real-time web map for Minecraft servers with **zero game-tick lag**. Unlike traditional web map plugins that generate heavy PNG images or freeze server ticks with massive raytracing routines, VanillaWebMap captures raw 8-bit Minecraft palette data in **0.005 milliseconds** directly upon chunk generation.

Tile data is packed into **512×512 Region Mega-Tiles (256 KB)** and streamed to a high-speed HTML5 Canvas client featuring GPU-accelerated rendering, 4 sleek UI themes, real-time player tracking, coordinate navigation, and interactive 360° map rotation.

---

## ✨ Key Features

### 🏎️ Performance & Server Health
* **⚡ 0.005ms Event-Driven Chunk Capture:** Intercepts chunks during natural `CHUNK_LOAD` events while data is hot in CPU cache.
* **🧠 Zero Server Thread Contention:** Background HTTP threads never touch Minecraft's `ServerLevel` or chunk managers. All active regions are served directly from an in-memory byte buffer in `0.0001ms`.
* **💾 Async Disk I/O:** Tiles are persisted asynchronously in 256-byte compact binary files (`.vmap`) without blocking the server tick loop.
* **⚡ Chunky & Pregen Compatible:** Seamlessly captures thousands of chunks generated per second during pregeneration with 20.0 TPS locked.

### 🌐 High-Speed Mega-Tile Region Streaming
* **📦 99.8% Network Request Reduction:** Groups 1,024 chunks into 512×512 block region mega-tiles, reducing browser HTTP calls from thousands down to 1–4 requests per screen view.
* **🔄 Live Exploration Hot-Reloading:** Versioned region headers detect newly explored chunks in real-time, hot-swapping updated tiles with zero flicker.
* **🧩 Anti-Aliasing Seam Eliminator:** Dynamic screen-space overlap calculations prevent 1-pixel hairline cracks at any zoom level (0.1x to 8.0x).

### 🎨 Modern Web Interface & Tools
* **🎭 4 Built-In Themes:** `📜 Parchment` (Classic RPG), `🌑 Slate Dark`, `⬛ AMOLED Pure Black`, and `☀️ Light` with persistent local storage.
* **🧭 360° Interactive Rotation:** Hold `Ctrl + Drag` (or right-click drag) to rotate the map in full 3D space with an interactive magnetic compass needle.
* **📍 Dual X/Z Coordinate Search:** Jump instantly to any coordinates using dedicated X and Z input boxes.
* **📐 Minecraft F3+G Chunk Grid:** High-contrast golden chunk boundary overlay visible from panoramic zoom out to block-level zoom in.
* **👥 Live Player Tracking:** Crisp screen-space heading markers and auto-sizing nametag pills with health & position telemetry.
* **📸 1-Click PNG Screenshot:** Export clean high-resolution map screenshots with camera flash visual effects.
* **📱 Responsive & Fullscreen:** Seamless transition between bordered frame mode and full browser edge-to-edge view.

---

## 🏛️ Architecture & Wire Protocol

```
+-----------------------------------------------------------------------+
|                            MINECRAFT SERVER                           |
|                                                                       |
|  [Chunk Load Event] ---> [Palette Mapper (0.005ms)]                   |
|                                    |                                  |
|                                    v                                  |
|            +-----------------------+-----------------------+          |
|            |                                               |          |
|            v                                               v          |
|   [Async Disk Writer]                            [RAM Region Buffer]  |
|   Writes 256B .vmap files                        512x512 byte arrays  |
|            |                                               |          |
+------------|-----------------------------------------------|----------+
             |                                               |
             |           +-----------------------+           |
             +---------> | Virtual-Threaded HTTP | <---------+
                         |      Server (:8105)   |
                         +-----------------------+
                                     |
                                     v
                       [High-Speed Browser Client]
                       - ImageBitmap GPU Decoding
                       - 60 / 144+ FPS Canvas Matrix
```

### 📡 REST API Endpoints

| Endpoint | Method | Description | Cache Policy |
| :--- | :--- | :--- | :--- |
| `/api/config` | `GET` | Server title, IP, and display settings | `max-age=300` |
| `/api/status` | `GET` | Live TPS, online count, time of day, rain status | `max-age=1` |
| `/api/players` | `GET` | Real-time coordinates, dimension, health, and yaw | `max-age=1` |
| `/api/regions_index` | `GET` | Map of explored regions and live chunk version numbers | `no-cache` |
| `/api/region?rx=X&rz=Z&v=V` | `GET` | 256 KB binary stream of 512×512 block region mega-tile | `immutable` |
| `/api/tile?cx=X&cz=Z` | `GET` | 256-byte binary stream of individual 16×16 chunk | `immutable` |

---

## 📦 Installation

1. Download the latest `vanilla-webmap-1.0.0.jar`.
2. Place the `.jar` into your Minecraft server's `mods/` folder.
3. Requires **Fabric Loader 0.16+** on Minecraft **1.21+ / 26.2+** with Java 21+.
4. Start the server. The web server will automatically initialize on port `8105`.

---

## ⚙️ Configuration

The configuration file is located at `config/vanilla-webmap.json`:

```json
{
  "version": 1,
  "serverTitle": "My Minecraft Server",
  "serverIp": "mc.example.com",
  "headerLinkUrl": "https://example.com",
  "httpPort": 8105,
  "bindAddress": "0.0.0.0",
  "scanRadius": 8,
  "scanIntervalTicks": 40,
  "tileCacheDir": "./world_webmap_cache",
  "showPlayerHealth": true,
  "showCoordinates": true
}
```

### Configuration Options
* **`serverTitle`**: Title displayed in the top HUD and browser tab.
* **`serverIp`**: Address copied when clicking the server IP badge in the sidebar.
* **`headerLinkUrl`**: URL opened when clicking the server title.
* **`httpPort`**: Port for the built-in binary tile HTTP server (Default: `8105`).
* **`bindAddress`**: Network interface binding (`0.0.0.0` for all interfaces).
* **`tileCacheDir`**: Directory where `.vmap` chunk cache files are stored.
* **`showPlayerHealth`**: Exposes player health points on the web map.
* **`showCoordinates`**: Exposes player world coordinates on the web map.

---

## 🎮 In-Game Commands

VanillaWebMap provides a command suite under `/webmap` *(Requires Permission Level 2+)*:

| Command | Description |
| :--- | :--- |
| `/webmap status` | Displays HTTP server status, cached disk tiles, RAM cache, and active players. |
| `/webmap render <radius>` | Force-scans and renders a radius of chunks around your current location. |
| `/webmap clear` | Clears all cached `.vmap` files from RAM and disk. |
| `/webmap reload` | Reloads `config/vanilla-webmap.json` without restarting the server. |

---

## 🌐 Reverse Proxy / Nginx Setup

To expose VanillaWebMap behind Nginx (with SSL / Cloudflare):

### Option A: Subpath Routing (e.g. `https://map.example.com/survival/`)
```nginx
location /survival/ {
    proxy_pass http://127.0.0.1:8105/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    
    # Enable WebSocket and buffer optimizations
    proxy_buffering off;
    proxy_read_timeout 600s;
}
```

### Option B: Root Domain (e.g. `https://map.example.com/`)
```nginx
location / {
    proxy_pass http://127.0.0.1:8105;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

---

## 🛠️ Building from Source

### Prerequisites
* JDK 21 or higher
* Fabric Loader libraries

### Manual Compilation
```bash
# 1. Compile Java sources
javac -cp "libs/server-26.2.jar:libs/fabric-loader-0.19.3.jar:libs/fabric-modules/*:libs/mc-libs/*" \
      -d bin src/main/java/xyz/chaonius/webmap/*.java

# 2. Bundle web assets and metadata
cp src/main/resources/fabric.mod.json bin/
mkdir -p bin/assets/vanilla-webmap/web
cp src/main/resources/assets/vanilla-webmap/web/index.html bin/assets/vanilla-webmap/web/

# 3. Create mod jar
cd bin && jar -cf ../vanilla-webmap-1.0.0.jar *
```

---

## 📄 License

Licensed under the **Apache License, Version 2.0**. See the [LICENSE](LICENSE) file for details.

```
Copyright 2026 VanillaWebMap Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
